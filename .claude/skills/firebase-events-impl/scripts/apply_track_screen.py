#!/usr/bin/env python3
"""
apply_track_screen.py — Batch wrap Composable screens với TrackedScreen.

Đọc event-spec.yaml để biết list screen + Composable class, tìm file Kotlin
chứa @Composable fun đó, và:
1. Add `import {package}.event.TrackedScreen` + `import {package}.event.ScreenName`
2. Wrap function body bằng `TrackedScreen(ScreenName.XXX) { ... }`

Idempotent: chạy lại nhiều lần safe — skip nếu đã wrap.

Usage:
    python apply_track_screen.py \\
        --spec docs/analytics/event-spec.yaml \\
        --src app/src/main/java \\
        [--dry-run] [--no-confirm] [--only HomeScreen,SettingsScreen]
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML required. pip install pyyaml", file=sys.stderr)
    sys.exit(1)


# ─────────────────────────────────────────────────────────────────────
# Patterns
# ─────────────────────────────────────────────────────────────────────
COMPOSABLE_FUN_RE = re.compile(
    r"(@Composable\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|private\s+|internal\s+|protected\s+)?fun\s+)(\w+)\s*\(",
    re.MULTILINE,
)
TRACKED_SCREEN_MARK_RE = re.compile(r"TrackedScreen\s*\(\s*ScreenName\.[A-Z_]+\s*[,)]")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*$", re.MULTILINE)


# ─────────────────────────────────────────────────────────────────────
# Paren/brace matching (same as scaffold_spec.py — copy here to avoid coupling)
# ─────────────────────────────────────────────────────────────────────
def find_matching_paren(text: str, open_idx: int) -> int:
    assert text[open_idx] == "(", "open_idx must point at '('"
    depth, i = 0, open_idx
    in_str = in_triple = in_char = in_line = in_block = False
    while i < len(text):
        c = text[i]
        if in_line:
            if c == "\n": in_line = False
        elif in_block:
            if c == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block = False; i += 1
        elif in_triple:
            if c == '"' and text[i:i+3] == '"""':
                in_triple = False; i += 2
        elif in_str:
            if c == "\\": i += 1
            elif c == '"': in_str = False
        elif in_char:
            if c == "\\": i += 1
            elif c == "'": in_char = False
        else:
            if c == "/" and i + 1 < len(text):
                if text[i + 1] == "/": in_line = True; i += 1
                elif text[i + 1] == "*": in_block = True; i += 1
            elif c == '"':
                if text[i:i+3] == '"""': in_triple = True; i += 2
                else: in_str = True
            elif c == "'": in_char = True
            elif c == "(": depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0: return i
        i += 1
    return len(text)


def find_matching_brace(text: str, open_idx: int) -> int:
    assert text[open_idx] == "{", "open_idx must point at '{'"
    depth, i = 0, open_idx
    in_str = in_triple = in_char = in_line = in_block = False
    while i < len(text):
        c = text[i]
        if in_line:
            if c == "\n": in_line = False
        elif in_block:
            if c == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block = False; i += 1
        elif in_triple:
            if c == '"' and text[i:i+3] == '"""':
                in_triple = False; i += 2
        elif in_str:
            if c == "\\": i += 1
            elif c == '"': in_str = False
        elif in_char:
            if c == "\\": i += 1
            elif c == "'": in_char = False
        else:
            if c == "/" and i + 1 < len(text):
                if text[i + 1] == "/": in_line = True; i += 1
                elif text[i + 1] == "*": in_block = True; i += 1
            elif c == '"':
                if text[i:i+3] == '"""': in_triple = True; i += 2
                else: in_str = True
            elif c == "'": in_char = True
            elif c == "{": depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0: return i
        i += 1
    return len(text)


# ─────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────
def screen_id_to_const(screen_id: str) -> str:
    """home → HOME; user_profile → USER_PROFILE."""
    return screen_id.upper()


@dataclass
class ScreenRef:
    spec_id: str        # 'home'
    class_name: str     # 'HomeScreen'
    const_name: str     # 'HOME'


def find_composable_file(class_name: str, src_root: Path) -> Path | None:
    """Find .kt file containing `@Composable fun <class_name>(`."""
    for kt in src_root.rglob("*.kt"):
        try:
            text = kt.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        for m in COMPOSABLE_FUN_RE.finditer(text):
            if m.group(2) == class_name:
                return kt
    return None


