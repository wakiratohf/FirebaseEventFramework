#!/usr/bin/env python3
"""
audit_project.py — Scan Kotlin sources to find missing analytics tracking.

Usage:
    python audit_project.py \\
        --spec docs/analytics/event-spec.yaml \\
        --src app/src/main \\
        --out docs/analytics/audit-report.md \\
        [--fixes-out docs/analytics/.audit-fixes.json] \\
        [--fail-on=high]

Detection patterns (severity in parens):
    P1  Activity not extending BaseTrackedActivity (HIGH)
    P2  Fragment without logScreenStart/Stop in onResume/onPause (HIGH)
    P3  setOnClickListener { ... } without logClickBtn nearby (MEDIUM) — view_binding
    P4  Dialog.show() / BottomSheet.show() without popup tracking (MEDIUM)
    P5  custom_event in spec but no logEvent(XxxEv(...)) call in code (MEDIUM)
    P6  File with AUTO-GENERATED header has different spec-hash (HIGH = drift)
    P7  Activity/Fragment class found in code but NOT in spec (LOW)
    P11 Compose Button(onClick = { ... }) without logClickBtn in handler (MEDIUM)
    P12 Compose Modifier.clickable { ... } without logClickBtn (MEDIUM)
    P13 Hoisted button — Button(onClick = onSave) parent must track (LOW info)
    P14 Back nav — onClick = onBack / popBackStack without tracking (MEDIUM)

Audit picks active patterns based on `project.mode` in spec:
    view_binding : P1-P7
    compose      : P1, P5-P7, P11-P14
    hybrid       : ALL

Exit codes:
    0     OK or only LOW-severity issues
    1     ≥ MEDIUM if --fail-on=medium
    1     ≥ HIGH if --fail-on=high (default action: still 0 if you don't pass --fail-on)
    2     IO / spec error
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import hashlib
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: PyYAML missing.  pip install pyyaml\n")
    sys.exit(2)


# ============================================================================
# Data
# ============================================================================

SEVERITY_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}


@dataclass
class Issue:
    pattern: str
    severity: str
    file: str
    line: int
    message: str
    suggestion: str = ""
    auto_fix: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class AuditCtx:
    spec: Dict[str, Any]
    mode: str
    screens_by_class: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    popups_by_class: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    screen_ids: Set[str] = field(default_factory=set)
    popup_ids: Set[str] = field(default_factory=set)
    button_ids_by_screen: Dict[str, Set[str]] = field(default_factory=dict)
    custom_event_names: Set[str] = field(default_factory=set)
    custom_event_triggers: Dict[str, Dict[str, str]] = field(default_factory=dict)
    ignore_screens: Set[str] = field(default_factory=set)
    ignored_classes: Set[str] = field(default_factory=set)
    spec_hash: str = ""


# ============================================================================
# Main
# ============================================================================

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True)
    ap.add_argument("--src", required=True, help="e.g. app/src/main")
    ap.add_argument("--out", required=True, help="Audit report markdown output")
    ap.add_argument("--fixes-out", default=None, help="JSON of auto-fixable issues")
    ap.add_argument("--fail-on", choices=["low", "medium", "high"], default=None)
    args = ap.parse_args()

    spec_path = Path(args.spec)
    src_path = Path(args.src)
    if not spec_path.is_file() or not src_path.is_dir():
        sys.stderr.write("ERROR: spec or src path invalid\n")
        return 2

    spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
    ctx = _build_ctx(spec)

    issues: List[Issue] = []
    kt_files = sorted(src_path.rglob("*.kt"))
    print(f"→ Scanning {len(kt_files)} Kotlin files...")

    for kt in kt_files:
        rel = str(kt.relative_to(src_path))
        try:
            text = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue

        if _is_in_ignored_screen(text, ctx):
            # still check drift (P6) but skip click-related
            _scan_drift(text, rel, ctx, issues)
            continue

        if ctx.mode in ("view_binding", "hybrid"):
            _scan_p1(text, rel, ctx, issues)
            _scan_p2(text, rel, ctx, issues)
            _scan_p3(text, rel, ctx, issues)
        _scan_p4(text, rel, ctx, issues)
        _scan_p7(text, rel, ctx, issues)
        _scan_p8(text, rel, ctx, issues)
        _scan_p9(text, rel, ctx, issues)
        _scan_p10(text, rel, ctx, issues)
        _scan_p15(text, rel, ctx, issues)
        _scan_drift(text, rel, ctx, issues)

        if ctx.mode in ("compose", "hybrid"):
            _scan_p11(text, rel, ctx, issues)
            _scan_p12(text, rel, ctx, issues)
            _scan_p13(text, rel, ctx, issues)
            _scan_p14(text, rel, ctx, issues)
            _scan_p16(text, rel, ctx, issues)
            _scan_p18(text, rel, ctx, issues)

    # P5 + P17 needs whole-codebase view (Application class có thể là 1 file riêng)
    _scan_p5(kt_files, src_path, ctx, issues)
    # P17: scan tất cả file, tìm Application class
    for kt in kt_files:
        try:
            text = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        rel = str(kt.relative_to(src_path))
        _scan_p17(text, rel, ctx, issues)

    _write_report(issues, ctx, args.out)
    if args.fixes_out:
        _write_fixes(issues, args.fixes_out)

    _print_summary(issues)

    if args.fail_on:
        threshold = SEVERITY_ORDER[args.fail_on.upper()]
        if any(SEVERITY_ORDER[i.severity] >= threshold for i in issues):
            return 1
    return 0


# ============================================================================
# Context
# ============================================================================

def _build_ctx(spec: Dict[str, Any]) -> AuditCtx:
    project = spec.get("project") or {}
    mode = project.get("mode", "view_binding")

    screens_by_class = {}
    screen_ids = set()
    for s in spec.get("screens") or []:
        screen_ids.add(s["id"])
        screens_by_class[s["class"]] = s

    popups_by_class = {}
    popup_ids = set()
    for p in spec.get("popups") or []:
        popup_ids.add(p["id"])
        if p.get("class"):
            popups_by_class[p["class"]] = p

    buttons_by_screen: Dict[str, Set[str]] = {}
    for b in spec.get("buttons") or []:
        s = b.get("screen")
        if s:
            buttons_by_screen.setdefault(s, set()).add(b["id"])

    custom_event_names = set()
    custom_event_triggers: Dict[str, Dict[str, str]] = {}
    for ev in spec.get("custom_events") or []:
        custom_event_names.add(ev["name"])
        hint = ev.get("trigger_hint") or {}
        if hint.get("file"):
            custom_event_triggers[ev["name"]] = {
                "file": hint["file"],
                "method": hint.get("method", ""),
            }

    ignore_screens = set(project.get("ignore_screens") or [])
    ignored_classes = {
        s["class"] for s in (spec.get("screens") or [])
        if s["id"] in ignore_screens
    }

    spec_hash = hashlib.sha256(
        yaml.safe_dump(spec, default_flow_style=False, sort_keys=True).encode()
    ).hexdigest()

    return AuditCtx(
        spec=spec, mode=mode,
        screens_by_class=screens_by_class,
        popups_by_class=popups_by_class,
        screen_ids=screen_ids,
        popup_ids=popup_ids,
        button_ids_by_screen=buttons_by_screen,
        custom_event_names=custom_event_names,
        custom_event_triggers=custom_event_triggers,
        ignore_screens=ignore_screens,
        ignored_classes=ignored_classes,
        spec_hash=spec_hash,
    )


# ============================================================================
# Pattern scanners
# ============================================================================

_CLASS_DECL = re.compile(
    r"^\s*(?:open\s+|abstract\s+)?class\s+(\w+)\s*[:(]"
    , re.MULTILINE
)
_BASE_TRACKED_ACTIVITY = re.compile(r":\s*BaseTrackedActivity\b")
_BASE_TRACKED_FRAGMENT = re.compile(r":\s*BaseTrackedFragment\b")
_LOG_SCREEN_START = re.compile(r"\blogScreen(Start|Open)\b")
_LOG_SCREEN_STOP = re.compile(r"\blogScreen(Stop|Closed)\b")
_LOG_CLICK_BTN = re.compile(r"\blogClickBtn\b")
_LOG_EVENT = re.compile(r"\blogEvent\b")
_GEN_MARKER = re.compile(r"^// AUTO-GENERATED by firebase-events-impl skill", re.MULTILINE)
_GEN_HASH = re.compile(r"^// Spec-hash:\s*([0-9a-f]+)", re.MULTILINE)


def _is_in_ignored_screen(text: str, ctx: AuditCtx) -> bool:
    """Return True if any top-level class declared in this file belongs to
    ignore_screens."""
    if not ctx.ignored_classes:
        return False
    for m in _CLASS_DECL.finditer(text):
        if m.group(1) in ctx.ignored_classes:
            return True
    return False


# --- P1: Activity must extend BaseTrackedActivity --------------------------

def _scan_p1(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    for m in _CLASS_DECL.finditer(text):
        cls = m.group(1)
        if not cls.endswith("Activity"):
            continue
        # Skip the BaseTrackedActivity definition itself, common base classes
        if cls in {"BaseTrackedActivity", "BaseActivity", "AppCompatActivity"}:
            continue
        line = text[: m.start()].count("\n") + 1
        decl_block = text[m.start(): m.start() + 600]
        if _BASE_TRACKED_ACTIVITY.search(decl_block):
            continue
        in_spec = cls in ctx.screens_by_class
        sid = ctx.screens_by_class.get(cls, {}).get("id", "")
        if not in_spec:
            # P7 handles this case
            continue
        issues.append(Issue(
            pattern="P1",
            severity="HIGH",
            file=rel,
            line=line,
            message=f"Activity `{cls}` does not extend BaseTrackedActivity",
            suggestion=(
                f"Change `class {cls} : AppCompatActivity()` to "
                f"`class {cls} : BaseTrackedActivity()` and override "
                f"`getScreenName() = ScreenName.{sid.upper()}`."
            ),
            auto_fix={
                "kind": "activity_extends_base",
                "class": cls,
                "screen_const": sid.upper(),
            },
        ))


# --- P2: Fragment must have screen-start/stop --------------------------

def _scan_p2(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    for m in _CLASS_DECL.finditer(text):
        cls = m.group(1)
        if not cls.endswith("Fragment"):
            continue
        if cls in {"BaseTrackedFragment", "BaseFragment", "Fragment"}:
            continue
        if cls not in ctx.screens_by_class:
            continue
        line = text[: m.start()].count("\n") + 1
        decl_block = text[m.start(): m.start() + 600]
        if _BASE_TRACKED_FRAGMENT.search(decl_block):
            continue
        # body scan: must reference logScreenStart and logScreenStop
        body = _extract_class_body(text, m.end())
        has_start = bool(_LOG_SCREEN_START.search(body))
        has_stop = bool(_LOG_SCREEN_STOP.search(body))
        if has_start and has_stop:
            continue
        sid = ctx.screens_by_class[cls]["id"]
        miss = []
        if not has_start:
            miss.append("logScreenStart")
        if not has_stop:
            miss.append("logScreenStop")
        issues.append(Issue(
            pattern="P2",
            severity="HIGH",
            file=rel,
            line=line,
            message=f"Fragment `{cls}` missing {' & '.join(miss)}",
            suggestion=(
                f"Either extend `BaseTrackedFragment` and override `getScreenName() "
                f"= ScreenName.{sid.upper()}`, or call "
                f"`AnalyticsEventsUtils.logScreenStart(ScreenName.{sid.upper()})` "
                f"in onResume() and `logScreenStop(...)` in onPause()."
            ),
        ))


# --- P3: setOnClickListener without logClickBtn nearby (XML/ViewBinding) ---

_SET_ON_CLICK = re.compile(
    r"\.setOnClickListener\s*(?:\(\s*)?\{",
)


def _scan_p3(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    for m in _SET_ON_CLICK.finditer(masked):
        line = masked[: m.start()].count("\n") + 1
        block = _extract_lambda_body(masked, m.end() - 1)
        if block is None:
            continue
        if _LOG_CLICK_BTN.search(block):
            continue
        # no-op handler like `{ }` or `{ Unit }` — skip
        stripped = block.strip()
        if stripped in ("", "Unit"):
            continue
        issues.append(Issue(
            pattern="P3",
            severity="MEDIUM",
            file=rel,
            line=line,
            message="setOnClickListener handler missing logClickBtn",
            suggestion=(
                "Add `AnalyticsEventsUtils.logClickBtn(<Screen>BtnEv.<ENTRY>)` (vd `HomeBtnEv.HOME_BACK`) "
                "as the first statement of the click handler."
            ),
        ))


# --- P4: Dialog.show() / BottomSheet.show() without popup tracking ---

_SHOW_CALL = re.compile(
    r"\b(\w+(?:Dialog|BottomSheet|Sheet))\s*(?:\([^)]*\))?\s*\.show\s*\(",
)


def _scan_p4(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    for m in _SHOW_CALL.finditer(masked):
        cls = m.group(1)
        line = masked[: m.start()].count("\n") + 1
        # If this file references PopupName / logScreenStart with popupName arg, skip
        ctx_window = masked[max(0, m.start() - 400): m.end() + 400]
        if "popupName" in ctx_window or "PopupName." in ctx_window:
            continue
        # If using TrackedDialog/TrackedDialogBuilder/BaseTrackedDialog → already wired
        if any(marker in ctx_window for marker in
               ("TrackedDialog(", "TrackedDialogBuilder(", "BaseTrackedDialog",
                ".attachTo(")):
            continue
        # If dialog là Compose (AlertDialog từ androidx.compose.material3) trong @Composable scope
        # → có thể dùng TrackedPopup wrap, skip nếu thấy TrackedPopup gần đó
        if "TrackedPopup(" in ctx_window:
            continue
        issues.append(Issue(
            pattern="P4",
            severity="MEDIUM",
            file=rel,
            line=line,
            message=f"`{cls}.show()` không có popup tracking — dialog không fire screen_view_ev",
            suggestion=(
                f"Có 3 cách:\n"
                f"  (1) Dùng `TrackedDialogBuilder(ctx, ScreenName.X, PopupName.Y)` "
                f"thay cho MaterialAlertDialogBuilder — tự wire onShow/onDismiss.\n"
                f"  (2) Hoặc cho dialog extend `BaseTrackedDialog(ctx, screen, popup)`.\n"
                f"  (3) Hoặc wire tay: `TrackedDialog(screen, popup).attachTo(dialog)` "
                f"trước khi `.show()`. Compose dialog thì dùng `TrackedPopup(PopupName.X) {{ ... }}`."
            ),
        ))


# --- P16: Compose dialog Composable không wrap trong TrackedPopup ---

_COMPOSE_DIALOG_CALL = re.compile(
    r"\b(AlertDialog|Dialog|ModalBottomSheet|BottomSheetDialog|"
    r"DatePickerDialog|TimePickerDialog)\s*\("
)


def _scan_p18(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Compose: dialog Composable call (AlertDialog/ModalBottomSheet/...) không
    nằm trong TrackedPopup wrap → dialog không fire screen_view_ev.

    Khác P16 (catch screen_name rỗng do thiếu LocalScreenName provider) — P18
    catch popup MISS fire screen_view_ev event entirely.
    """
    if not _file_uses_compose(text):
        return
    masked = _mask_comments_and_strings(text)
    for m in _COMPOSE_DIALOG_CALL.finditer(masked):
        kw = m.group(1)
        line = masked[: m.start()].count("\n") + 1
        # Check enclosing context: nếu trong TrackedPopup { ... } → OK
        ctx_window = masked[max(0, m.start() - 600): m.start()]
        if "TrackedPopup(" in ctx_window:
            continue
        # Nếu file là chính TrackedComposables.kt (định nghĩa TrackedPopup) thì skip
        if rel.endswith("TrackedComposables.kt"):
            continue
        issues.append(Issue(
            pattern="P18",
            severity="MEDIUM",
            file=rel,
            line=line,
            message=f"Compose `{kw}(...)` không bọc trong `TrackedPopup` — dialog không fire screen_view_ev",
            suggestion=(
                f"Bọc dialog content trong `TrackedPopup(PopupName.X) {{ "
                f"{kw}(...) {{ ... }} }}`. TrackedPopup tự fire screen_open lúc enter "
                f"và screen_view_ev STOP với duration lúc dispose."
            ),
        ))


