#!/usr/bin/env python3
"""
setup_module.py — Clone module `:firebase-events` từ GitHub repo public + copy
2 module (firebase-events, firebase-events-lint) vào project Android target +
wire Gradle (settings.gradle + app/build.gradle).

Usage:
    python setup_module.py \\
        --target /path/to/android/project \\
        [--repo-url https://github.com/wakiratohf/FirebaseEventFramework.git] \\
        [--branch main] \\
        [--dry-run] [--yes] [--force-reclone]

Workflow (mỗi phase có thể skip riêng):
    1. CLONE   — git clone (shallow) repo về `<target>/.claude-cache/firebase-events-upstream/`
                 hoặc git fetch + reset nếu cache đã có
    2. COPY    — copy `firebase-events/` + `firebase-events-lint/` từ cache sang target
                 (preserve VERSION file, skip .git/build/.gradle)
    3. WIRE    — patch `settings.gradle(.kts)` với `include(":firebase-events")` +
                 `include(":firebase-events-lint")`
    4. APPWIRE — patch `app/build.gradle(.kts)` với `implementation(project(...))` +
                 `lintChecks(project(...))`

Idempotent: chạy 2 lần liên tiếp → 0 thay đổi.

Exit codes:
    0 — Success (gồm 'không thay đổi gì')
    1 — User abort, dry-run only, or one phase failed but others OK
    2 — Fatal (git not installed, target not Android project, repo unreachable)
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import List, Optional, Tuple

DEFAULT_REPO_URL = "https://github.com/wakiratohf/FirebaseEventFramework.git"
DEFAULT_BRANCH = "main"
CACHE_SUBDIR = ".claude-cache/firebase-events-upstream"

# Default modules to import — mọi project nên có cả 3:
#   - firebase-events:        SDK lõi (bắt buộc). minSdk 21, JDK 17, ZERO project(":...") deps.
#                             Bump VERSION khi đổi public API. Copy với firebase-events-lint.
#   - firebase-events-lint:   Lint rule enforce `buttonName` convention compile-time
#                             (`ClickBtnEvUnderscore`, `BtnPrefix`, `NotCamelCase`, `Empty`).
#                             Pure-JVM, ship qua lintPublish/lintChecks.
#   - app-events:             App-level wrapper cho lifecycle + intent + ads bridge:
#                             • TimeOpenAppTracker (time_open_app_ev)
#                             • AppExitTracker (app_exit, debounced 1s)
#                             • OpenAppFromIntent (open_app_from_ev)
#                             • AdsEventTracker (ad events, SDK-agnostic)
#                             • RateDialogEventTracker (show_rate_dialog_ev)
#                             High-level entry: `AppEventsInstaller.install(application)`.
#
# Optional opt-in qua --modules:
#   - ads:                    AdMob bridge — module DUY NHẤT biết Google Mobile Ads SDK.
#                             Cung cấp:
#                             • AdsManager.initialize(context, isTestMode)
#                             • Compose `BannerAd` widget
#                             • AdMob callbacks → bridge vào AdsEventTracker (:app-events)
#                             Chỉ thêm khi project dùng AdMob (`api` exposing AdRequest/MobileAds).
#                             Depend trên CẢ :firebase-events + :app-events (strict direction).
#
# Dependency direction strict:
#   :app-events       → :firebase-events
#   :ads              → :app-events + :firebase-events
#   :app              → all three + lintChecks(:firebase-events-lint)
#   Never invert these arrows. `:firebase-events` có ZERO project deps.
MODULES_TO_IMPORT = ["firebase-events", "firebase-events-lint", "app-events"]

# Modules that should be wired with `lintChecks(project(...))` thay vì `implementation(...)`.
# Suffix `-lint` là convention rõ ràng.
LINT_MODULE_SUFFIX = "-lint"

# Files/dirs to skip when copying from cache → target
COPY_SKIP_DIRS = {".git", "build", ".gradle", ".idea", "out"}
COPY_SKIP_PATTERNS = (".iml", ".DS_Store")


# ============================================================================
# Argument parsing
# ============================================================================

def parse_args(argv: List[str]) -> argparse.Namespace:
    ap = argparse.ArgumentParser(
        description="Clone firebase-events module + wire into Android project",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--target", default=".",
                    help="Path to Android project root (default: cwd)")
    ap.add_argument("--repo-url", default=DEFAULT_REPO_URL,
                    help=f"Git URL of module repo (default: {DEFAULT_REPO_URL})")
    ap.add_argument("--branch", default=DEFAULT_BRANCH,
                    help=f"Branch / tag / commit (default: {DEFAULT_BRANCH})")
    ap.add_argument("--cache-dir", default=None,
                    help="Override cache location (default: <target>/" + CACHE_SUBDIR + ")")
    ap.add_argument("--modules", nargs="+", default=MODULES_TO_IMPORT,
                    help="Module subdirs to copy")
    ap.add_argument("--dry-run", action="store_true",
                    help="Print what would happen, don't write")
    ap.add_argument("--yes", "-y", action="store_true",
                    help="Skip confirmation prompts")
    ap.add_argument("--force-reclone", action="store_true",
                    help="Wipe cache and re-clone from scratch")
    ap.add_argument("--skip-clone", action="store_true",
                    help="Skip clone phase (use existing cache)")
    ap.add_argument("--skip-copy", action="store_true",
                    help="Skip copy phase (module already at target)")
    ap.add_argument("--skip-wire", action="store_true",
                    help="Skip Gradle wiring phase")
    ap.add_argument("--skip-align", action="store_true",
                    help="Skip Gradle alignment phase (don't scan compat)")
    ap.add_argument("--skip-reconcile", action="store_true",
                    help="Skip catalog reconcile phase (don't patch module files)")
    ap.add_argument("--apply-catalog", action="store_true",
                    help="When aligning, auto-add missing libs.versions.toml entries (placeholders)")
    return ap.parse_args(argv)


# ============================================================================
# Main orchestration
# ============================================================================

def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])

    target = Path(args.target).resolve()
    if not target.is_dir():
        print(f"❌ Target dir không tồn tại: {target}", file=sys.stderr)
        return 2

    # Validate target = Android project
    if not _is_android_project(target):
        print(f"⚠ Target không giống Android project: {target}", file=sys.stderr)
        if not args.yes:
            ans = input("  Không thấy settings.gradle.kts/settings.gradle hoặc app/. Vẫn tiếp tục? [y/N] ").strip().lower()
            if ans not in ("y", "yes"):
                return 1

    # Validate git installed (needed unless ALL phases skipped)
    if not args.skip_clone:
        if shutil.which("git") is None:
            print("❌ `git` không có trong PATH. Cài git rồi chạy lại.", file=sys.stderr)
            return 2

    cache_dir = Path(args.cache_dir).resolve() if args.cache_dir \
        else (target / CACHE_SUBDIR).resolve()

    print(f"═══ setup_module ═══")
    print(f"  Target:    {target}")
    print(f"  Repo URL:  {args.repo_url}")
    print(f"  Branch:    {args.branch}")
    print(f"  Cache:     {cache_dir}")
    print(f"  Modules:   {', '.join(args.modules)}")
    if args.dry_run:
        print(f"  Mode:      DRY-RUN (no writes)")
    print()

    summary = {"clone": "skipped", "copy": "skipped", "wire": "skipped",
               "appwire": "skipped", "reconcile": "skipped", "align": "skipped"}

    # ---- Phase 1: Clone ----
    if not args.skip_clone:
        rc = _phase_clone(args, cache_dir)
        if rc != 0:
            print(f"\n❌ Clone phase failed (exit {rc}).", file=sys.stderr)
            return rc
        summary["clone"] = "done"

    # ---- Phase 2: Copy modules to target ----
    if not args.skip_copy:
        # Warn (not fail) if some modules aren't in the cloned repo — phase_copy
        # will graceful-skip them. This lets forks with subset of modules work.
        missing = [m for m in args.modules if not (cache_dir / m).is_dir()]
        if missing:
            print(f"⚠ Modules not in clone (will skip): {', '.join(missing)}")
            print(f"  Repo: {args.repo_url} branch {args.branch}")
            available = [d.name for d in cache_dir.iterdir() if d.is_dir() and not d.name.startswith(".")]
            print(f"  Available in cache: {', '.join(sorted(available))}")
            print()

        rc = _phase_copy(args, cache_dir, target)
        if rc != 0:
            print(f"\n❌ Copy phase failed.", file=sys.stderr)
            return rc
        summary["copy"] = "done"

    # ---- Phase 3 & 4: Wire Gradle ----
    if not args.skip_wire:
        rc_wire = _phase_wire_settings(args, target)
        if rc_wire == 0:
            summary["wire"] = "done"
        else:
            summary["wire"] = "failed"

        rc_appwire = _phase_wire_app(args, target)
        if rc_appwire == 0:
            summary["appwire"] = "done"
        else:
            summary["appwire"] = "failed"

    # ---- Phase 5.5: Reconcile catalog (patch module files để match target keys) ----
    if not args.skip_reconcile:
        rc_reconcile = _phase_reconcile(args, cache_dir, target)
        if rc_reconcile == 0:
            summary["reconcile"] = "done"
        elif rc_reconcile == 1:
            summary["reconcile"] = "warn"  # missing libs in target
        else:
            summary["reconcile"] = "failed"

    # ---- Phase 5: Align Gradle (scan compat) ----
    if not args.skip_align:
        rc_align = _phase_align(args, target)
        if rc_align == 0:
            summary["align"] = "done"
        elif rc_align == 1:
            summary["align"] = "warn"  # findings present but report written
        else:
            summary["align"] = "failed"

    # ---- Summary ----
    print()
    print("═══ Summary ═══")
    for k, v in summary.items():
        icon = {"done": "✓", "skipped": "·", "failed": "✗", "warn": "⚠"}.get(v, "?")
        print(f"  {icon} {k}: {v}")

    if args.dry_run:
        print("\n(dry-run — no files written)")
        return 1

    if any(v == "failed" for v in summary.values()):
        return 1

    print("\n✅ Module đã được import. Bước kế tiếp:")
    print("   1. Mở Android Studio → File → Sync Project with Gradle Files")
    print("   2. Build verify: ./gradlew :app:assembleDebug")
    print("   3. Tiếp tục: /analytics-scaffold (nếu chưa có spec) hoặc /analytics-setup")
    return 0


def _is_android_project(target: Path) -> bool:
    return (
        (target / "settings.gradle.kts").is_file()
        or (target / "settings.gradle").is_file()
        or (target / "app").is_dir()
    )


# ============================================================================
# Phase 1: Clone
# ============================================================================

def _phase_clone(args, cache_dir: Path) -> int:
    print("─── Phase 1: Clone repo ───")

    if args.force_reclone and cache_dir.exists():
        print(f"  Removing existing cache: {cache_dir}")
        if not args.dry_run:
            shutil.rmtree(cache_dir)

    cache_dir.parent.mkdir(parents=True, exist_ok=True)

    if (cache_dir / ".git").is_dir():
        # Update existing cache
        print(f"  Cache exists — fetching latest from {args.branch}")
        if args.dry_run:
            print(f"  (dry-run: would run `git fetch` + `git reset --hard origin/{args.branch}`)")
            return 0
        try:
            subprocess.run(
                ["git", "fetch", "--depth", "1", "origin", args.branch],
                cwd=cache_dir, check=True, capture_output=True, text=True,
            )
            subprocess.run(
                ["git", "reset", "--hard", f"origin/{args.branch}"],
                cwd=cache_dir, check=True, capture_output=True, text=True,
            )
            head = subprocess.run(
                ["git", "rev-parse", "--short", "HEAD"],
                cwd=cache_dir, check=True, capture_output=True, text=True,
            ).stdout.strip()
            print(f"  ✓ Updated cache to {head}")
            return 0
        except subprocess.CalledProcessError as e:
            print(f"  ❌ git fetch/reset failed:\n{e.stderr}", file=sys.stderr)
            print(f"  Hint: --force-reclone để wipe + clone lại", file=sys.stderr)
            return 2

    # Fresh clone
    print(f"  Cloning {args.repo_url} (branch {args.branch}, depth 1)")
    if args.dry_run:
        print(f"  (dry-run: would run `git clone --depth 1 --branch {args.branch} {args.repo_url} {cache_dir}`)")
        return 0
    try:
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", args.branch,
             args.repo_url, str(cache_dir)],
            check=True, capture_output=True, text=True,
        )
        head = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=cache_dir, check=True, capture_output=True, text=True,
        ).stdout.strip()
        print(f"  ✓ Cloned at {head}")
        return 0
    except subprocess.CalledProcessError as e:
        print(f"  ❌ git clone failed:\n{e.stderr}", file=sys.stderr)
        print(f"  Common causes:", file=sys.stderr)
        print(f"    - URL không tồn tại hoặc repo private (cần SSH key)", file=sys.stderr)
        print(f"    - Branch `{args.branch}` không có trong repo", file=sys.stderr)
        print(f"    - Network không kết nối được github.com", file=sys.stderr)
        return 2


# ============================================================================
# Phase 2: Copy modules
# ============================================================================

def _phase_copy(args, cache_dir: Path, target: Path) -> int:
    """Copy modules từ cache sang target.

    LUÔN refresh — không skip dù VERSION match. Lý do: code có thể đổi (bug fix,
    new method) ngay cả khi VERSION chưa bump. User chạy /analytics-import-module
    để đảm bảo có code mới nhất.

    Module thiếu trong cache (vd repo fork chỉ có 2/3 module) → warn + skip,
    không fail toàn pipeline.
    """
    print("\n─── Phase 2: Copy modules vào target ───")
    copied = 0
    skipped = 0
    for module in args.modules:
        src = cache_dir / module
        dst = target / module
        if not src.is_dir():
            print(f"  ⚠ Module `{module}` không có trong repo đã clone (skip)",
                  file=sys.stderr)
            skipped += 1
            continue

        # Show version info for logging (no longer used for skip decision)
        src_ver = (src / "VERSION").read_text(encoding="utf-8").strip() if (src / "VERSION").is_file() else None
        dst_ver = (dst / "VERSION").read_text(encoding="utf-8").strip() if (dst / "VERSION").is_file() else None

        if dst.exists():
            if src_ver and dst_ver and src_ver == dst_ver:
                print(f"  ⟳ {module}/  (refresh — same version {src_ver})")
            elif src_ver and dst_ver:
                print(f"  ~ {module}/  ({dst_ver} → {src_ver})")
            else:
                print(f"  ~ {module}/  (refresh)")
        else:
            print(f"  + {module}/  (new" + (f", version {src_ver}" if src_ver else "") + ")")

        if args.dry_run:
            continue

        # Force re-copy: wipe target dir if exists. User confirm only first time
        # in interactive mode — subsequent modules in same run reuse the answer.
        if dst.exists():
            if not args.yes and not getattr(args, "_copy_confirmed", False):
                ans = input(f"    Overwrite existing modules in target? "
                            f"All changes to module files will be lost. [y/N] ").strip().lower()
                if ans not in ("y", "yes"):
                    print(f"    Skipped {module} (user declined)")
                    skipped += 1
                    continue
                # Remember answer for rest of this run
                args._copy_confirmed = True
            shutil.rmtree(dst)

        _copytree_filtered(src, dst)
        copied += 1

    if copied == 0 and skipped > 0:
        print(f"  ⚠ No modules copied ({skipped} skipped)", file=sys.stderr)
        return 1
    return 0


def _copytree_filtered(src: Path, dst: Path) -> None:
    """Copy tree but skip COPY_SKIP_DIRS and COPY_SKIP_PATTERNS."""
    def ignore_fn(directory, contents):
        ignored = set()
        for c in contents:
            if c in COPY_SKIP_DIRS:
                ignored.add(c)
            elif any(c.endswith(p) for p in COPY_SKIP_PATTERNS):
                ignored.add(c)
        return ignored
    shutil.copytree(src, dst, ignore=ignore_fn)


# ============================================================================
# Phase 3: Wire settings.gradle(.kts)
# ============================================================================

def _phase_wire_settings(args, target: Path) -> int:
    print("\n─── Phase 3: Wire settings.gradle ───")
    settings_kts = target / "settings.gradle.kts"
    settings_groovy = target / "settings.gradle"

    if settings_kts.is_file():
        return _wire_settings_kts(args, settings_kts)
    if settings_groovy.is_file():
        return _wire_settings_groovy(args, settings_groovy)
    print(f"  ⚠ Không tìm thấy settings.gradle(.kts) trong {target}", file=sys.stderr)
    return 1


def _wire_settings_kts(args, path: Path) -> int:
    """Kotlin DSL: include(":firebase-events")"""
    return _wire_settings_generic(args, path, kts=True)


def _wire_settings_groovy(args, path: Path) -> int:
    """Groovy DSL: include ':firebase-events'"""
    return _wire_settings_generic(args, path, kts=False)


def _wire_settings_generic(args, path: Path, kts: bool) -> int:
    text = path.read_text(encoding="utf-8")
    original = text
    target = path.parent  # settings.gradle(.kts) ở target root
    changes: List[str] = []

    for module in args.modules:
        # Skip module không có dir trong target (vd fork không có app-events)
        # tránh inject include(":xxx") trỏ tới module không tồn tại → Gradle sync fail
        if not (target / module).is_dir():
            continue

        if _settings_already_has_module(text, module):
            print(f"  · {path.name}: already has include(\":{module}\")")
            continue

        if kts:
            new_line = f'include(":{module}")'
        else:
            new_line = f"include ':{module}'"

        text = _append_include(text, new_line, kts=kts)
        changes.append(module)
        print(f"  + {path.name}: include(\":{module}\")")

    if not changes:
        return 0

    if args.dry_run:
        return 0

    # Backup
    _backup(path)
    path.write_text(text, encoding="utf-8")
    return 0


def _settings_already_has_module(text: str, module: str) -> bool:
    """Match include(":mod") or include ':mod' (Kotlin or Groovy)."""
    patterns = [
        rf'include\s*\(\s*"{re.escape(":" + module)}"\s*\)',
        rf"include\s*\(\s*'{re.escape(':' + module)}'\s*\)",
        rf"include\s+'{re.escape(':' + module)}'(?:\s|$)",
        rf'include\s+"{re.escape(":" + module)}"(?:\s|$)',
    ]
    for p in patterns:
        if re.search(p, text, re.MULTILINE):
            return True
    return False


def _append_include(text: str, new_line: str, kts: bool) -> str:
    """Insert `new_line` right after the last existing include(...) line.
    If none exists, append at EOF."""
    # Find last include(...) line span
    last_match = None
    for m in re.finditer(r'^[ \t]*include\s*[(\s].*$', text, re.MULTILINE):
        last_match = m
    if last_match:
        end = last_match.end()
        return text[:end] + "\n" + new_line + text[end:]
    # No include yet → append to end with leading blank line
    sep = "\n" if text.endswith("\n") else "\n\n"
    return text + sep + new_line + "\n"


# ============================================================================
# Phase 4: Wire app/build.gradle(.kts)
# ============================================================================

def _phase_wire_app(args, target: Path) -> int:
    print("\n─── Phase 4: Wire app/build.gradle ───")
    app_kts = target / "app" / "build.gradle.kts"
    app_groovy = target / "app" / "build.gradle"

    if app_kts.is_file():
        return _wire_app_generic(args, app_kts, kts=True)
    if app_groovy.is_file():
        return _wire_app_generic(args, app_groovy, kts=False)
    print(f"  ⚠ Không tìm thấy app/build.gradle(.kts)", file=sys.stderr)
    return 1


def _wire_app_generic(args, path: Path, kts: bool) -> int:
    text = path.read_text(encoding="utf-8")

    # Identify which modules to wire + classify each as impl vs lintChecks
    # Skip modules that weren't actually copied (don't reference non-existent dirs)
    target = path.parent.parent  # app/build.gradle(.kts) → app/ → target/
    to_wire = []
    for module in args.modules:
        if not (target / module).is_dir():
            # Module wasn't copied (e.g. fork missing it) — skip wire too
            continue
        kind = "lintChecks" if module.endswith(LINT_MODULE_SUFFIX) else "implementation"
        to_wire.append((module, kind))

    if not to_wire:
        print(f"  ⚠ No modules to wire in {path.name}", file=sys.stderr)
        return 1

    changes: List[str] = []
    for module, kind in to_wire:
        if _app_has_dep(text, module, impl_kind=kind):
            print(f"  · {path.name}: already has {kind}(project(\":{module}\"))")
            continue
        line = _dependency_line(module, kind, kts)
        changes.append(line)
        print(f"  + {path.name}: {line.strip()}")

    if not changes:
        return 0

    new_text = _inject_into_dependencies_block(text, changes, kts=kts)
    if new_text is None:
        print(f"  ❌ Không tìm thấy `dependencies {{...}}` block trong {path.name}",
              file=sys.stderr)
        print(f"     Thêm tay các dòng dưới vào dependencies block:", file=sys.stderr)
        for line in changes:
            print(f"        {line.strip()}", file=sys.stderr)
        return 1

    if args.dry_run:
        return 0

    _backup(path)
    path.write_text(new_text, encoding="utf-8")
    return 0


def _dependency_line(module: str, kind: str, kts: bool) -> str:
    """Format a single dependency line.

    kind ∈ {"implementation", "lintChecks"}
    """
    if kts:
        return f'    {kind}(project(":{module}"))\n'
    else:
        return f"    {kind} project(':{module}')\n"


def _app_has_dep(text: str, module: str, impl_kind: str) -> bool:
    """Check whether text already contains the dep we'd add.

    impl_kind ∈ {"implementation", "lintChecks"}
    """
    patterns = [
        # Kotlin DSL: kind(project(":mod"))
        rf'{re.escape(impl_kind)}\s*\(\s*project\s*\(\s*"{re.escape(":" + module)}"\s*\)\s*\)',
        # Groovy: kind project(':mod')
        rf"{re.escape(impl_kind)}\s+project\s*\(\s*'{re.escape(':' + module)}'\s*\)",
        rf"{re.escape(impl_kind)}\s+project\s*\(\s*\"{re.escape(':' + module)}\"\s*\)",
    ]
    for p in patterns:
        if re.search(p, text):
            return True
    return False


def _inject_into_dependencies_block(text: str, lines_to_add: List[str], kts: bool) -> Optional[str]:
    """Find top-level `dependencies {` block and insert lines just before closing `}`.
    Returns None if can't find a suitable block.

    Strategy:
      1. Find every `dependencies {` occurrence
      2. Track brace depth to find matching `}`
      3. Pick the LAST one (heuristic — usually the real top-level deps block,
         since buildscript/pluginManagement come earlier in the file).
    """
    # Find all "dependencies {" candidates with their start position
    candidates = []
    for m in re.finditer(r'\bdependencies\s*\{', text):
        start = m.end()  # position right after the opening '{'
        # Walk forward, count braces
        depth = 1
        i = start
        # Mask strings + comments to avoid counting braces inside them
        masked = _mask_strings_and_comments(text)
        while i < len(masked) and depth > 0:
            ch = masked[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    candidates.append((m.start(), i))  # (block_start_of_keyword, close_brace_pos)
                    break
            i += 1

    if not candidates:
        return None

    # Pick last (usually the real top-level dependencies block)
    _, close_pos = candidates[-1]

    # Insert before close_pos
    insert_block = "".join(lines_to_add)
    # Ensure leading newline if previous char isn't already \n
    if close_pos > 0 and text[close_pos - 1] != "\n":
        insert_block = "\n" + insert_block
    return text[:close_pos] + insert_block + text[close_pos:]


def _mask_strings_and_comments(text: str) -> str:
    """Replace string/comment chars with spaces (preserves indices).
    Naive but good enough for Gradle DSL files."""
    out = list(text)
    n = len(out)
    i = 0
    while i < n:
        c = out[i]
        # Line comment
        if c == "/" and i + 1 < n and out[i + 1] == "/":
            while i < n and out[i] != "\n":
                out[i] = " "
                i += 1
            continue
        # Block comment
        if c == "/" and i + 1 < n and out[i + 1] == "*":
            out[i] = " "
            out[i + 1] = " "
            i += 2
            while i < n - 1 and not (out[i] == "*" and out[i + 1] == "/"):
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n - 1:
                out[i] = " "
                out[i + 1] = " "
                i += 2
            continue
        # Strings: "..." (handle escape \")
        if c == '"':
            out[i] = " "
            i += 1
            while i < n and out[i] != '"':
                if out[i] == "\\" and i + 1 < n:
                    out[i] = " "
                    out[i + 1] = " "
                    i += 2
                    continue
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "
                i += 1
            continue
        # Strings: '...'
        if c == "'":
            out[i] = " "
            i += 1
            while i < n and out[i] != "'":
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "
                i += 1
            continue
        i += 1
    return "".join(out)


# ============================================================================
# Phase 5.5: Reconcile catalog (delegate to reconcile_catalog.py)
# ============================================================================

def _phase_reconcile(args, cache_dir: Path, target: Path) -> int:
    """Delegate to reconcile_catalog.py — patch module files để dùng key catalog
    của target nếu target đã có lib tương ứng.

    Returns:
      0 — done (patched hoặc không cần patch)
      1 — done but có lib missing trong target catalog (warn)
      2 — fatal error
    """
    print("\n─── Phase 5.5: Reconcile catalog (patch module catalog refs) ───")
    rec_script = Path(__file__).parent / "reconcile_catalog.py"
    if not rec_script.is_file():
        print(f"  ⚠ reconcile_catalog.py not found, skipping")
        return 0

    if args.dry_run:
        print(f"  (dry-run: would run reconcile_catalog.py --target {target})")
        return 0

    cmd = [sys.executable, str(rec_script),
           "--target", str(target),
           "--cache-dir", str(cache_dir),
           "--modules", *args.modules]

    try:
        result = subprocess.run(cmd, check=False, capture_output=True, text=True)
        if result.stdout:
            print(result.stdout, end="")
        if result.stderr:
            print(result.stderr, end="", file=sys.stderr)
        return result.returncode if result.returncode in (0, 1) else 2
    except Exception as e:
        print(f"  ❌ reconcile_catalog.py failed: {e}", file=sys.stderr)
        return 2


# ============================================================================
# Phase 5: Align Gradle (delegate to align_gradle.py)
# ============================================================================

def _phase_align(args, target: Path) -> int:
    """Run align_gradle.py to scan compat between module and target.

    Returns:
      0 — no findings (compatible)
      1 — findings present (any severity) — surface as 'warn' in summary
      2 — fatal error running align_gradle.py
    """
    print("\n─── Phase 5: Align Gradle (scan compat) ───")
    align_script = Path(__file__).parent / "align_gradle.py"
    if not align_script.is_file():
        print(f"  ⚠ align_gradle.py not found at {align_script}, skipping align phase",
              file=sys.stderr)
        return 0

    if args.dry_run:
        print(f"  (dry-run: would run align_gradle.py --target {target})")
        return 0

    cmd = [sys.executable, str(align_script), "--target", str(target)]
    if args.apply_catalog:
        cmd.append("--apply")
    cmd.extend(["--fail-on", "none"])  # never block setup_module on align findings

    try:
        result = subprocess.run(cmd, check=False, capture_output=True, text=True)
        # Forward output to user
        if result.stdout:
            print(result.stdout, end="")
        if result.stderr:
            print(result.stderr, end="", file=sys.stderr)
        if result.returncode != 0:
            return 2
        # Detect findings by parsing "Found N mismatch(es)" line
        m = re.search(r"Found (\d+) mismatch", result.stdout)
        if m and int(m.group(1)) > 0:
            return 1
        return 0
    except Exception as e:
        print(f"  ❌ align_gradle.py failed: {e}", file=sys.stderr)
        return 2


# ============================================================================
# Backup helper
# ============================================================================

def _backup(path: Path) -> None:
    bak = path.with_suffix(path.suffix + ".bak")
    if not bak.exists():
        shutil.copy2(path, bak)


# ============================================================================
# Entry point
# ============================================================================

if __name__ == "__main__":
    sys.exit(main())
