#!/usr/bin/env python3
"""
align_gradle.py — Scan dependencies + Gradle config của module `firebase-events`,
so sánh với target app, sinh report mismatch, và (optional) tự add missing
entries vào `gradle/libs.versions.toml`.

Usage:
    python align_gradle.py --target /path/to/android/project [options]

Phases:
    1. Parse module Gradle files (firebase-events + firebase-events-lint)
    2. Parse target Gradle files (app/build.gradle*, libs.versions.toml, root build.gradle*)
    3. Compare:
        - SDK levels (compileSdk, minSdk, targetSdk)
        - Kotlin plugin version
        - AGP version
        - Dependencies (which libs module uses, target catalog has them or not)
    4. Sinh report `docs/analytics/gradle-align-report.md`
    5. (Optional) Với --apply, add missing entries vào libs.versions.toml

KHÔNG tự sửa `app/build.gradle.kts` (compileSdk, minSdk, Kotlin version) — quá
rủi ro, có thể break chain dependencies. User đọc report rồi sửa tay.

Exit codes:
    0 — OK (gồm 'có findings nhưng chỉ báo cáo')
    1 — Có HIGH severity findings (CI nên fail)
    2 — Lỗi parse / không tìm thấy file Gradle
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    import tomllib  # Python 3.11+
except ImportError:
    tomllib = None  # type: ignore


# ============================================================================
# Data classes
# ============================================================================

@dataclass
class GradleInfo:
    """Parsed snapshot của 1 build.gradle(.kts) hoặc tổng hợp project."""
    source_label: str
    compile_sdk: Optional[int] = None
    min_sdk: Optional[int] = None
    target_sdk: Optional[int] = None
    java_target: Optional[str] = None  # "1.8" / "17" / ...
    kotlin_jvm_target: Optional[str] = None
    plugins: Dict[str, Optional[str]] = field(default_factory=dict)  # plugin_id -> version
    direct_deps: List[str] = field(default_factory=list)  # ["com.google.firebase:firebase-analytics:21.5.0", ...]
    catalog_refs: List[str] = field(default_factory=list)  # ["libs.firebase.analytics", ...]
    files_parsed: List[str] = field(default_factory=list)


@dataclass
class Finding:
    severity: str  # HIGH | MEDIUM | LOW | INFO
    category: str  # SDK | Plugin | Dependency | Catalog
    title: str
    detail: str
    suggested_fix: Optional[str] = None


SEVERITY_RANK = {"HIGH": 3, "MEDIUM": 2, "LOW": 1, "INFO": 0}


# ============================================================================
# CLI
# ============================================================================

def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Align Gradle config: module vs target app")
    ap.add_argument("--target", default=".", help="Android project root (default: cwd)")
    ap.add_argument("--module-dir", default=None,
                    help="Override module root (default: <target>/firebase-events/)")
    ap.add_argument("--lint-module-dir", default=None,
                    help="Override lint module root (default: <target>/firebase-events-lint/)")
    ap.add_argument("--out", default="docs/analytics/gradle-align-report.md",
                    help="Report output path (relative to target)")
    ap.add_argument("--apply", action="store_true",
                    help="Auto-add missing entries to libs.versions.toml "
                         "(does NOT modify app/build.gradle.kts)")
    ap.add_argument("--fail-on", default="high", choices=["high", "medium", "low", "none"],
                    help="Exit code 1 if findings at this severity exist (default: high)")
    ap.add_argument("--quiet", action="store_true", help="Minimal console output")
    args = ap.parse_args(argv if argv is not None else sys.argv[1:])

    target = Path(args.target).resolve()
    if not target.is_dir():
        print(f"❌ Target không tồn tại: {target}", file=sys.stderr)
        return 2

    module_dir = Path(args.module_dir).resolve() if args.module_dir else target / "firebase-events"
    lint_dir = Path(args.lint_module_dir).resolve() if args.lint_module_dir else target / "firebase-events-lint"

    if not module_dir.is_dir():
        print(f"❌ Module dir không tồn tại: {module_dir}", file=sys.stderr)
        print(f"   Chạy /analytics-import-module trước để clone module.", file=sys.stderr)
        return 2

    print(f"═══ Gradle align ═══")
    print(f"  Target:      {target}")
    print(f"  Module:      {module_dir}")
    print(f"  Lint module: {lint_dir if lint_dir.is_dir() else '(none)'}")
    print()

    # ---- Parse module ----
    module_info = _parse_module(module_dir, lint_dir if lint_dir.is_dir() else None)
    if not args.quiet:
        _print_info("Module", module_info)

    # ---- Parse target ----
    target_info = _parse_target(target)
    if not args.quiet:
        _print_info("Target", target_info)

    # ---- Parse libs.versions.toml (target) ----
    toml_path = target / "gradle" / "libs.versions.toml"
    target_toml = _parse_toml(toml_path) if toml_path.is_file() else None
    if target_toml and not args.quiet:
        n = len(target_toml.get("versions", {}))
        print(f"  → libs.versions.toml: {n} version entries")

    # ---- Compare ----
    findings: List[Finding] = []
    findings.extend(_compare_sdk(module_info, target_info))
    findings.extend(_compare_plugins(module_info, target_info))
    findings.extend(_compare_dependencies(module_info, target_info, target_toml))

    # ---- Report ----
    if not findings:
        print("\n✅ No mismatches found — module + target Gradle are compatible.")
    else:
        print(f"\n→ Found {len(findings)} mismatch(es):")
        for sev in ("HIGH", "MEDIUM", "LOW", "INFO"):
            n = sum(1 for f in findings if f.severity == sev)
            if n:
                print(f"  · {sev}: {n}")

    out_path = (target / args.out).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(_render_report(module_info, target_info, target_toml, findings),
                        encoding="utf-8")
    print(f"\n→ Wrote report: {out_path.relative_to(target) if out_path.is_relative_to(target) else out_path}")

    # ---- Apply (optional) ----
    if args.apply and target_toml is not None and toml_path.is_file():
        rc_apply = _apply_catalog_additions(toml_path, target_toml, module_info, findings)
        if rc_apply > 0:
            print(f"✓ Added {rc_apply} missing entries to libs.versions.toml")

    # ---- Exit code ----
    threshold_map = {"none": 99, "low": SEVERITY_RANK["LOW"],
                     "medium": SEVERITY_RANK["MEDIUM"], "high": SEVERITY_RANK["HIGH"]}
    threshold = threshold_map[args.fail_on]
    max_rank = max((SEVERITY_RANK[f.severity] for f in findings), default=-1)
    if max_rank >= threshold:
        return 1
    return 0


def _print_info(label: str, info: GradleInfo) -> None:
    print(f"  → {label} ({info.source_label}):")
    print(f"      compileSdk={info.compile_sdk}, minSdk={info.min_sdk}, targetSdk={info.target_sdk}")
    if info.plugins:
        pgs = ", ".join(f"{p}={v or '?'}" for p, v in info.plugins.items())
        print(f"      plugins: {pgs}")
    if info.direct_deps:
        print(f"      direct deps: {len(info.direct_deps)}")
    if info.catalog_refs:
        print(f"      catalog refs: {len(info.catalog_refs)}")


# ============================================================================
# Parsing — module side
# ============================================================================

def _parse_module(module_dir: Path, lint_dir: Optional[Path]) -> GradleInfo:
    info = GradleInfo(source_label=f"firebase-events @ {module_dir.name}")
    _parse_gradle_file_into(module_dir / "build.gradle.kts", info)
    _parse_gradle_file_into(module_dir / "build.gradle", info)
    if lint_dir is not None:
        _parse_gradle_file_into(lint_dir / "build.gradle.kts", info)
        _parse_gradle_file_into(lint_dir / "build.gradle", info)
    return info


def _parse_target(target: Path) -> GradleInfo:
    info = GradleInfo(source_label="app + root project")
    # root build.gradle (for plugins block with versions)
    _parse_gradle_file_into(target / "build.gradle.kts", info)
    _parse_gradle_file_into(target / "build.gradle", info)
    # app/build.gradle (for android block + deps)
    _parse_gradle_file_into(target / "app" / "build.gradle.kts", info)
    _parse_gradle_file_into(target / "app" / "build.gradle", info)
    return info


def _parse_gradle_file_into(path: Path, info: GradleInfo) -> None:
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8", errors="replace")
    masked = _mask_strings_and_comments(text)
    info.files_parsed.append(str(path.name))

    # SDK levels (look for patterns inside android { } block — for simplicity, scan whole file)
    for key, attr in [("compileSdk", "compile_sdk"),
                      ("minSdk", "min_sdk"),
                      ("targetSdk", "target_sdk"),
                      ("minSdkVersion", "min_sdk"),
                      ("targetSdkVersion", "target_sdk"),
                      ("compileSdkVersion", "compile_sdk")]:
        m = re.search(rf'\b{key}\s*[=]?\s*(\d+)\b', masked)
        if m and getattr(info, attr) is None:
            setattr(info, attr, int(m.group(1)))

    # Plugin block: parse `id("kotlin-android") version "1.9.0"` etc
    for m in re.finditer(
        r'id\s*\(\s*[\'"]([^\'"]+)[\'"]\s*\)(?:\s*version\s*[\'"]([^\'"]+)[\'"])?',
        masked,
    ):
        plugin_id = m.group(1)
        version = m.group(2)
        # Update only if not already known or if we have a new version
        if plugin_id not in info.plugins or (version and info.plugins[plugin_id] is None):
            info.plugins[plugin_id] = version

    # Kotlin DSL shortcut: kotlin("android") version "1.9.0"
    for m in re.finditer(
        r'kotlin\s*\(\s*[\'"]([^\'"]+)[\'"]\s*\)(?:\s*version\s*[\'"]([^\'"]+)[\'"])?',
        masked,
    ):
        plugin_id = f"org.jetbrains.kotlin.{m.group(1)}"
        version = m.group(2)
        if plugin_id not in info.plugins or (version and info.plugins[plugin_id] is None):
            info.plugins[plugin_id] = version

    # Dependencies: implementation("group:artifact:version") | implementation 'group:artifact:version'
    for m in re.finditer(
        r'(?:implementation|api|compileOnly|runtimeOnly|lintChecks|kapt|ksp)\s*'
        r'(?:\(\s*)?[\'"]([^\'"]+:[^\'"]+:[^\'"]+)[\'"]',
        masked,
    ):
        dep = m.group(1)
        if dep not in info.direct_deps:
            info.direct_deps.append(dep)

    # Platform BoM: implementation(platform("group:artifact:version"))
    for m in re.finditer(
        r'platform\s*\(\s*[\'"]([^\'"]+:[^\'"]+:[^\'"]+)[\'"]',
        masked,
    ):
        dep = "platform(" + m.group(1) + ")"
        if dep not in info.direct_deps:
            info.direct_deps.append(dep)

    # Catalog refs: implementation(libs.firebase.analytics) | implementation libs.firebase.analytics
    for m in re.finditer(
        r'(?:implementation|api|compileOnly|runtimeOnly|lintChecks|kapt|ksp)\s*'
        r'(?:\(\s*)?(?:platform\s*\(\s*)?(libs\.[\w.]+)',
        masked,
    ):
        ref = m.group(1)
        if ref not in info.catalog_refs:
            info.catalog_refs.append(ref)


# ============================================================================
# Parsing — libs.versions.toml
# ============================================================================

def _parse_toml(path: Path) -> Optional[Dict[str, Any]]:
    if tomllib is None:
        print(f"⚠ Python 3.11+ cần tomllib để parse {path.name}. Skipping.", file=sys.stderr)
        return None
    try:
        with path.open("rb") as f:
            return tomllib.load(f)
    except Exception as e:
        print(f"⚠ Failed to parse {path}: {e}", file=sys.stderr)
        return None


# ============================================================================
# Comparison rules
# ============================================================================

def _compare_sdk(module: GradleInfo, target: GradleInfo) -> List[Finding]:
    findings = []
    # compileSdk: target ≥ module
    if module.compile_sdk and target.compile_sdk:
        if target.compile_sdk < module.compile_sdk:
            findings.append(Finding(
                severity="HIGH",
                category="SDK",
                title=f"Target compileSdk ({target.compile_sdk}) thấp hơn module yêu cầu ({module.compile_sdk})",
                detail=(
                    f"Module `firebase-events` được compile với `compileSdk = {module.compile_sdk}` "
                    f"nhưng app target hiện tại `compileSdk = {target.compile_sdk}`. "
                    f"Khi link, có thể gặp lỗi `error: cannot find symbol` cho API mới hơn target.compileSdk."
                ),
                suggested_fix=(
                    f"Sửa `app/build.gradle.kts`:\n"
                    f"```kotlin\n"
                    f"android {{\n"
                    f"    compileSdk = {module.compile_sdk}\n"
                    f"}}\n"
                    f"```\n"
                    f"Hoặc bump cao hơn nếu cần."
                ),
            ))
    # minSdk: module yêu cầu API tối thiểu, target phải ≥
    if module.min_sdk and target.min_sdk:
        if target.min_sdk < module.min_sdk:
            findings.append(Finding(
                severity="HIGH",
                category="SDK",
                title=f"Target minSdk ({target.min_sdk}) thấp hơn module yêu cầu ({module.min_sdk})",
                detail=(
                    f"Module `firebase-events` được build với `minSdk = {module.min_sdk}`. "
                    f"App target có `minSdk = {target.min_sdk}` — sẽ bị manifest merger error: "
                    f"\"uses-sdk:minSdkVersion {target.min_sdk} cannot be smaller than version {module.min_sdk} declared in library\"."
                ),
                suggested_fix=(
                    f"Sửa `app/build.gradle.kts`:\n"
                    f"```kotlin\n"
                    f"android {{\n"
                    f"    defaultConfig {{\n"
                    f"        minSdk = {module.min_sdk}\n"
                    f"    }}\n"
                    f"}}\n"
                    f"```"
                ),
            ))
    # targetSdk: thông tin warning nếu mismatch (không block build)
    if module.target_sdk and target.target_sdk:
        if target.target_sdk < module.target_sdk:
            findings.append(Finding(
                severity="LOW",
                category="SDK",
                title=f"Target targetSdk ({target.target_sdk}) thấp hơn module ({module.target_sdk})",
                detail=(
                    f"Module dùng `targetSdk = {module.target_sdk}`, app target {target.target_sdk}. "
                    f"Không block build nhưng có thể behavior khác giữa các API level."
                ),
                suggested_fix=(
                    f"Cân nhắc bump `app/build.gradle.kts` `targetSdk = {module.target_sdk}` "
                    f"(yêu cầu test compat lại)."
                ),
            ))
    return findings


def _compare_plugins(module: GradleInfo, target: GradleInfo) -> List[Finding]:
    findings = []
    # Kotlin plugin version
    module_kotlin = _get_plugin_version(module, "org.jetbrains.kotlin")
    target_kotlin = _get_plugin_version(target, "org.jetbrains.kotlin")
    if module_kotlin and target_kotlin and module_kotlin != target_kotlin:
        sev = "MEDIUM" if _major_minor(module_kotlin) != _major_minor(target_kotlin) else "LOW"
        findings.append(Finding(
            severity=sev,
            category="Plugin",
            title=f"Kotlin version mismatch: module={module_kotlin} target={target_kotlin}",
            detail=(
                f"Module dùng Kotlin {module_kotlin}, app target {target_kotlin}. "
                f"Khác major.minor → risk binary incompat (metadata format khác). "
                f"Cùng major.minor, khác patch → thường OK."
            ),
            suggested_fix=(
                f"Đồng bộ về cùng version. Edit root `build.gradle.kts`:\n"
                f"```kotlin\nplugins {{ kotlin(\"android\") version \"{module_kotlin}\" apply false }}\n```\n"
                f"hoặc dùng version catalog `gradle/libs.versions.toml`:\n"
                f"```toml\n[versions]\nkotlin = \"{module_kotlin}\"\n```"
            ),
        ))

    # AGP version
    module_agp = _get_plugin_version(module, "com.android")
    target_agp = _get_plugin_version(target, "com.android")
    if module_agp and target_agp and module_agp != target_agp:
        findings.append(Finding(
            severity="MEDIUM" if _major_minor(module_agp) != _major_minor(target_agp) else "LOW",
            category="Plugin",
            title=f"Android Gradle Plugin (AGP) version mismatch: module={module_agp} target={target_agp}",
            detail=(
                f"Module dùng AGP {module_agp}, app dùng {target_agp}. "
                f"AGP major mismatch sẽ break composite build."
            ),
            suggested_fix=f"Đồng bộ về AGP {module_agp} hoặc cao hơn.",
        ))

    return findings


def _get_plugin_version(info: GradleInfo, prefix: str) -> Optional[str]:
    for pid, ver in info.plugins.items():
        if pid.startswith(prefix) and ver:
            return ver
    return None


def _major_minor(version: str) -> str:
    parts = version.split(".")
    return ".".join(parts[:2]) if len(parts) >= 2 else version


def _compare_dependencies(
    module: GradleInfo, target: GradleInfo, target_toml: Optional[Dict[str, Any]],
) -> List[Finding]:
    findings = []

    # 1. Module catalog refs: kiểm tra target catalog có entry tương ứng không
    if module.catalog_refs and target_toml is not None:
        libraries = target_toml.get("libraries", {}) or {}
        bundles = target_toml.get("bundles", {}) or {}
        for ref in module.catalog_refs:
            # ref: "libs.firebase.analytics" → key "firebase-analytics" (hoặc "firebase.analytics")
            key_dotted = ref.replace("libs.", "")
            key_dashed = key_dotted.replace(".", "-")
            if key_dotted in libraries or key_dashed in libraries:
                continue
            if key_dotted in bundles or key_dashed in bundles:
                continue
            findings.append(Finding(
                severity="MEDIUM",
                category="Catalog",
                title=f"Catalog entry missing: `{ref}`",
                detail=(
                    f"Module's Gradle dùng `{ref}` (version catalog reference) "
                    f"nhưng `gradle/libs.versions.toml` của target không có entry tương ứng "
                    f"(`{key_dashed}` hoặc `{key_dotted}`)."
                ),
                suggested_fix=(
                    f"Thêm vào `libs.versions.toml`:\n"
                    f"```toml\n[libraries]\n{key_dashed} = {{ module = \"group:artifact\", version.ref = \"…\" }}\n```\n"
                    f"Chạy `align_gradle.py --apply` để skill tự thêm placeholder (user sửa values sau)."
                ),
            ))
    elif module.catalog_refs and target_toml is None:
        findings.append(Finding(
            severity="MEDIUM",
            category="Catalog",
            title=f"Module dùng version catalog nhưng target không có `libs.versions.toml`",
            detail=(
                f"Module dùng {len(module.catalog_refs)} catalog reference (vd `{module.catalog_refs[0]}`). "
                f"Target không có `gradle/libs.versions.toml` → module sẽ fail Gradle sync."
            ),
            suggested_fix=(
                f"Tạo `gradle/libs.versions.toml` và thêm các entries module cần. "
                f"Xem `firebase-events/gradle/libs.versions.toml` ở source upstream làm mẫu."
            ),
        ))

    # 2. Module direct deps: warn nếu target chưa có cùng dep + version
    target_dep_keys = {_dep_key(d): d for d in target.direct_deps}
    for dep in module.direct_deps:
        key = _dep_key(dep)
        if key not in target_dep_keys:
            continue  # target không xài → không phải mismatch
        target_dep = target_dep_keys[key]
        if _dep_version(dep) != _dep_version(target_dep):
            findings.append(Finding(
                severity="LOW",
                category="Dependency",
                title=f"Version mismatch: `{key}` module={_dep_version(dep)} target={_dep_version(target_dep)}",
                detail=(
                    f"Cả module và target cùng dùng `{key}` nhưng version khác. "
                    f"Gradle sẽ resolve về version cao hơn (default strategy), "
                    f"có thể không match expectation của module."
                ),
                suggested_fix=f"Align về `{_dep_version(dep)}` (version module yêu cầu).",
            ))

    return findings


def _dep_key(dep: str) -> str:
    """`com.google.firebase:firebase-analytics:21.5.0` → `com.google.firebase:firebase-analytics`"""
    if dep.startswith("platform("):
        inner = dep[len("platform("):-1] if dep.endswith(")") else dep[len("platform("):]
        return "platform:" + _dep_key(inner)
    parts = dep.split(":")
    return ":".join(parts[:2]) if len(parts) >= 2 else dep


def _dep_version(dep: str) -> str:
    if dep.startswith("platform("):
        inner = dep[len("platform("):-1] if dep.endswith(")") else dep[len("platform("):]
        return _dep_version(inner)
    parts = dep.split(":")
    return parts[2] if len(parts) >= 3 else "?"


# ============================================================================
# Report rendering
# ============================================================================

def _render_report(
    module: GradleInfo, target: GradleInfo, toml: Optional[Dict[str, Any]],
    findings: List[Finding],
) -> str:
    lines = []
    lines.append("# Gradle alignment report")
    lines.append("")
    lines.append(f"Generated by `align_gradle.py` — compares module Gradle vs target Gradle.\n")
    lines.append("")

    # Summary
    lines.append("## Summary")
    lines.append("")
    n_high = sum(1 for f in findings if f.severity == "HIGH")
    n_med = sum(1 for f in findings if f.severity == "MEDIUM")
    n_low = sum(1 for f in findings if f.severity == "LOW")
    n_info = sum(1 for f in findings if f.severity == "INFO")
    if not findings:
        lines.append("✅ **No mismatches found** — module + target Gradle are compatible.")
    else:
        lines.append(f"- HIGH:   {n_high}")
        lines.append(f"- MEDIUM: {n_med}")
        lines.append(f"- LOW:    {n_low}")
        lines.append(f"- INFO:   {n_info}")
    lines.append("")

    # Side-by-side
    lines.append("## Side-by-side")
    lines.append("")
    lines.append("| | Module (`firebase-events`) | Target (app) |")
    lines.append("|---|---|---|")
    lines.append(f"| compileSdk | {module.compile_sdk or '?'} | {target.compile_sdk or '?'} |")
    lines.append(f"| minSdk     | {module.min_sdk or '?'}     | {target.min_sdk or '?'}     |")
    lines.append(f"| targetSdk  | {module.target_sdk or '?'}  | {target.target_sdk or '?'}  |")
    module_kotlin = _get_plugin_version(module, "org.jetbrains.kotlin") or "?"
    target_kotlin = _get_plugin_version(target, "org.jetbrains.kotlin") or "?"
    lines.append(f"| Kotlin     | {module_kotlin} | {target_kotlin} |")
    module_agp = _get_plugin_version(module, "com.android") or "?"
    target_agp = _get_plugin_version(target, "com.android") or "?"
    lines.append(f"| AGP        | {module_agp} | {target_agp} |")
    lines.append(f"| Direct deps   | {len(module.direct_deps)} | {len(target.direct_deps)} |")
    lines.append(f"| Catalog refs  | {len(module.catalog_refs)} | {len(target.catalog_refs)} |")
    if toml is not None:
        n_libs = len(toml.get("libraries", {}))
        n_versions = len(toml.get("versions", {}))
        lines.append(f"| libs.versions.toml | — | {n_versions} versions, {n_libs} libraries |")
    lines.append("")

    # Findings details
    if findings:
        lines.append("## Findings")
        lines.append("")
        for sev in ("HIGH", "MEDIUM", "LOW", "INFO"):
            sev_items = [f for f in findings if f.severity == sev]
            if not sev_items:
                continue
            lines.append(f"### {sev} — {len(sev_items)}")
            lines.append("")
            for idx, f in enumerate(sev_items, 1):
                lines.append(f"#### {sev}.{idx} [{f.category}] {f.title}")
                lines.append("")
                lines.append(f.detail)
                lines.append("")
                if f.suggested_fix:
                    lines.append(f"**Fix:**")
                    lines.append("")
                    lines.append(f.suggested_fix)
                    lines.append("")

    # Module deps listing for reference
    if module.direct_deps or module.catalog_refs:
        lines.append("## Module dependencies (reference)")
        lines.append("")
        if module.direct_deps:
            lines.append("### Direct coordinates")
            lines.append("")
            for d in module.direct_deps:
                lines.append(f"- `{d}`")
            lines.append("")
        if module.catalog_refs:
            lines.append("### Catalog references")
            lines.append("")
            for r in module.catalog_refs:
                lines.append(f"- `{r}`")
            lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("Files parsed:")
    lines.append("- Module: " + ", ".join(module.files_parsed))
    lines.append("- Target: " + ", ".join(target.files_parsed))

    return "\n".join(lines) + "\n"


# ============================================================================
# Auto-fix: add missing entries to libs.versions.toml
# ============================================================================

def _apply_catalog_additions(
    toml_path: Path, target_toml: Dict[str, Any], module: GradleInfo,
    findings: List[Finding],
) -> int:
    """For each Catalog-missing finding, append a placeholder entry to libs.versions.toml.

    KHÔNG sửa version đã có. CHỈ append vào `[libraries]` section với placeholder
    `module = "group:artifact"`, version.ref = "?". User fill in sau.
    """
    catalog_missing = [f for f in findings
                       if f.category == "Catalog" and "missing:" in f.title]
    if not catalog_missing:
        return 0

    # Find [libraries] section in raw file
    text = toml_path.read_text(encoding="utf-8")
    if "[libraries]" not in text:
        # Create new section at end
        text += "\n\n[libraries]\n"

    libs_existing = target_toml.get("libraries", {}) or {}
    additions = []
    for f in catalog_missing:
        # Title: "Catalog entry missing: `libs.firebase.analytics`"
        m = re.search(r"`libs\.([\w.]+)`", f.title)
        if not m:
            continue
        ref_dotted = m.group(1)
        ref_dashed = ref_dotted.replace(".", "-")
        # Use dashed form (TOML convention)
        if ref_dashed in libs_existing or ref_dotted in libs_existing:
            continue
        line = (
            f'{ref_dashed} = {{ module = "TODO-group:TODO-artifact", '
            f'version = "TODO" }}  # auto-added by align_gradle.py, fill in'
        )
        additions.append(line)

    if not additions:
        return 0

    # Append after [libraries] header
    pattern = re.compile(r'^\[libraries\][^\n]*\n', re.MULTILINE)
    match = pattern.search(text)
    if match:
        insertion = "\n" + "\n".join(additions) + "\n"
        new_text = text[:match.end()] + insertion + text[match.end():]
    else:
        new_text = text + "\n[libraries]\n" + "\n".join(additions) + "\n"

    # Backup + write
    bak = toml_path.with_suffix(toml_path.suffix + ".bak")
    if not bak.exists():
        import shutil
        shutil.copy2(toml_path, bak)
    toml_path.write_text(new_text, encoding="utf-8")
    return len(additions)


# ============================================================================
# String/comment masking (reuse from setup_module)
# ============================================================================

def _mask_strings_and_comments(text: str) -> str:
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
            # Don't mask string contents — we need to extract version strings
            # from "kotlin-android" etc. Skip past the string but keep content.
            i += 1
            while i < n and out[i] != '"':
                if out[i] == "\\" and i + 1 < n:
                    i += 2
                    continue
                i += 1
            if i < n:
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n and out[i] != "'":
                i += 1
            if i < n:
                i += 1
            continue
        i += 1
    return "".join(out)


if __name__ == "__main__":
    sys.exit(main())
