#!/usr/bin/env python3
"""
scaffold_spec.py — Scan an existing Android codebase and propose a first
draft of event-spec.yaml.

Auto-detects:
  - Activities, Fragments (classes ending with those suffixes)
  - Composable screens (functions ending with Screen/Page/Route)
  - Dialogs / BottomSheets / AlertDialog / ModalBottomSheet
  - View click handlers (setOnClickListener) and Compose Button(onClick=...)
  - Project mode (view_binding / compose / hybrid) by % of files with @Composable

Output:
  - <out>            event-spec.yaml draft
  - <review-out>     human review notes (places where the scaffold isn't sure)

Usage:
    python scaffold_spec.py \\
        --src app/src/main/java \\
        --out docs/analytics/event-spec.yaml \\
        --review-out docs/analytics/spec-review.md \\
        [--package com.example.app] \\
        [--name "My App"]
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: PyYAML missing.  pip install pyyaml\n")
    sys.exit(2)


@dataclass
class ScreenCandidate:
    id: str
    cls: str
    kind: str  # activity | fragment | composable
    file: str

@dataclass
class ButtonCandidate:
    screen_id: str
    proposed_id: str
    proposed_name: str
    file: str
    line: int
    raw_hint: str  # what was detected (e.g. "Button(onClick = ...)")
    confidence: str  # "high" | "medium" | "low"

@dataclass
class PopupCandidate:
    id: str
    cls: str
    file: str


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--review-out", required=True)
    ap.add_argument("--package", default=None, help="App package; auto-detect from manifest if omitted")
    ap.add_argument("--name", default="My App")
    ap.add_argument("--app-class", default=None, help="Application class fully-qualified")
    args = ap.parse_args()

    src = Path(args.src)
    if not src.is_dir():
        sys.stderr.write(f"src not found: {src}\n")
        return 2

    kt_files = sorted(src.rglob("*.kt"))
    print(f"→ Scanning {len(kt_files)} Kotlin files...")

    screens: List[ScreenCandidate] = []
    popups: List[PopupCandidate] = []
    buttons: List[ButtonCandidate] = []
    review_notes: List[str] = []

    for kt in kt_files:
        try:
            text = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        rel = str(kt.relative_to(src))
        masked = _mask(text)

        _scan_screens(masked, rel, screens)
        _scan_popups(masked, rel, popups)
        _scan_buttons(masked, rel, screens, buttons, review_notes)

    # Dedupe BEFORE computing mode so we count distinct UI entities, not raw matches.
    screens = _dedupe_screens(screens)
    popups = _dedupe_popups(popups)
    buttons = _dedupe_buttons(buttons)

    # ----- Mode detection (counts UI entities, NOT all .kt files) --------------
    #
    # The old formula `compose_files / total_kt_files` was wrong: a Compose-first
    # project with 30 screens but 200 supporting files (ViewModel, Repository,
    # data class, DI module, extensions) would only show ~13% → falsely classified
    # as view_binding. We now compare UI screens directly.
    #
    # ratio = composable_screens / (composable_screens + activity_screens + fragment_screens)
    #
    # Thresholds (symmetric, inclusive boundaries):
    #   ≥ 80%  → compose      (pure Compose, possibly with 1-2 host Activities)
    #   ≤ 20%  → view_binding (pure XML/ViewBinding)
    #   else   → hybrid
    ui_compose = sum(1 for s in screens if s.kind == "composable")
    ui_view = sum(1 for s in screens if s.kind in ("activity", "fragment"))
    ui_total = ui_compose + ui_view

    if ui_total == 0:
        review_notes.append(
            "No UI screens detected (no Activity/Fragment/@Composable Screen). "
            "Falling back to `view_binding`; set `project.mode` manually if wrong."
        )
        mode = "view_binding"
        ratio = 0.0
    else:
        ratio = ui_compose / ui_total
        if ratio >= 0.8:
            mode = "compose"
        elif ratio <= 0.2:
            mode = "view_binding"
        else:
            mode = "hybrid"

    print(
        f"→ Detected mode = {mode}  "
        f"({ui_compose} composable / {ui_view} activity+fragment, ratio={ratio:.0%})"
    )
    print(f"→ Found {len(screens)} screen(s), {len(popups)} popup(s), {len(buttons)} button candidate(s)")

    pkg = args.package or _detect_package(src)
    if not pkg:
        review_notes.append(
            "Could not auto-detect package — please fill `project.package` manually."
        )
        pkg = "TODO_PACKAGE"

    app_class = args.app_class or _detect_app_class(src, pkg)
    if not app_class:
        review_notes.append(
            "Could not auto-detect Application class — please fill `project.application_class` manually."
        )
        app_class = f"{pkg}.App"

    spec = _build_spec(args.name, pkg, app_class, mode, screens, popups, buttons)

    out_yaml = Path(args.out)
    out_yaml.parent.mkdir(parents=True, exist_ok=True)
    out_yaml.write_text(_dump_spec(spec), encoding="utf-8")

    review = Path(args.review_out)
    review.parent.mkdir(parents=True, exist_ok=True)
    review.write_text(
        _build_review(screens, popups, buttons, review_notes, mode, ratio,
                      ui_compose=ui_compose, ui_view=ui_view),
        encoding="utf-8",
    )

    print(f"\n✓ Wrote draft spec: {out_yaml}")
    print(f"✓ Wrote review notes: {review}")
    print("\nNext steps:")
    print("  1. Open the spec, review screens/popups/buttons; fix any TODO_*")
    print("  2. Read the review notes for low-confidence detections")
    print("  3. Run /analytics-setup (or validate_spec.py + generate_kotlin.py)")
    return 0


# ============================================================================
# Scanners
# ============================================================================

_CLASS_DECL = re.compile(
    r"^\s*(?:open\s+|abstract\s+|sealed\s+)?(?:data\s+)?class\s+(\w+)\s*[:(]",
    re.MULTILINE,
)
_COMPOSABLE_FUN = re.compile(
    r"@Composable[^\n]*\n\s*(?:fun)\s+(\w+)\s*\(",
)


def _scan_screens(text: str, rel: str, out: List[ScreenCandidate]) -> None:
    for m in _CLASS_DECL.finditer(text):
        cls = m.group(1)
        if cls.startswith("Base") or cls in {
            "AppCompatActivity", "Fragment", "DialogFragment",
            "BottomSheetDialogFragment", "Application",
        }:
            continue
        if cls.endswith("Activity"):
            out.append(ScreenCandidate(
                id=_snake(cls.removesuffix("Activity")) or "main",
                cls=cls, kind="activity", file=rel,
            ))
        elif cls.endswith("Fragment"):
            out.append(ScreenCandidate(
                id=_snake(cls.removesuffix("Fragment")) or "main",
                cls=cls, kind="fragment", file=rel,
            ))

    for m in _COMPOSABLE_FUN.finditer(text):
        fn = m.group(1)
        if fn.endswith("Screen") or fn.endswith("Page") or fn.endswith("Route"):
            for suf in ("Screen", "Page", "Route"):
                if fn.endswith(suf):
                    base = fn.removesuffix(suf)
                    break
            out.append(ScreenCandidate(
                id=_snake(base) or _snake(fn),
                cls=fn, kind="composable", file=rel,
            ))

    # NavHost composable("route") { ... } — Navigation Compose routes
    # Add as screen if not already covered by a @Composable fun
    existing_ids = {s.id for s in out}
    for m in _NAV_COMPOSABLE_RE.finditer(text):
        route = m.group(1)
        # Sanitize: take first segment before /, ?
        first_seg = route.split("/", 1)[0].split("?", 1)[0]
        sid = re.sub(r"[^\w]+", "_", first_seg).strip("_").lower()
        if not sid or sid in existing_ids:
            continue
        existing_ids.add(sid)
        out.append(ScreenCandidate(
            id=sid,
            cls=f"NavRoute({route})",
            kind="composable",
            file=rel,
        ))


_DIALOG_DECL = re.compile(
    r"^\s*(?:open\s+|abstract\s+)?class\s+(\w*(?:Dialog|BottomSheet|Sheet|Popup))\b",
    re.MULTILINE,
)
_COMPOSABLE_DIALOG = re.compile(
    r"@Composable[^\n]*\n\s*fun\s+(\w*(?:Dialog|Sheet|Popup|Modal))\s*\(",
)


def _scan_popups(text: str, rel: str, out: List[PopupCandidate]) -> None:
    for m in _DIALOG_DECL.finditer(text):
        cls = m.group(1)
        if cls.startswith("Base"):
            continue
        out.append(PopupCandidate(
            id=_strip_popup_words(_snake(cls)),
            cls=cls, file=rel,
        ))
    for m in _COMPOSABLE_DIALOG.finditer(text):
        cls = m.group(1)
        if cls.startswith("Base"):
            continue
        out.append(PopupCandidate(
            id=_strip_popup_words(_snake(cls)),
            cls=cls, file=rel,
        ))

    # Inline dialog calls: `AlertDialog(...)`, `ModalBottomSheet(...)`, etc.
    # Mỗi call là 1 popup candidate (ID dùng tên hàm + line number).
    seen_inline_ids = {p.id for p in out}
    for m in _DIALOG_CALL_RE.finditer(text):
        dlg_kw = m.group(1)
        line = text[: m.start()].count("\n") + 1
        # Build id from filename stem + dialog kind
        file_stem = Path(rel).stem
        synthetic_id = _strip_popup_words(_snake(f"{file_stem}_{dlg_kw}_l{line}"))
        if synthetic_id in seen_inline_ids:
            continue
        seen_inline_ids.add(synthetic_id)
        out.append(PopupCandidate(
            id=synthetic_id,
            cls=f"Inline{dlg_kw}@{file_stem}:{line}",
            file=rel,
        ))


_BTN_PATTERNS = [
    # findViewById / ViewBinding
    re.compile(r"findViewById<[^>]*>\s*\(\s*R\.id\.(\w+)\s*\)\s*\.setOnClickListener"),
    re.compile(r"\bbinding\.(\w+)\.setOnClickListener"),
    re.compile(r"\b(\w+)\.setOnClickListener\s*\{"),
]

# Material 1 + Material 3 button family — 14 variants
_COMPOSE_BTN_FAMILY = (
    "Button", "IconButton", "TextButton", "OutlinedButton",
    "FilledTonalButton", "ElevatedButton",
    "FloatingActionButton", "ExtendedFloatingActionButton",
    "SmallFloatingActionButton", "LargeFloatingActionButton",
    "FilledIconButton", "OutlinedIconButton", "FilledTonalIconButton",
    "IconToggleButton",
)
_btn_alt = "|".join(re.escape(n) for n in _COMPOSE_BTN_FAMILY)
_COMPOSE_BTN = re.compile(rf"\b({_btn_alt})\s*\(", re.MULTILINE)

# Modifier.clickable { ... } — any Composable becomes a clickable target
_CLICKABLE_RE = re.compile(r"\.clickable\s*(?:\(\s*\)\s*)?\{")

# Inline dialog/sheet calls — Compose-flavored popups
_DIALOG_CALL_RE = re.compile(
    r"\b(AlertDialog|Dialog|ModalBottomSheet|BottomSheetDialog|"
    r"DatePickerDialog|TimePickerDialog)\s*\("
)

# NavHost composable("route") { ... }
_NAV_COMPOSABLE_RE = re.compile(
    r'\bcomposable\s*\(\s*(?:route\s*=\s*)?["\']([^"\']+?)(?:[/?].*?)?["\']'
)

# Identity hints inside button body (priority order — high → low)
# 1. @analytics: comment hint
_ANALYTICS_HINT = re.compile(r"//\s*@analytics\s*:\s*([\w_]+)")
# 2. .testTag("...")
_TEST_TAG = re.compile(r'\.testTag\s*\(\s*["\']([^"\']+)["\']\s*\)')
# 3. contentDescription (for IconButton)
_CONTENT_DESC_LIT = re.compile(r'contentDescription\s*=\s*["\']([^"\']+)["\']')
_CONTENT_DESC_RES = re.compile(r"contentDescription\s*=\s*stringResource\s*\(\s*R\.string\.(\w+)")
# 4. Text("...") label
_TEXT_LIT = re.compile(r'\bText\s*\(\s*(?:text\s*=\s*)?["\']([^"\']+)["\']')
_TEXT_RES = re.compile(r"\bText\s*\(\s*(?:text\s*=\s*)?stringResource\s*\(\s*R\.string\.(\w+)")


def _scan_buttons(
    text: str,
    rel: str,
    screens: List[ScreenCandidate],
    out: List[ButtonCandidate],
    notes: List[str],
) -> None:
    file_screen = _guess_file_screen(rel, text, screens)

    # ──────────── View Binding / findViewById ────────────
    seen_btn_keys: Set[str] = set()
    for pat in _BTN_PATTERNS:
        for m in pat.finditer(text):
            view_name = m.group(1)
            if view_name in {"this", "it", "view", "v"}:
                continue
            line = text[: m.start()].count("\n") + 1
            bid = _normalize_btn_id(view_name)
            name = _pascal(bid.removeprefix("btn_") or view_name)
            key = (file_screen, bid)
            if key in seen_btn_keys:
                continue
            seen_btn_keys.add(key)
            out.append(ButtonCandidate(
                screen_id=file_screen or "TODO_screen",
                proposed_id=bid,
                proposed_name=name,
                file=rel, line=line,
                raw_hint=f"setOnClickListener on '{view_name}'",
                confidence="high" if file_screen else "low",
            ))

    # ──────────── Compose Button family ────────────
    # Identity extraction scans BOTH arglist AND trailing lambda
    for m in _COMPOSE_BTN.finditer(text):
        btn_kind = m.group(1)
        btn_start = m.start()
        paren_open = m.end() - 1  # position of '('
        paren_close = _find_matching_paren(text, paren_open)
        if paren_close < 0:
            continue
        arglist = text[paren_open + 1: paren_close]
        line = text[: btn_start].count("\n") + 1

        # Scan trailing lambda `{...}` ngay sau closing paren
        lambda_body = ""
        j = paren_close + 1
        while j < len(text) and text[j] in " \t":
            j += 1
        if j < len(text) and text[j] == "{":
            brace_close = _find_matching_brace(text, j)
            if brace_close > j:
                lambda_body = text[j + 1: brace_close]

        full_scope = arglist + "\n" + lambda_body
        # Comment hint above button call
        prelude = _get_prelude(text, btn_start, lines=3)

        ident, confidence = _extract_compose_identity(prelude, full_scope, lambda_body or arglist)

        # FALLBACK: dù không extract được identity, vẫn add candidate với
        # btn_compose_l<line> — user duyệt và đổi tên sau. KHÔNG skip hẳn.
        if not ident:
            ident = f"compose_l{line}"
            confidence = "low"
            notes.append(
                f"`{rel}:{line}` — `{btn_kind}(...)` chưa rõ identity. "
                f"Đề xuất id `btn_compose_l{line}`. Đặt `Modifier.testTag(\"btn_xxx\")` "
                f"hoặc `// @analytics: btn_xxx` để skill tự đặt tên chuẩn."
            )

        bid = _normalize_btn_id(ident)
        name = _pascal(bid.removeprefix("btn_") or ident)
        key = (file_screen, bid)
        if key in seen_btn_keys:
            continue
        seen_btn_keys.add(key)
        out.append(ButtonCandidate(
            screen_id=file_screen or "TODO_screen",
            proposed_id=bid,
            proposed_name=name,
            file=rel, line=line,
            raw_hint=f"{btn_kind}(...) — via {confidence}",
            confidence="medium" if file_screen and confidence != "fallback" else
                       "low",
        ))

    # ──────────── Modifier.clickable {} ────────────
    # Bất kỳ Composable nào (Box/Row/Card/Surface/Image/...) có `.clickable`
    # → 1 click target. Cần track riêng để không miss khi designer dùng Row
    # thay vì Button.
    for clk_pos, widget_paren_pos, widget_name, _ in _find_clickable_enclosing_calls(text):
        # Tránh duplicate khi widget chính là Button family (đã catch ở trên)
        if widget_name in _COMPOSE_BTN_FAMILY:
            continue
        line = text[: clk_pos].count("\n") + 1
        # Extract identity từ widget's body
        widget_close = _find_matching_paren(text, widget_paren_pos)
        if widget_close < 0:
            continue
        widget_body = text[widget_paren_pos + 1: widget_close]
        # Trailing lambda của widget
        lambda_body = ""
        j = widget_close + 1
        while j < len(text) and text[j] in " \t":
            j += 1
        if j < len(text) and text[j] == "{":
            brace_close = _find_matching_brace(text, j)
            if brace_close > j:
                lambda_body = text[j + 1: brace_close]

        prelude = _get_prelude(text, widget_paren_pos, lines=3)
        ident, confidence = _extract_compose_identity(
            prelude, widget_body + "\n" + lambda_body, lambda_body or widget_body,
        )

        if not ident:
            ident = f"clickable_l{line}"
            confidence = "low"
            notes.append(
                f"`{rel}:{line}` — `{widget_name}` với `Modifier.clickable` "
                f"chưa rõ identity. Đề xuất id `btn_clickable_l{line}`."
            )

        bid = _normalize_btn_id(ident)
        # Prefix differentiation: nếu fallback id, dùng btn_clickable_l<line>
        if confidence == "low" and bid.startswith("btn_compose_"):
            bid = f"btn_clickable_l{line}"
        name = _pascal(bid.removeprefix("btn_") or ident)
        key = (file_screen, bid)
        if key in seen_btn_keys:
            continue
        seen_btn_keys.add(key)
        out.append(ButtonCandidate(
            screen_id=file_screen or "TODO_screen",
            proposed_id=bid,
            proposed_name=name,
            file=rel, line=line,
            raw_hint=f"{widget_name}.clickable — via {confidence}",
            confidence="medium" if file_screen and confidence != "fallback" else "low",
        ))


def _extract_compose_identity(
    prelude: str, full_scope: str, label_scope: str,
) -> Tuple[Optional[str], str]:
    """Extract button identity from surrounding code.

    Priority chain (high → low confidence):
      1. // @analytics: btn_xxx comment (in prelude before call)
      2. Modifier.testTag("xxx")
      3. contentDescription = "xxx" hoặc stringResource(R.string.xxx)
      4. Text("xxx") trong trailing lambda
      5. Text(stringResource(R.string.xxx))

    Returns (identity, confidence_reason) hoặc (None, "fallback") nếu không tìm thấy.
    """
    m = _ANALYTICS_HINT.search(prelude)
    if m:
        return m.group(1), "@analytics hint"

    m = _TEST_TAG.search(full_scope)
    if m:
        return m.group(1), "testTag"

    m = _CONTENT_DESC_LIT.search(full_scope)
    if m:
        return m.group(1), "contentDescription"
    m = _CONTENT_DESC_RES.search(full_scope)
    if m:
        return m.group(1), "contentDescription stringResource"

    m = _TEXT_LIT.search(label_scope)
    if m:
        return m.group(1), "Text label"
    m = _TEXT_RES.search(label_scope)
    if m:
        return m.group(1), "Text stringResource"

    return None, "fallback"


def _get_prelude(text: str, pos: int, lines: int = 3) -> str:
    """Get last N lines before pos (for @analytics comment scan)."""
    line_start = text.rfind("\n", 0, pos) + 1
    cursor = line_start - 1
    for _ in range(lines):
        prev = text.rfind("\n", 0, cursor)
        if prev < 0:
            cursor = 0
            break
        cursor = prev
    return text[cursor:line_start]


def _find_clickable_enclosing_calls(text: str) -> List[Tuple[int, int, str, int]]:
    """Stack-based scan: tìm mỗi `.clickable` và Composable bao quanh nó.

    Cho code như:
        Box(modifier = Modifier.clickable { onClick() }) { Text("X") }
        Row(Modifier.clickable(onClick = onItem)) { ... }
        Card(Modifier.clickable { go() }) { ... }

    Trả về list of (clickable_pos, widget_open_paren_pos, widget_name, widget_name_start).
    """
    results: List[Tuple[int, int, str, int]] = []
    stack: List[Tuple[int, str, int]] = []
    n = len(text)
    i = 0
    in_str = in_triple = in_char = in_line_c = in_block_c = False

    while i < n:
        c = text[i]
        # Comment/string state machine
        if in_line_c:
            if c == "\n":
                in_line_c = False
            i += 1
            continue
        if in_block_c:
            if c == "*" and i + 1 < n and text[i + 1] == "/":
                in_block_c = False
                i += 2
                continue
            i += 1
            continue
        if in_triple:
            if c == '"' and text[i:i + 3] == '"""':
                in_triple = False
                i += 3
                continue
            i += 1
            continue
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_char:
            if c == "\\":
                i += 2
                continue
            if c == "'":
                in_char = False
            i += 1
            continue
        if c == "/" and i + 1 < n:
            if text[i + 1] == "/":
                in_line_c = True
                i += 2
                continue
            if text[i + 1] == "*":
                in_block_c = True
                i += 2
                continue
        if c == '"':
            if text[i:i + 3] == '"""':
                in_triple = True
                i += 3
                continue
            in_str = True
            i += 1
            continue
        if c == "'":
            # Char literal vs Kotlin label (return@label) — simple heuristic
            if i + 2 < n and text[i + 2] == "'":
                in_char = True
            i += 1
            continue

        # Paren tracking
        if c == "(":
            # Walk back over whitespace + identifier to capture widget name
            j = i - 1
            while j >= 0 and text[j] in " \t":
                j -= 1
            name_end = j + 1
            while j >= 0 and (text[j].isalnum() or text[j] == "_"):
                j -= 1
            name_start = j + 1
            name = text[name_start:name_end]
            stack.append((i, name, name_start))
        elif c == ")":
            if stack:
                stack.pop()
        elif c == "." and text[i:i + 10] == ".clickable":
            after = text[i + 10] if i + 10 < n else ""
            # Boundary check — exclude `.clickableArea`, etc.
            if not (after.isalnum() or after == "_"):
                if stack:
                    paren_pos, name, name_start = stack[-1]
                    if name:
                        results.append((i, paren_pos, name, name_start))
        i += 1

    return results


