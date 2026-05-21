#!/usr/bin/env python3
"""
reconcile_catalog.py — Đối chiếu catalog refs trong module's build.gradle.kts
với target's `gradle/libs.versions.toml`.

Nguyên tắc:
  - Module được copy nguyên từ upstream repo, dùng key catalog của repo gốc
    (vd `libs.androidx.core.ktx`).
  - Target project có thể đã có lib tương ứng nhưng đặt key khác
    (vd `core-ktx`, `androidxCoreKtx`).
  - Nếu match coordinate → patch module file đổi sang KEY CỦA TARGET.
  - KHÔNG tự thêm key mới vào target catalog (giữ catalog target sạch).

Workflow:
  1. Load source catalog (từ cache repo's gradle/libs.versions.toml)
  2. Load target catalog
  3. Scan mỗi module's build.gradle(.kts) → list of catalog refs
  4. Cho mỗi ref:
       a. Lookup coordinate trong source catalog
       b. Lookup key của coordinate trong target catalog
       c. Nếu key target ≠ key source → patch (rename ref in module file)
  5. Sinh report `docs/analytics/catalog-reconcile-report.md`

Usage:
    python reconcile_catalog.py --target /path/to/android/project \\
        [--cache-dir .claude-cache/firebase-events-upstream] \\
        [--modules firebase-events firebase-events-lint app-events] \\
        [--dry-run]

Exit codes:
    0 — OK (patched hoặc không cần patch)
    1 — Có lib module cần nhưng target chưa có (cần manual thêm)
    2 — Lỗi parse / missing catalog file
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    import tomllib
except ImportError:
    tomllib = None  # type: ignore


CACHE_SUBDIR = ".claude-cache/firebase-events-upstream"
DEFAULT_MODULES = ["firebase-events", "firebase-events-lint", "app-events"]


@dataclass
class CatalogEntry:
    """1 entry trong [libraries] hoặc [plugins] block."""
    key: str            # raw TOML key (vd "androidx-core-ktx")
    accessor: str       # Kotlin DSL form (vd "androidx.core.ktx")
    coordinate: str     # "group:artifact" hoặc plugin id


@dataclass
class Catalog:
    """Catalog parsed từ libs.versions.toml."""
    libraries: List[CatalogEntry] = field(default_factory=list)
    plugins: List[CatalogEntry] = field(default_factory=list)

    def find_library_by_coord(self, coord: str) -> Optional[CatalogEntry]:
        for entry in self.libraries:
            if entry.coordinate == coord:
                return entry
        return None

    def find_plugin_by_id(self, plugin_id: str) -> Optional[CatalogEntry]:
        for entry in self.plugins:
            if entry.coordinate == plugin_id:
                return entry
        return None

    def find_library_by_accessor(self, accessor: str) -> Optional[CatalogEntry]:
        for entry in self.libraries:
            if entry.accessor == accessor:
                return entry
        return None

    def find_plugin_by_accessor(self, accessor: str) -> Optional[CatalogEntry]:
        for entry in self.plugins:
            if entry.accessor == accessor:
                return entry
        return None


@dataclass
class Rename:
    """1 rename cần áp dụng: từ accessor cũ → accessor mới."""
    kind: str          # "lib" | "plugin"
    old_accessor: str  # vd "androidx.core.ktx"
    new_accessor: str  # vd "core.ktx"
    coordinate: str    # for reporting


@dataclass
class MissingRef:
    """Module dùng ref nhưng target catalog không có lib tương ứng."""
    module: str
    file: str
    kind: str          # "lib" | "plugin"
    accessor: str
    source_coordinate: Optional[str] = None


# ============================================================================
# CLI
# ============================================================================

def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Reconcile module catalog refs with target catalog")
    ap.add_argument("--target", default=".", help="Android project root")
    ap.add_argument("--cache-dir", default=None,
                    help=f"Override cache dir (default: <target>/{CACHE_SUBDIR})")
    ap.add_argument("--modules", nargs="+", default=DEFAULT_MODULES,
                    help="Module subdirs to scan")
    ap.add_argument("--out", default="docs/analytics/catalog-reconcile-report.md",
                    help="Report output path")
    ap.add_argument("--dry-run", action="store_true",
                    help="Don't actually patch files")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args(argv if argv is not None else sys.argv[1:])

    if tomllib is None:
        print("❌ Python 3.11+ required (cần tomllib)", file=sys.stderr)
        return 2

    target = Path(args.target).resolve()
    cache_dir = Path(args.cache_dir).resolve() if args.cache_dir \
        else (target / CACHE_SUBDIR).resolve()

    if not args.quiet:
        print("═══ Reconcile catalog ═══")
        print(f"  Target:    {target}")
        print(f"  Cache:     {cache_dir}")
        print(f"  Modules:   {', '.join(args.modules)}")
        print()

    # 1. Load source catalog
    source_toml_path = cache_dir / "gradle" / "libs.versions.toml"
    if not source_toml_path.is_file():
        print(f"⚠ Source repo không có {source_toml_path.relative_to(cache_dir.parent) if cache_dir.parent in source_toml_path.parents else source_toml_path}")
        print(f"  → Module có thể dùng direct coord (không cần reconcile). Skip.")
        return 0

    source_catalog = _load_catalog(source_toml_path)
    if not args.quiet:
        print(f"  → Source catalog: {len(source_catalog.libraries)} libraries, {len(source_catalog.plugins)} plugins")

    # 2. Load target catalog
    target_toml_path = target / "gradle" / "libs.versions.toml"
    if not target_toml_path.is_file():
        print(f"⚠ Target không có gradle/libs.versions.toml")
        print(f"  → Module sẽ dùng key của upstream. Cân nhắc tạo catalog ở target.")
        return 0

    target_catalog = _load_catalog(target_toml_path)
    if not args.quiet:
        print(f"  → Target catalog: {len(target_catalog.libraries)} libraries, {len(target_catalog.plugins)} plugins")
        print()

    # 3. Scan module files + collect refs
    all_refs: List[Tuple[str, Path, str, str]] = []  # (module, file, kind, accessor)
    for module in args.modules:
        module_dir = target / module
        if not module_dir.is_dir():
            continue
        for gradle_file in _find_gradle_files(module_dir):
            refs = _extract_catalog_refs(gradle_file.read_text(encoding="utf-8"))
            for kind, accessor in refs:
                all_refs.append((module, gradle_file, kind, accessor))

    if not all_refs:
        print("→ Không tìm thấy catalog ref nào trong module files. Skip.")
        return 0

    # 4. Match refs to coordinates → build rename map
    renames: List[Rename] = []
    missing: List[MissingRef] = []
    seen_accessors: set = set()

    for module, gradle_file, kind, accessor in all_refs:
        if (kind, accessor) in seen_accessors:
            continue
        seen_accessors.add((kind, accessor))

        if kind == "lib":
            src_entry = source_catalog.find_library_by_accessor(accessor)
            if src_entry is None:
                # Ref không có trong source catalog → bỏ qua (unknown)
                continue
            target_entry = target_catalog.find_library_by_coord(src_entry.coordinate)
            if target_entry is None:
                missing.append(MissingRef(
                    module=module,
                    file=str(gradle_file.relative_to(target)),
                    kind="lib",
                    accessor=accessor,
                    source_coordinate=src_entry.coordinate,
                ))
                continue
            if target_entry.accessor != accessor:
                renames.append(Rename(
                    kind="lib",
                    old_accessor=accessor,
                    new_accessor=target_entry.accessor,
                    coordinate=src_entry.coordinate,
                ))

        elif kind == "plugin":
            src_entry = source_catalog.find_plugin_by_accessor(accessor)
            if src_entry is None:
                continue
            target_entry = target_catalog.find_plugin_by_id(src_entry.coordinate)
            if target_entry is None:
                missing.append(MissingRef(
                    module=module,
                    file=str(gradle_file.relative_to(target)),
                    kind="plugin",
                    accessor=accessor,
                    source_coordinate=src_entry.coordinate,
                ))
                continue
            if target_entry.accessor != accessor:
                renames.append(Rename(
                    kind="plugin",
                    old_accessor=accessor,
                    new_accessor=target_entry.accessor,
                    coordinate=src_entry.coordinate,
                ))

    # 5. Print plan
    if not renames and not missing:
        print("✓ All module catalog refs match target catalog keys — no changes needed.")
        _write_report(target, args.out, source_catalog, target_catalog, renames, missing)
        return 0

    if renames:
        print(f"→ {len(renames)} catalog key mismatch(es) to patch in module files:")
        for r in renames:
            kind_tag = "" if r.kind == "lib" else "plugins."
            print(f"     libs.{kind_tag}{r.old_accessor}  →  libs.{kind_tag}{r.new_accessor}   ({r.coordinate})")
        print()

    # 6. Apply patches
    files_patched = 0
    if renames and not args.dry_run:
        for module in args.modules:
            module_dir = target / module
            if not module_dir.is_dir():
                continue
            for gradle_file in _find_gradle_files(module_dir):
                if _patch_file(gradle_file, renames):
                    rel = gradle_file.relative_to(target)
                    print(f"     ~ patched {rel}")
                    files_patched += 1

    if args.dry_run and renames:
        print(f"(dry-run: would patch up to {len(args.modules) * 2} module gradle files)")

    # 7. Report missing
    if missing:
        print()
        print(f"⚠ {len(missing)} lib/plugin module cần nhưng target catalog không có:")
        for m in missing:
            kind_tag = "" if m.kind == "lib" else "plugins."
            print(f"     {m.module}/{m.file.split('/')[-1]}: libs.{kind_tag}{m.accessor}  →  {m.source_coordinate}")
        print(f"  → Thêm tay vào target's gradle/libs.versions.toml, hoặc chạy /analytics-align --apply để add placeholder.")

    _write_report(target, args.out, source_catalog, target_catalog, renames, missing)
    if not args.quiet:
        print()
        print(f"→ Wrote report: {args.out}")

    return 1 if missing else 0


# ============================================================================
# Catalog loading
# ============================================================================

def _load_catalog(toml_path: Path) -> Catalog:
    """Load and parse gradle/libs.versions.toml into structured catalog."""
    with toml_path.open("rb") as f:
        data = tomllib.load(f)

    catalog = Catalog()

    # Libraries
    for key, entry in (data.get("libraries") or {}).items():
        coord = _extract_lib_coord(entry)
        if coord is None:
            continue
        catalog.libraries.append(CatalogEntry(
            key=key,
            accessor=_key_to_accessor(key),
            coordinate=coord,
        ))

    # Plugins
    for key, entry in (data.get("plugins") or {}).items():
        plugin_id = _extract_plugin_id(entry)
        if plugin_id is None:
            continue
        catalog.plugins.append(CatalogEntry(
            key=key,
            accessor=_key_to_accessor(key),
            coordinate=plugin_id,
        ))

    return catalog


def _extract_lib_coord(entry: Any) -> Optional[str]:
    """Extract 'group:artifact' from a [libraries] entry (string or dict)."""
    if isinstance(entry, str):
        # "group:artifact:version" or "group:artifact"
        parts = entry.split(":")
        if len(parts) >= 2:
            return f"{parts[0]}:{parts[1]}"
        return None
    if isinstance(entry, dict):
        # Form 1: { module = "group:artifact", version.ref = "..." }
        if "module" in entry and isinstance(entry["module"], str):
            parts = entry["module"].split(":")
            if len(parts) >= 2:
                return f"{parts[0]}:{parts[1]}"
        # Form 2: { group = "...", name = "...", version.ref = "..." }
        if "group" in entry and "name" in entry:
            return f"{entry['group']}:{entry['name']}"
    return None


def _extract_plugin_id(entry: Any) -> Optional[str]:
    if isinstance(entry, str):
        # "id:version"
        return entry.split(":")[0] if ":" in entry else entry
    if isinstance(entry, dict):
        if "id" in entry and isinstance(entry["id"], str):
            return entry["id"]
    return None


def _key_to_accessor(key: str) -> str:
    """Convert TOML key to Kotlin DSL accessor.

    TOML rules (per Gradle docs):
      - Hyphens `-`, underscores `_`, and dots `.` all become `.` in Kotlin
      - camelCase preserved
    Examples:
      "androidx-core-ktx"       → "androidx.core.ktx"
      "androidx_core_ktx"       → "androidx.core.ktx"
      "androidxCoreKtx"         → "androidxCoreKtx"  (no separator → unchanged)
      "androidx.lifecycle.viewmodel.ktx" → "androidx.lifecycle.viewmodel.ktx"
    """
    return re.sub(r"[-_]", ".", key)


# ============================================================================
# Module file scanning
# ============================================================================

def _find_gradle_files(module_dir: Path) -> List[Path]:
    out = []
    for name in ("build.gradle.kts", "build.gradle"):
        f = module_dir / name
        if f.is_file():
            out.append(f)
    return out


# Match: libs.X.Y.Z  (lib refs)
# Match: libs.plugins.X.Y  (plugin refs)
# Bounded by word/paren chars on the left, identifier chars on the right
_REF_RE = re.compile(
    r"(?<![.\w])libs\.((?:plugins\.)?[a-zA-Z][\w]*(?:\.[a-zA-Z][\w]*)*)",
)


def _extract_catalog_refs(text: str) -> List[Tuple[str, str]]:
    """Extract list of (kind, accessor) pairs from a build.gradle text.

    kind ∈ {"lib", "plugin"}
    accessor: dotted form WITHOUT the "plugins." prefix
              vd "androidx.core.ktx" hoặc "androidLibrary"

    Strings/comments are masked before matching to avoid false positives.
    """
    masked = _mask_strings_and_comments(text)
    out = []
    seen = set()
    for m in _REF_RE.finditer(masked):
        ref = m.group(1)  # vd "plugins.androidLibrary" hoặc "androidx.core.ktx"
        if ref.startswith("plugins."):
            kind = "plugin"
            accessor = ref[len("plugins."):]
        else:
            kind = "lib"
            accessor = ref
        if (kind, accessor) in seen:
            continue
        seen.add((kind, accessor))
        out.append((kind, accessor))
    return out


def _mask_strings_and_comments(text: str) -> str:
    """Reuse from setup_module.py: blank out strings + comments."""
    out = list(text)
    n = len(out)
    i = 0
    while i < n:
        c = out[i]
        if c == "/" and i + 1 < n and out[i + 1] == "/":
            while i < n and out[i] != "\n":
                out[i] = " "
                i += 1
            continue
        if c == "/" and i + 1 < n and out[i + 1] == "*":
            out[i] = " "; out[i + 1] = " "; i += 2
            while i < n - 1 and not (out[i] == "*" and out[i + 1] == "/"):
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n - 1:
                out[i] = " "; out[i + 1] = " "; i += 2
            continue
        if c == '"':
            out[i] = " "; i += 1
            while i < n and out[i] != '"':
                if out[i] == "\\" and i + 1 < n:
                    out[i] = " "; out[i + 1] = " "; i += 2
                    continue
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "; i += 1
            continue
        if c == "'":
            out[i] = " "; i += 1
            while i < n and out[i] != "'":
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            if i < n:
                out[i] = " "; i += 1
            continue
        i += 1
    return "".join(out)


# ============================================================================
# Patching
# ============================================================================

def _patch_file(path: Path, renames: List[Rename]) -> bool:
    """Apply all renames to a file. Returns True if file was modified."""
    text = path.read_text(encoding="utf-8")
    new_text = text

    for r in renames:
        if r.kind == "lib":
            old_pattern = re.compile(
                rf"(?<![.\w])libs\.{re.escape(r.old_accessor)}(?![.\w])"
            )
            new_text = old_pattern.sub(f"libs.{r.new_accessor}", new_text)
        else:  # plugin
            old_pattern = re.compile(
                rf"(?<![.\w])libs\.plugins\.{re.escape(r.old_accessor)}(?![.\w])"
            )
            new_text = old_pattern.sub(f"libs.plugins.{r.new_accessor}", new_text)

    if new_text == text:
        return False

    bak = path.with_suffix(path.suffix + ".bak")
    if not bak.exists():
        shutil.copy2(path, bak)
    path.write_text(new_text, encoding="utf-8")
    return True


# ============================================================================
# Report
# ============================================================================

def _write_report(
    target: Path, out_rel: str,
    source_catalog: Catalog, target_catalog: Catalog,
    renames: List[Rename], missing: List[MissingRef],
) -> None:
    path = (target / out_rel).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)

    lines = []
    lines.append("# Catalog reconcile report")
    lines.append("")
    lines.append("Sinh bởi `reconcile_catalog.py` — đối chiếu catalog refs trong module với target.")
    lines.append("")

    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Source catalog: {len(source_catalog.libraries)} libraries, {len(source_catalog.plugins)} plugins")
    lines.append(f"- Target catalog: {len(target_catalog.libraries)} libraries, {len(target_catalog.plugins)} plugins")
    lines.append(f"- Patched refs:   {len(renames)}")
    lines.append(f"- Missing in target: {len(missing)}")
    lines.append("")

    if renames:
        lines.append("## Patched (module files đã sửa)")
        lines.append("")
        lines.append("| Kind | Cũ (key của upstream) | Mới (key của target) | Coordinate |")
        lines.append("|---|---|---|---|")
        for r in renames:
            kind_tag = "" if r.kind == "lib" else "plugins."
            lines.append(f"| {r.kind} | `libs.{kind_tag}{r.old_accessor}` | `libs.{kind_tag}{r.new_accessor}` | `{r.coordinate}` |")
        lines.append("")

    if missing:
        lines.append("## Missing (target catalog chưa có)")
        lines.append("")
        lines.append("Module reference các lib/plugin sau nhưng target catalog không có entry tương ứng.")
        lines.append("Thêm tay vào `gradle/libs.versions.toml`, hoặc chạy `/analytics-align --apply`.")
        lines.append("")
        lines.append("| Module | File | Ref | Coordinate cần |")
        lines.append("|---|---|---|---|")
        for m in missing:
            kind_tag = "" if m.kind == "lib" else "plugins."
            lines.append(f"| {m.module} | `{m.file}` | `libs.{kind_tag}{m.accessor}` | `{m.source_coordinate}` |")
        lines.append("")

    if not renames and not missing:
        lines.append("✅ Mọi catalog ref trong module đã match key của target.")
        lines.append("")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