def add_imports_if_missing(text: str, package: str, screen_const: str) -> tuple[str, bool]:
    """Ensure `import {package}.event.TrackedScreen` and `ScreenName` are present.

    Returns (new_text, changed).
    """
    changed = False
    needed = [
        f"{package}.event.TrackedScreen",
        f"{package}.event.ScreenName",
    ]
    for imp in needed:
        if re.search(rf"^\s*import\s+{re.escape(imp)}\s*$", text, re.MULTILINE):
            continue
        # Insert after the last existing import, or after package line
        last_imp = list(re.finditer(r"^\s*import\s+[\w.]+\s*$", text, re.MULTILINE))
        if last_imp:
            ins = last_imp[-1].end()
            text = text[:ins] + f"\nimport {imp}" + text[ins:]
        else:
            pkg = PACKAGE_RE.search(text)
            if pkg:
                ins = pkg.end()
                text = text[:ins] + f"\n\nimport {imp}" + text[ins:]
            else:
                text = f"import {imp}\n" + text
        changed = True
    return text, changed


def wrap_composable_body(text: str, class_name: str, screen_const: str) -> tuple[str, str]:
    """Wrap the body of `@Composable fun <class_name>(...) { ... }` with
    `TrackedScreen(ScreenName.<const>) { ... }`.

    Returns (new_text, status) where status is one of:
      'wrapped' | 'already' | 'not_found' | 'empty_body'
    """
    # Find the function declaration
    m = None
    for cand in COMPOSABLE_FUN_RE.finditer(text):
        if cand.group(2) == class_name:
            m = cand
            break
    if not m:
        return text, "not_found"

    # Find '(', matching ')', then '{' opening body
    paren_open = text.find("(", m.end() - 1)
    if paren_open < 0:
        return text, "not_found"
    paren_close = find_matching_paren(text, paren_open)
    brace_open = text.find("{", paren_close)
    if brace_open < 0:
        return text, "not_found"
    if brace_open - paren_close > 200:
        # Probably parsing went wrong (Modifier=Modifier.padding() etc).
        return text, "not_found"
    brace_close = find_matching_brace(text, brace_open)
    if brace_close == len(text):
        return text, "not_found"

    body_inner = text[brace_open + 1 : brace_close]
    if not body_inner.strip():
        return text, "empty_body"  # don't wrap empty composables

    # Already wrapped?
    if TRACKED_SCREEN_MARK_RE.search(body_inner[:200]):
        return text, "already"

    # Determine indent
    indent_match = re.search(r"^(\s*)\S", body_inner.lstrip("\n"))
    base_indent = ""
    if indent_match:
        base_indent = indent_match.group(1)
    inner_indent = base_indent + "    "

    # Re-indent existing body content (best effort)
    body_lines = body_inner.split("\n")
    # Detect existing base indent of first non-empty line
    existing_base = None
    for line in body_lines:
        if line.strip():
            existing_base = re.match(r"^(\s*)", line).group(1)
            break
    if existing_base is None:
        existing_base = base_indent

    indented_lines = []
    for line in body_lines:
        if line.strip():
            # strip existing base indent
            stripped = line[len(existing_base):] if line.startswith(existing_base) else line
            indented_lines.append(inner_indent + stripped)
        else:
            indented_lines.append(line)
    new_body_inner = "\n".join(indented_lines)

    wrapped = (
        f"\n{base_indent}TrackedScreen(ScreenName.{screen_const}) {{\n"
        f"{new_body_inner}\n"
        f"{base_indent}}}\n"
    )

    new_text = text[:brace_open + 1] + wrapped + text[brace_close:]
    return new_text, "wrapped"