def _find_matching_brace(text: str, open_idx: int) -> int:
    """Find matching '}' for '{' at open_idx. String/comment-aware."""
    if open_idx >= len(text) or text[open_idx] != "{":
        return -1
    depth = 0
    i = open_idx
    in_str = in_triple = in_char = in_line_c = in_block_c = False
    while i < len(text):
        c = text[i]
        if in_line_c:
            if c == "\n":
                in_line_c = False
        elif in_block_c:
            if c == "*" and i + 1 < len(text) and text[i + 1] == "/":
                in_block_c = False
                i += 1
        elif in_triple:
            if c == '"' and text[i:i + 3] == '"""':
                in_triple = False
                i += 2
        elif in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif in_char:
            if c == "\\":
                i += 1
            elif c == "'":
                in_char = False
        else:
            if c == "/" and i + 1 < len(text):
                if text[i + 1] == "/":
                    in_line_c = True
                    i += 1
                elif text[i + 1] == "*":
                    in_block_c = True
                    i += 1
            elif c == '"':
                if text[i:i + 3] == '"""':
                    in_triple = True
                    i += 2
                else:
                    in_str = True
            elif c == "'":
                in_char = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return -1


# ============================================================================
# Helpers
# ============================================================================

def _guess_file_screen(
    rel: str, text: str, screens: List[ScreenCandidate]
) -> Optional[str]:
    base = Path(rel).stem
    for s in screens:
        if s.cls == base:
            return s.id
        if s.file == rel:
            return s.id
    return None