# --- P16: Dialog render ngoài TrackedScreen scope → screen_name rỗng (HIGH) ---
# Catch case dialog global declared as sibling của NavHost — overlay mọi screen
# nhưng KHÔNG có TrackedScreen ancestor → LocalScreenName.current = "" →
# click_btn_ev có screen_name rỗng (vd `_Ok` thay vì `HomeOk`).

_P16_PROVIDER_RE = re.compile(
    r"LocalScreenName\s+provides\b|\bTrackedScreen\s*\("
)


def _scan_p16(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Detect dialog render như sibling của NavHost (ngoài mọi TrackedScreen scope).

    `LocalScreenName` là staticCompositionLocalOf { "" }; chỉ TrackedScreen cấp giá trị.
    Dialog khai báo ở nav-graph level (ngoài NavHost block) không có ancestor nào provide
    → `LocalScreenName.current` rỗng → click_btn_ev có screen_name rỗng (vd `_Ok`).

    Idempotent: sau khi wrap `CompositionLocalProvider(LocalScreenName provides ...)` hoặc
    đặt trong `TrackedScreen` thì không còn báo.
    """
    if "AUTO-GENERATED" in text[:500]:
        return
    masked = _mask_comments_and_strings(text)
    if not _file_uses_compose(masked):
        return
    nav_m = re.search(r"\bNavHost\s*\(", masked)
    if not nav_m:
        return

    # Xác định span của block NavHost(...) { ... } — dialog bên trong (qua screen
    # composable) đã có TrackedScreen, chỉ dialog NGOÀI span mới là vấn đề.
    paren_close = _find_matching_paren(masked, nav_m.end() - 1)
    if paren_close < 0:
        return
    brace_open = masked.find("{", paren_close)
    nav_end = _find_matching_brace(masked, brace_open) if brace_open >= 0 else paren_close
    nav_start = nav_m.start()

    # Re-use _SHARED_DIALOG_RE defined later in file (P15 helper)
    for m in _SHARED_DIALOG_RE.finditer(masked):
        comp = m.group(1)
        if comp in {"AlertDialog", "Dialog", "ModalBottomSheet", "BottomSheetDialog",
                    "DatePickerDialog", "TimePickerDialog"}:
            continue
        # Trong NavHost block → ok (screen wrap TrackedScreen).
        if nav_start <= m.start() <= nav_end:
            continue
        # Đã wrap provider/TrackedScreen ngay trước call → ok.
        window = masked[max(0, m.start() - 300): m.start()]
        if _P16_PROVIDER_RE.search(window):
            continue
        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            severity="HIGH",
            pattern="P16",
            file=rel, line=line,
            message=(
                f"Dialog `{comp}` render ngoài NavHost/TrackedScreen scope → "
                f"`LocalScreenName.current` rỗng → click_btn_ev có screen_name rỗng"
            ),
            suggestion=(
                "Wrap dialog trong `CompositionLocalProvider(LocalScreenName provides "
                "<screen>)` — suy <screen> từ route hiện tại "
                "(navController.currentBackStackEntryAsState()) — hoặc đặt trong "
                "`TrackedScreen`. Dialog global ngoài NavHost không kế thừa screen name."
            ),
        ))


def _find_matching_brace(text: str, open_idx: int) -> int:
    """Trả index của `}` khớp với `{` tại open_idx, hoặc cuối text nếu không cân."""
    if open_idx < 0 or open_idx >= len(text) or text[open_idx] != "{":
        return len(text) - 1
    depth = 0
    i = open_idx
    while i < len(text):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text) - 1


# --- P17: Application class chưa init analytics bootstrap ---

_APPLICATION_DECL = re.compile(
    r"^class\s+(\w+)\s*[:]?\s*(?:.*\b)?Application\b",
    re.MULTILINE,
)


def _scan_p17(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Application class không gọi AnalyticsBootstrap.init / AnalyticsModule.init
    → events sẽ silent (kill-switch ở init time, hoặc app_exit không fire vì
    timestamp chưa save)."""
    m = _APPLICATION_DECL.search(text)
    if not m:
        return
    cls = m.group(1)
    # Bootstrapper init? hoặc trực tiếp AnalyticsModule.init?
    has_bootstrap = "AnalyticsBootstrap.init" in text
    has_module_init = "AnalyticsModule.init" in text
    has_session_save = "saveAppOpenedTimestamp" in text or "FirebasePrefs" in text
    has_app_events_install = "AppEventsInstaller.install" in text

    if not (has_bootstrap or has_module_init):
        line = text[: m.start()].count("\n") + 1
        issues.append(Issue(
            pattern="P17",
            severity="HIGH",
            file=rel,
            line=line,
            message=f"Application class `{cls}` chưa init analytics SDK trong `onCreate()`",
            suggestion=(
                "Thêm vào `override fun onCreate()`:\n"
                f"  `AnalyticsBootstrap.init(app = this, isTestMode = BuildConfig.DEBUG)`\n"
                "Hoặc nếu cần custom, gọi tay 3 API:\n"
                "  `AnalyticsModule.init(...)`\n"
                "  `FirebasePrefs.saveAppOpenedTimestamp(this, System.currentTimeMillis())`\n"
                "  `AppEventsInstaller.install(this)`"
            ),
        ))
        return

    # Nếu init bằng cách tay → check thiếu mảnh nào
    if has_module_init and not has_bootstrap:
        line = text[: m.start()].count("\n") + 1
        if not has_session_save:
            issues.append(Issue(
                pattern="P17",
                severity="MEDIUM",
                file=rel,
                line=line,
                message=f"`{cls}` init `AnalyticsModule` nhưng chưa gọi `saveAppOpenedTimestamp`",
                suggestion=(
                    "Thiếu `FirebasePrefs.saveAppOpenedTimestamp(this, System.currentTimeMillis())` "
                    "→ `app_exit` event sẽ silent vì timestamp = 0. Thêm vào sau `AnalyticsModule.init(...)`."
                ),
            ))
        if not has_app_events_install:
            issues.append(Issue(
                pattern="P17",
                severity="MEDIUM",
                file=rel,
                line=line,
                message=f"`{cls}` chưa wire `app-events` lifecycle observer",
                suggestion=(
                    "Thiếu `AppEventsInstaller.install(this)` → `time_open_app_ev` và `app_exit` "
                    "sẽ không fire. Thêm vào `onCreate()` (sau khi save timestamp). "
                    "Bỏ qua nếu app dùng custom lifecycle handling."
                ),
            ))


# --- P5: custom_event in spec but no logEvent(XxxEv(...)) anywhere ---

def _scan_p5(kt_files: List[Path], src_path: Path, ctx: AuditCtx, issues: List[Issue]) -> None:
    if not ctx.custom_event_names:
        return
    found: Set[str] = set()
    pascal_to_name: Dict[str, str] = {}
    for ev_name in ctx.custom_event_names:
        # snake_case → PascalCase, e.g. vpn_connect_ev → VpnConnectEv
        pascal = "".join(p[:1].upper() + p[1:] for p in ev_name.split("_"))
        pascal_to_name[pascal] = ev_name

    for kt in kt_files:
        try:
            text = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        text = _mask_comments_and_strings(text)
        for pascal, ev_name in pascal_to_name.items():
            if ev_name in found:
                continue
            if re.search(rf"\b{re.escape(pascal)}\s*\(", text):
                found.add(ev_name)

    for ev_name in ctx.custom_event_names - found:
        hint = ctx.custom_event_triggers.get(ev_name, {})
        loc_file = hint.get("file", "<unknown>")
        loc_method = hint.get("method", "")
        suggestion = (
            f"Construct `{_pascal(ev_name)}(...)` and call "
            f"`AnalyticsEventsUtils.logProjectEvent(...)`"
        )
        if loc_file != "<unknown>":
            suggestion += f" — expected in `{loc_file}`"
            if loc_method:
                suggestion += f"::{loc_method}"
        issues.append(Issue(
            pattern="P5",
            severity="MEDIUM",
            file=loc_file,
            line=0,
            message=f"custom_event `{ev_name}` declared in spec but never logged",
            suggestion=suggestion,
        ))


# --- P6: Generated file modified (drift) ---

def _scan_drift(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    if not _GEN_MARKER.search(text):
        return
    m = _GEN_HASH.search(text)
    if not m:
        # Generated file but no hash → very old, force regen
        issues.append(Issue(
            pattern="P6",
            severity="HIGH",
            file=rel,
            line=1,
            message="Generated file has no spec-hash header (legacy or tampered)",
            suggestion="Run `/analytics-generate` to refresh.",
        ))
        return
    file_hash = m.group(1)
    if file_hash != ctx.spec_hash:
        issues.append(Issue(
            pattern="P6",
            severity="HIGH",
            file=rel,
            line=1,
            message=f"Generated file stale (hash {file_hash[:8]} ≠ spec {ctx.spec_hash[:8]})",
            suggestion="Run `/analytics-generate` to regenerate.",
        ))


# --- P7: Activity/Fragment present in code but missing from spec ---

def _scan_p7(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    SKIP_CLASSES = {
        "BaseTrackedActivity", "BaseTrackedFragment",
        "BaseActivity", "BaseFragment",
        "AppCompatActivity",
    }
    for m in _CLASS_DECL.finditer(text):
        cls = m.group(1)
        if cls in SKIP_CLASSES:
            continue
        is_activity = cls.endswith("Activity")
        is_fragment = cls.endswith("Fragment")
        if not (is_activity or is_fragment):
            continue
        if cls in ctx.screens_by_class:
            continue
        line = text[: m.start()].count("\n") + 1
        kind = "Activity" if is_activity else "Fragment"
        issues.append(Issue(
            pattern="P7",
            severity="LOW",
            file=rel,
            line=line,
            message=f"{kind} `{cls}` not declared in `screens` spec",
            suggestion=(
                f"Add to event-spec.yaml:\n"
                f"    screens:\n"
                f"      - id: {_snake(cls)}\n"
                f"        class: {cls}"
            ),
        ))


# --- P11: Compose Button(onClick = { ... }) without logClickBtn ---

_COMPOSE_BUTTONS = (
    "Button", "OutlinedButton", "TextButton", "ElevatedButton",
    "FilledTonalButton", "FloatingActionButton", "ExtendedFloatingActionButton",
    "IconButton", "IconToggleButton", "FilledIconButton", "OutlinedIconButton",
)


# ============================================================================
# P8 — Hard-code event name string (bypass constants)
# ============================================================================

# Match string literal cho các event chuẩn được sinh ra từ module/spec
_HARDCODE_EVENT_NAMES = (
    "screen_view_ev", "click_btn_ev", "iap_ev", "show_rate_dialog_ev",
    "load_ad_ev", "show_ad_ev", "click_ad_ev", "paid_ad_impression",
    "app_exit", "onboarding_step_ev", "time_open_app_ev", "open_app_from_ev",
)
_HARDCODE_EVENT_RE = re.compile(
    rf'"({"|".join(re.escape(n) for n in _HARDCODE_EVENT_NAMES)})"'
)


def _scan_p8(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Detect hard-code event name literal — should use Events constants instead."""
    # Skip generated event files
    if rel.endswith("Ev.kt") or rel.endswith("AnalyticsEventsUtils.kt"):
        return
    if "AUTO-GENERATED" in text[:500]:
        return
    masked_strings_only_for_event_check = text  # keep strings — we want to find them
    masked_comments = re.sub(r"//[^\n]*", "", text, flags=re.MULTILINE)
    for m in _HARDCODE_EVENT_RE.finditer(masked_comments):
        event_name = m.group(1)
        line = masked_comments[: m.start()].count("\n") + 1
        issues.append(Issue(
            severity="MEDIUM",
            pattern="P8",
            file=rel, line=line,
            message=f"Hard-code event name literal `\"{event_name}\"` thay vì dùng constant",
            suggestion=(
                f"Dùng constant trong module (vd `AnalyticsEvents.SCREEN_VIEW_EV`) "
                f"hoặc gọi qua `AnalyticsEventsUtils.logXxx(...)`. Literal có thể typo "
                f"không bị compiler bắt."
            ),
        ))


# ============================================================================
# P9 — Direct AnalyticsEvents.logXxx call (bypass AnalyticsEventsUtils)
# ============================================================================

_BYPASS_ANALYTICS_RE = re.compile(r"\bAnalyticsEvents\.log[A-Z]\w+Ev\s*\(")


def _scan_p9(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Detect call `AnalyticsEvents.logXxxEv(...)` ở UI — should go through wrapper."""
    # AnalyticsEventsUtils chính là wrapper, được phép gọi direct
    if rel.endswith("AnalyticsEventsUtils.kt"):
        return
    # Application class được phép — bootstrap context
    if re.search(r"^class\s+\w+\s*:\s*(?:android\.app\.)?Application\b",
                 text, re.MULTILINE):
        return
    masked = _mask_comments_and_strings(text)
    for m in _BYPASS_ANALYTICS_RE.finditer(masked):
        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            severity="LOW",
            pattern="P9",
            file=rel, line=line,
            message="Gọi `AnalyticsEvents.logXxx` trực tiếp, bypass `AnalyticsEventsUtils` wrapper",
            suggestion=(
                "Thêm wrapper method tương ứng vào `AnalyticsEventsUtils` rồi gọi qua đó. "
                "Wrapper là single point để toggle / suppress events theo BuildConfig."
            ),
        ))


# ============================================================================
# P10 — Direct AnalyticsUserProperties.xxx call from UI
# ============================================================================

_DIRECT_USER_PROP_RE = re.compile(r"\bAnalyticsUserProperties\.\w+\s*\(")


def _scan_p10(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Detect direct `AnalyticsUserProperties.xxx(...)` from UI — should be wrapped."""
    if rel.endswith("AnalyticsEventsUtils.kt"):
        return
    # Application — bootstrap setUserId/setLanguage là chuẩn
    if re.search(r"^class\s+\w+\s*:\s*(?:android\.app\.)?Application\b",
                 text, re.MULTILINE):
        return
    masked = _mask_comments_and_strings(text)
    for m in _DIRECT_USER_PROP_RE.finditer(masked):
        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            severity="LOW",
            pattern="P10",
            file=rel, line=line,
            message="Gọi `AnalyticsUserProperties.xxx` trực tiếp từ UI",
            suggestion=(
                "Wrap vào helper trong `AnalyticsEventsUtils` (vd `setUserTier(...)`). "
                "User property thay đổi cần được kiểm soát ở 1 chỗ."
            ),
        ))


# ============================================================================
# P15 — Shared dialog component có button params chưa được track
# ============================================================================

# Match call dialog component dùng chung của project — định danh qua tham số có
# tên `positiveText` / `negativeText` / `neutralText` (convention).
_SHARED_DIALOG_RE = re.compile(
    r"\b([A-Z]\w*(?:Dialog|Alert|Confirm|Sheet))\s*\("
)
# Sau khi tìm dialog call, check body có 3 thứ:
_DIALOG_HAS_BUTTON_TEXT = re.compile(
    r"\b(positiveText|negativeText|neutralText|positiveLabel|negativeLabel)\s*="
)
_DIALOG_HAS_LOG_CALL = re.compile(
    r"\blog(?:Click)?Btn\s*\(|AnalyticsEventsUtils\.log"
)


def _scan_p15(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    """Detect shared dialog component có positiveText/negativeText param nhưng
    handler onPositive/onNegative không có logClickBtn call."""
    if rel.endswith("AnalyticsEventsUtils.kt"):
        return
    if "AUTO-GENERATED" in text[:500]:
        return
    masked = _mask_comments_and_strings(text)

    for m in _SHARED_DIALOG_RE.finditer(masked):
        comp = m.group(1)
        # Skip well-known platform / Compose dialogs (P4 covers those)
        if comp in {"AlertDialog", "Dialog", "ModalBottomSheet", "BottomSheetDialog",
                    "DatePickerDialog", "TimePickerDialog"}:
            continue
        paren_open = m.end() - 1
        paren_close = _find_matching_paren(masked, paren_open)
        if paren_close < 0:
            continue
        body = masked[paren_open + 1: paren_close]

        # Có button text param?
        if not _DIALOG_HAS_BUTTON_TEXT.search(body):
            continue

        # Có log call trong body?
        if _DIALOG_HAS_LOG_CALL.search(body):
            continue

        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            severity="MEDIUM",
            pattern="P15",
            file=rel, line=line,
            message=(
                f"Dialog dùng chung `{comp}` có positiveText/negativeText nhưng "
                f"call site không có `logClickBtn` — nút dialog không được track"
            ),
            suggestion=(
                "Trong onPositive/onNegative/onNeutral handler, gọi "
                "`AnalyticsEventsUtils.logClickBtn(ScreenBtnEv.XXX)`. Dialog là điểm "
                "TERMINAL — không có caller sâu hơn track hộ, phải tự log."
            ),
        ))


# ============================================================================
# P11 — Compose Button missing logClickBtn
# ============================================================================

def _scan_p11(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    if not _file_uses_compose(masked):
        return
    for btn in _COMPOSE_BUTTONS:
        for m in re.finditer(rf"\b{btn}\s*\(", masked):
            paren_end = _find_matching_paren(masked, m.end() - 1)
            if paren_end < 0:
                continue
            arglist = masked[m.end(): paren_end]
            handler = _extract_onclick(arglist)
            if handler is None:
                continue
            if handler.startswith("{"):  # inline lambda
                body = _strip_lambda_braces(handler)
                if body.strip() in ("", "Unit"):
                    continue
                if _LOG_CLICK_BTN.search(body):
                    continue
                line = masked[: m.start()].count("\n") + 1
                issues.append(Issue(
                    pattern="P11",
                    severity="MEDIUM",
                    file=rel,
                    line=line,
                    message=f"Compose `{btn}` onClick missing logClickBtn",
                    suggestion=(
                        f"Wrap with TrackedButton(event=<Screen>BtnEv.<ENTRY>) or add "
                        f"`AnalyticsEventsUtils.logClickBtn(...)` as first stmt of the lambda."
                    ),
                ))


# --- P12: Modifier.clickable { ... } without logClickBtn ---

def _scan_p12(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    if not _file_uses_compose(masked):
        return
    for m in re.finditer(r"\.clickable\s*(?:\([^)]*\))?\s*\{", masked):
        body = _extract_lambda_body(masked, m.end() - 1)
        if body is None:
            continue
        if body.strip() in ("", "Unit"):
            continue
        if _LOG_CLICK_BTN.search(body):
            continue
        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            pattern="P12",
            severity="MEDIUM",
            file=rel,
            line=line,
            message="Modifier.clickable handler missing logClickBtn",
            suggestion=(
                "Add `AnalyticsEventsUtils.logClickBtn(<Screen>BtnEv.<ENTRY>)` (vd `HomeBtnEv.HOME_X`) "
                "or use a TrackedButton wrapper."
            ),
        ))


# --- P13: Hoisted button onClick = onSave (informational) ---

def _scan_p13(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    if not _file_uses_compose(masked):
        return
    for btn in _COMPOSE_BUTTONS:
        for m in re.finditer(rf"\b{btn}\s*\(", masked):
            paren_end = _find_matching_paren(masked, m.end() - 1)
            if paren_end < 0:
                continue
            arglist = masked[m.end(): paren_end]
            handler = _extract_onclick(arglist)
            if handler is None or handler.startswith("{"):
                continue
            # function-reference style: onClick = onSave, onClick = vm::save
            if re.match(r"^[a-zA-Z_]\w*(?:::[a-zA-Z_]\w*)?$", handler):
                line = masked[: m.start()].count("\n") + 1
                issues.append(Issue(
                    pattern="P13",
                    severity="LOW",
                    file=rel,
                    line=line,
                    message=f"Compose `{btn}` hoisted onClick `{handler}` — track in parent or wrap in TrackedButton",
                    suggestion=(
                        "Convert to inline lambda + logClickBtn, OR ensure the calling parent "
                        "passes a tracked callback."
                    ),
                ))


# --- P14: Back navigation without track ---

_BACK_HANDLERS = (
    "onBack", "onBackPressed", "onNavigateBack", "onNavigateUp", "onBackClick",
)


def _scan_p14(text: str, rel: str, ctx: AuditCtx, issues: List[Issue]) -> None:
    masked = _mask_comments_and_strings(text)
    if not _file_uses_compose(masked):
        return
    # Match value-form onClick assignments only (per v1.13 conventions)
    for m in re.finditer(r"onClick\s*=\s*([A-Za-z_][\w]*)", masked):
        callee = m.group(1)
        if callee not in _BACK_HANDLERS and not callee.endswith("popBackStack"):
            continue
        # If file's wrapper does the tracking (TrackedIconButton …), allow it
        ctx_window = masked[max(0, m.start() - 200): m.end() + 200]
        if "TrackedIconButton" in ctx_window or "TrackedButton" in ctx_window:
            continue
        line = masked[: m.start()].count("\n") + 1
        issues.append(Issue(
            pattern="P14",
            severity="MEDIUM",
            file=rel,
            line=line,
            message=f"Back navigation `{callee}` without tracking",
            suggestion=(
                "Wrap: `onClick = { AnalyticsEventsUtils.logClickBtn(ScreenName.X, "
                "<Screen>BtnEv.<SCREEN>_BACK); " + callee + "() }`"
            ),
        ))


# ============================================================================
# Source-parsing helpers
# ============================================================================

def _file_uses_compose(text: str) -> bool:
    return "@Composable" in text or "import androidx.compose." in text


def _mask_comments_and_strings(text: str) -> str:
    """Replace comments and string-literals with spaces of the same length,
    preserving line numbers + character offsets.

    Order matters: strings first (so // inside a string doesn't trigger),
    then // line comments, then /* */ block comments.
    """
    out = list(text)
    n = len(text)
    i = 0
    while i < n:
        c = text[i]
        # Triple-quoted string
        if text[i:i+3] == '"""':
            j = i + 3
            while j < n - 2 and text[j:j+3] != '"""':
                j += 1
            j = min(n, j + 3)
            for k in range(i + 1, j - 1):
                if text[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "\n":
                    break
                j += 1
            j = min(n, j + 1)
            for k in range(i + 1, j - 1):
                if text[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if text[i:i+2] == "//":
            j = i
            while j < n and text[j] != "\n":
                out[j] = " "
                j += 1
            i = j
            continue
        if text[i:i+2] == "/*":
            j = i + 2
            while j < n - 1 and text[j:j+2] != "*/":
                if text[j] != "\n":
                    out[j] = " "
                j += 1
            j = min(n, j + 2)
            for k in range(i, j):
                if text[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def _find_matching_paren(text: str, open_idx: int) -> int:
    if text[open_idx] != "(":
        return -1
    depth = 0
    i = open_idx
    while i < len(text):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def _extract_lambda_body(text: str, open_brace_idx: int) -> Optional[str]:
    if text[open_brace_idx] != "{":
        return None
    depth = 0
    i = open_brace_idx
    while i < len(text):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace_idx + 1: i]
        i += 1
    return None


def _strip_lambda_braces(s: str) -> str:
    s = s.strip()
    if s.startswith("{"):
        s = s[1:]
    if s.endswith("}"):
        s = s[:-1]
    return s


def _extract_onclick(arglist: str) -> Optional[str]:
    """Find `onClick = <expr>` value in a function arg list. Returns expr
    (without leading 'onClick = ') or None if not found."""
    m = re.search(r"\bonClick\s*=\s*", arglist)
    if not m:
        return None
    rest = arglist[m.end():]
    # Lambda?
    rest_s = rest.lstrip()
    if rest_s.startswith("{"):
        # Track braces to find the end
        offset = len(rest) - len(rest_s)
        depth = 0
        for i in range(offset, len(rest)):
            if rest[i] == "{":
                depth += 1
            elif rest[i] == "}":
                depth -= 1
                if depth == 0:
                    return rest[offset: i + 1]
        return rest[offset:]
    # value form — until next top-level comma or end
    depth = 0
    end = len(rest)
    for i, ch in enumerate(rest):
        if ch in "({[":
            depth += 1
        elif ch in ")}]":
            depth -= 1
        elif ch == "," and depth == 0:
            end = i
            break
    return rest[:end].strip()


def _extract_class_body(text: str, after_decl_idx: int) -> str:
    # Find first '{' after the declaration line ends, then balance.
    i = after_decl_idx
    while i < len(text) and text[i] != "{":
        i += 1
    if i >= len(text):
        return ""
    depth = 0
    start = i
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start: i + 1]
        i += 1
    return text[start:]


def _snake(camel: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", camel).lower()


def _pascal(snake: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in snake.split("_") if p)


# ============================================================================
# Report writers
# ============================================================================

def _write_report(issues: List[Issue], ctx: AuditCtx, out_path: str) -> None:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)

    counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0}
    for i in issues:
        counts[i.severity] += 1

    lines = []
    lines.append("# Analytics audit report\n")
    lines.append(f"- Project mode: `{ctx.mode}`")
    lines.append(f"- Spec hash: `{ctx.spec_hash[:12]}`")
    lines.append(f"- HIGH: {counts['HIGH']} · MEDIUM: {counts['MEDIUM']} · LOW: {counts['LOW']}\n")
    if not issues:
        lines.append("✓ No issues detected.")
    else:
        for sev in ("HIGH", "MEDIUM", "LOW"):
            sev_issues = [i for i in issues if i.severity == sev]
            if not sev_issues:
                continue
            lines.append(f"## {sev} — {len(sev_issues)}\n")
            for it in sev_issues:
                loc = f"{it.file}:{it.line}" if it.line else it.file
                lines.append(f"### [{it.pattern}] `{loc}`")
                lines.append(f"{it.message}")
                if it.suggestion:
                    lines.append(f"\n**Fix:** {it.suggestion}\n")
                lines.append("")
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"→ Wrote {out}")


def _write_fixes(issues: List[Issue], out_path: str) -> None:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    auto = [i.to_dict() for i in issues if i.auto_fix]
    out.write_text(json.dumps(auto, indent=2), encoding="utf-8")
    print(f"→ Wrote {out} ({len(auto)} auto-fixable)")


def _print_summary(issues: List[Issue]) -> None:
    counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0}
    for i in issues:
        counts[i.severity] += 1
    total = sum(counts.values())
    print(f"\n→ Done. {total} issue(s): "
          f"HIGH {counts['HIGH']}, MEDIUM {counts['MEDIUM']}, LOW {counts['LOW']}")


if __name__ == "__main__":
    sys.exit(main())