def get_package(text: str) -> str | None:
    m = PACKAGE_RE.search(text)
    return m.group(1) if m else None


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────
def collect_screens_from_spec(spec: dict) -> list[ScreenRef]:
    """Extract screens that are Composables (not Activities/Fragments)."""
    out: list[ScreenRef] = []
    for s in spec.get("screens") or []:
        class_name = s.get("class")
        spec_id = s.get("id")
        if not class_name or not spec_id:
            continue
        # Skip if class looks like Activity/Fragment (these use BaseTrackedActivity instead)
        if class_name.endswith(("Activity", "Fragment")) and "Screen" not in class_name:
            continue
        # Skip NavRoute synthetic screens
        if class_name.startswith("NavRoute"):
            continue
        out.append(ScreenRef(
            spec_id=spec_id,
            class_name=class_name,
            const_name=screen_id_to_const(spec_id),
        ))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Batch wrap Composable screens với TrackedScreen")
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--src", type=Path, required=True,
                        help="App source root (e.g. app/src/main/java)")
    parser.add_argument("--dry-run", action="store_true",
                        help="In ra plan, không write file")
    parser.add_argument("--only", type=str, default=None,
                        help="Comma-separated list of class names (limit scope)")
    parser.add_argument("--no-confirm", action="store_true",
                        help="Không hỏi confirmation, apply luôn")
    args = parser.parse_args()

    if not args.spec.exists():
        print(f"ERROR: spec not found: {args.spec}", file=sys.stderr)
        return 1
    if not args.src.exists():
        print(f"ERROR: src not found: {args.src}", file=sys.stderr)
        return 1

    spec = yaml.safe_load(args.spec.read_text(encoding="utf-8"))
    package = (spec.get("project") or {}).get("package")
    if not package:
        print("ERROR: spec.project.package required", file=sys.stderr)
        return 1

    screens = collect_screens_from_spec(spec)
    if args.only:
        only = set(args.only.split(","))
        screens = [s for s in screens if s.class_name in only]
    if not screens:
        print("Không tìm thấy Composable screen nào trong spec.")
        print("(Chỉ wrap class kết thúc bằng Screen/Route/Page, không phải Activity/Fragment)")
        return 0

    # Phase 1: find files for each screen
    print(f"\n═══ Phase 1: Locate files for {len(screens)} screens ═══\n")
    plan: list[tuple[ScreenRef, Path]] = []
    for sref in screens:
        f = find_composable_file(sref.class_name, args.src)
        if f is None:
            print(f"  ⚠ {sref.class_name:30s} — file không tìm thấy (skip)")
            continue
        try:
            rel = f.relative_to(Path.cwd())
        except ValueError:
            rel = f
        print(f"  ✓ {sref.class_name:30s} → {rel}")
        plan.append((sref, f))

    if not plan:
        print("\nKhông có file nào để wrap.")
        return 0

    if not args.no_confirm and not args.dry_run:
        print(f"\nSẽ wrap {len(plan)} file. Tiếp tục? [y/N] ", end="", flush=True)
        ans = input().strip().lower()
        if ans not in ("y", "yes"):
            print("Cancelled.")
            return 0

    # Phase 2: apply transformations
    print(f"\n═══ Phase 2: Apply wrapping ═══\n")
    counts = {"wrapped": 0, "already": 0, "empty_body": 0, "not_found": 0, "failed": 0}
    for sref, file in plan:
        try:
            text = file.read_text(encoding="utf-8")
        except Exception as e:
            print(f"  ✗ {file.name}: read failed ({e})")
            counts["failed"] += 1
            continue

        pkg_in_file = get_package(text) or package
        # Add imports first (only if we'll actually wrap)
        new_text, status = wrap_composable_body(text, sref.class_name, sref.const_name)
        if status == "wrapped":
            new_text, _ = add_imports_if_missing(new_text, package, sref.const_name)

        marker = {
            "wrapped": "✓ wrapped",
            "already": "─ already",
            "empty_body": "○ empty (skipped)",
            "not_found": "? not found",
        }.get(status, "✗ failed")
        try:
            rel = file.relative_to(Path.cwd())
        except ValueError:
            rel = file
        print(f"  {marker:25s} {sref.class_name:25s} {rel}")

        counts[status] = counts.get(status, 0) + 1
        if status == "wrapped" and not args.dry_run:
            file.write_text(new_text, encoding="utf-8")

    # Summary
    print(f"\n═══ Summary ═══")
    print(f"  Wrapped:    {counts['wrapped']}")
    print(f"  Already:    {counts['already']}")
    print(f"  Empty body: {counts['empty_body']}")
    print(f"  Not found:  {counts['not_found']}")
    print(f"  Failed:     {counts['failed']}")
    if args.dry_run:
        print("\n(Dry-run — no files written)")
    elif counts["wrapped"]:
        print(f"\n✓ {counts['wrapped']} file đã wrap. Mở Android Studio kiểm tra format + Optimize Imports.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