def _normalize_btn_id(raw: str) -> str:
    s = _snake(raw)
    s = re.sub(r"^button_?", "btn_", s)
    s = re.sub(r"^view_?", "btn_", s)
    if not s.startswith(("btn_", "radio_")):
        s = "btn_" + s
    s = re.sub(r"_+", "_", s)
    return s[:40]


def _dedupe_screens(arr: List[ScreenCandidate]) -> List[ScreenCandidate]:
    seen: Dict[str, ScreenCandidate] = {}
    for s in arr:
        if s.id not in seen:
            seen[s.id] = s
    return list(seen.values())


def _dedupe_popups(arr: List[PopupCandidate]) -> List[PopupCandidate]:
    seen: Dict[str, PopupCandidate] = {}
    for p in arr:
        if p.id and p.id not in seen:
            seen[p.id] = p
    return list(seen.values())


def _dedupe_buttons(arr: List[ButtonCandidate]) -> List[ButtonCandidate]:
    seen: Dict[tuple, ButtonCandidate] = {}
    for b in arr:
        key = (b.screen_id, b.proposed_id)
        if key not in seen:
            seen[key] = b
    return list(seen.values())


_PKG_DECL = re.compile(r"^\s*package\s+([a-zA-Z][\w.]*)", re.MULTILINE)


def _detect_package(src: Path) -> Optional[str]:
    # Use the most common package from immediate Application files; fallback to any .kt
    counts: Dict[str, int] = defaultdict(int)
    for kt in src.rglob("*.kt"):
        try:
            txt = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        m = _PKG_DECL.search(txt)
        if m:
            pkg = m.group(1)
            # Take top-level (e.g. com.example.app from com.example.app.ui.home)
            parts = pkg.split(".")
            if len(parts) >= 3:
                counts[".".join(parts[:3])] += 1
            counts[pkg] += 1
    if not counts:
        return None
    return max(counts.items(), key=lambda kv: kv[1])[0]


def _detect_app_class(src: Path, pkg: str) -> Optional[str]:
    for kt in src.rglob("*.kt"):
        try:
            txt = kt.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        m = re.search(r"class\s+(\w+)\s*:\s*\w*Application\b", txt)
        if m:
            file_pkg_m = _PKG_DECL.search(txt)
            file_pkg = file_pkg_m.group(1) if file_pkg_m else pkg
            return f"{file_pkg}.{m.group(1)}"
    return None


def _build_spec(
    name: str, pkg: str, app_class: str, mode: str,
    screens: List[ScreenCandidate],
    popups: List[PopupCandidate],
    buttons: List[ButtonCandidate],
) -> Dict:
    return {
        "project": {
            "name": name,
            "package": pkg,
            "application_class": app_class,
            "version": "1.0.0",
            "mode": mode,
            "features": {
                "iap": False,
                "ads": False,
                "rate_dialog": any("rate" in p.id.lower() for p in popups),
                "onboarding": False,
                "appsflyer": False,
                "permissions": [],
            },
        },
        "screens": [
            {"id": s.id, "class": s.cls, "kind": s.kind} for s in screens
        ],
        "popups": [
            {"id": p.id, "class": p.cls} for p in popups if p.id
        ],
        "buttons": [
            {
                "screen": b.screen_id,
                "id": b.proposed_id,
                "name": b.proposed_name,
            } for b in buttons if b.screen_id != "TODO_screen"
        ],
        "custom_events": [],
        "user_properties": [],
    }


def _dump_spec(spec: Dict) -> str:
    header = (
        "# Generated by /analytics-scaffold — REVIEW BEFORE COMMIT\n"
        "# Read docs/analytics/spec-review.md for low-confidence detections.\n\n"
    )
    return header + yaml.safe_dump(spec, sort_keys=False, allow_unicode=True)


def _build_review(
    screens, popups, buttons, notes, mode, ratio,
    ui_compose: int = 0, ui_view: int = 0,
) -> str:
    lines = []
    lines.append("# Spec review — scaffold results")
    lines.append("")
    ui_total = ui_compose + ui_view
    if ui_total == 0:
        lines.append(f"- Project mode detected: **{mode}** (no UI screens found — fallback)")
    else:
        lines.append(
            f"- Project mode detected: **{mode}** "
            f"({ui_compose} composable / {ui_view} activity+fragment "
            f"= {ratio:.0%} compose)"
        )
        lines.append("  - Formula: `composable_screens / (composable + activity + fragment)`")
        lines.append("  - Thresholds: ≥80% → compose · ≤20% → view_binding · else → hybrid")
        lines.append("  - Override by editing `project.mode` in spec if detection is wrong.")
    lines.append(f"- Screens found: {len(screens)}")
    lines.append(f"- Popups found: {len(popups)}")
    lines.append(f"- Button candidates: {len(buttons)} "
                 f"(high {sum(1 for b in buttons if b.confidence == 'high')}, "
                 f"medium {sum(1 for b in buttons if b.confidence == 'medium')}, "
                 f"low {sum(1 for b in buttons if b.confidence == 'low')})")
    lines.append("")
    low = [b for b in buttons if b.confidence == "low"]
    if low:
        lines.append("## ⚠ Low-confidence button candidates")
        lines.append("")
        lines.append("Could not infer which screen these belong to. Fix `screen:` field in spec or add `// @analytics:` comment.")
        lines.append("")
        for b in low:
            lines.append(f"- `{b.file}:{b.line}` — {b.raw_hint} (proposed id=`{b.proposed_id}`, screen=`{b.screen_id}`)")
        lines.append("")
    if notes:
        lines.append("## Other notes")
        lines.append("")
        for n in notes:
            lines.append(f"- {n}")
    lines.append("")
    lines.append("## Next steps")
    lines.append("1. Edit `docs/analytics/event-spec.yaml`, fix any `TODO_*` values.")
    lines.append("2. Run validate_spec.py to check naming convention.")
    lines.append("3. Run generate_kotlin.py to emit Kotlin files.")
    return "\n".join(lines)


# --- Generic helpers ---

def _snake(s: str) -> str:
    if not s:
        return ""
    # Insert underscore between camelCase boundaries
    res = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s)
    res = re.sub(r"([a-z\d])([A-Z])", r"\1_\2", res)
    # Replace any non-alphanumeric run (spaces, dashes, dots, commas...) with _
    res = re.sub(r"[^\w]+", "_", res)
    # Collapse multi-underscore + lowercase + trim
    res = re.sub(r"_+", "_", res).lower().strip("_")
    return res


def _pascal(s: str) -> str:
    s = _snake(s)
    return "".join(p[:1].upper() + p[1:] for p in s.split("_") if p)


def _strip_popup_words(s: str) -> str:
    s = re.sub(r"_?(dialog|popup|bottom_sheet|sheet)$", "", s)
    return s


def _mask(text: str) -> str:
    """Mask out // line comments to reduce false positives, BUT preserve
    `// @analytics: btn_xxx` hints — those are intentional ID assignments."""
    def repl(m):
        comment = m.group(0)
        if "@analytics" in comment:
            return comment  # keep intact
        return ""
    return re.sub(r"//.*?$", repl, text, flags=re.MULTILINE)


def _find_matching_paren(text: str, open_idx: int) -> int:
    if open_idx >= len(text) or text[open_idx] != "(":
        return -1
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


if __name__ == "__main__":
    sys.exit(main())
