#!/usr/bin/env python3
"""
verify_events.py — Generate a QC test checklist from event-spec.yaml.

Usage:
    # Default — write checklist
    python verify_events.py \\
        --spec docs/analytics/event-spec.yaml \\
        --out docs/analytics/test-checklist.md

    # Watch Logcat and auto-tick items as events fire:
    adb logcat -s AnalyticsEvents:D | python verify_events.py \\
        --mode=watch \\
        --checklist docs/analytics/test-checklist.md \\
        --timeout 600

Exit codes:
    0 — Success
    1 — Watch timed out with missing items
    2 — IO error
"""

from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: PyYAML missing.  pip install pyyaml\n")
    sys.exit(2)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["generate", "watch"], default="generate")
    ap.add_argument("--spec", help="event-spec.yaml (required for generate)")
    ap.add_argument("--out", help="Checklist output path (generate)")
    ap.add_argument("--checklist", help="Existing checklist path (watch)")
    ap.add_argument("--timeout", type=int, default=600, help="Watch timeout in seconds")
    args = ap.parse_args()

    if args.mode == "generate":
        if not args.spec or not args.out:
            sys.stderr.write("--spec and --out required for generate mode\n")
            return 2
        return _generate(args.spec, args.out)
    else:
        if not args.checklist:
            sys.stderr.write("--checklist required for watch mode\n")
            return 2
        return _watch(args.checklist, args.timeout, args.spec)


# ----------------------------------------------------------------------------
# Generate mode
# ----------------------------------------------------------------------------

def _generate(spec_path: str, out_path: str) -> int:
    spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8"))
    project = spec.get("project") or {}
    screens = spec.get("screens") or []
    buttons = spec.get("buttons") or []
    popups = {p["id"]: p for p in (spec.get("popups") or [])}
    customs = spec.get("custom_events") or []

    lines: List[str] = []
    lines.append(f"# QC test checklist — {project.get('name', 'project')}\n")
    lines.append("> Tick `[x]` as events fire correctly in Firebase DebugView or Logcat.")
    lines.append("> Enable DebugView: `adb shell setprop debug.firebase.analytics.app <appId>`\n")

    # Group buttons by screen
    by_screen: Dict[str, List[Dict[str, Any]]] = {}
    for b in buttons:
        by_screen.setdefault(b["screen"], []).append(b)

    for s in screens:
        sid = s["id"]
        cls = s["class"]
        camel = _pascal(sid)
        lines.append(f"## Màn hình `{cls}` (id=`{sid}`)\n")
        lines.append(f"- [ ] Mở màn → log `screen_view_ev` với `screen_name=\"{camel}\"`, `screen_state=stop`, duration > 0")
        lines.append(f"- [ ] Set user-property `screen_open=\"{camel}\"` khi vào màn")

        for b in by_screen.get(sid, []):
            bid = b["id"]
            name = b["name"]
            popup_id = b.get("popup")
            popup_suffix = ""
            if popup_id and popup_id in popups:
                popup_suffix = f" (trong popup `{popup_id}`)"
            btype = b.get("type")
            variants = []
            if btype == "toggle":
                variants = [(f"{name}On", "ON"), (f"{name}Off", "OFF")]
            elif btype == "checkbox":
                variants = [(f"{name}Checked", "CHECKED"), (f"{name}Unchecked", "UNCHECKED")]
            elif btype == "accordion":
                variants = [(f"{name}Expanded", "EXPANDED"), (f"{name}Collapsed", "COLLAPSED")]
            elif btype == "radio":
                for opt in b.get("options") or []:
                    variants.append((f"{name}{opt['label']}", f"RADIO({opt['value']})"))
            else:
                variants = [(name, None)]

            for vname, state in variants:
                tag = f" — state `{state}`" if state else ""
                lines.append(
                    f"- [ ] Bấm `{vname}`{popup_suffix}{tag} → log `click_btn_ev` với "
                    f"`click_btn_ev_name=\"{camel}{vname}\"`"
                )

            for trg in b.get("triggers") or []:
                ev = trg["event"]
                params = trg.get("params") or {}
                params_str = ", ".join(f"{k}=\"{v}\"" for k, v in params.items())
                lines.append(f"  - [ ] Side-effect: log `{ev}` với {params_str}")

        lines.append("")

    # Custom events
    if customs:
        lines.append("## Custom events\n")
        for ev in customs:
            name = ev["name"]
            desc = ev.get("description", "")
            hint = ev.get("trigger_hint") or {}
            file_hint = hint.get("file", "")
            method_hint = hint.get("method", "")
            ctx_str = ""
            if file_hint:
                ctx_str = f" (trigger trong `{file_hint}`"
                if method_hint:
                    ctx_str += f"::{method_hint}"
                ctx_str += ")"
            lines.append(f"- [ ] `{name}` — {desc}{ctx_str}")
            for p in ev.get("params") or []:
                pn = p["name"]
                pt = p["type"]
                enum = p.get("enum")
                enum_str = f" ∈ {enum}" if enum else ""
                lines.append(f"  - param `{pn}` ({pt}){enum_str} có giá trị đúng")
        lines.append("")

    if spec.get("user_properties"):
        lines.append("## User properties\n")
        for p in spec["user_properties"]:
            lines.append(f"- [ ] `{p['name']}` set đúng giá trị")
        lines.append("")

    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"→ Wrote checklist: {out}")
    return 0


# ----------------------------------------------------------------------------
# Watch mode — tail logcat, auto-tick
# ----------------------------------------------------------------------------

# Logcat lines we care about (from EventNameValidator/TestMode dump):
#   ===== FirebaseEvents =====
#   eventName: click_btn_ev
#   ---Params---
#   Key: click_btn_ev_name | Value: HomeConnectButton
# Multiple logcat formats supported:
#   1. `eventName: screen_view_ev` (module's AnalyticsEvents format)
#   2. `Event: screen_view_ev`     (simpler format)
#   3. `[Analytics] screen_view_ev` (custom logger)
EVENT_NAME_RE = re.compile(
    r"(?:eventName|Event|\[Analytics\])\s*[:]\s+([a-z][\w]*)"
)
# Params: `Key: foo | Value: bar` or `param foo = "bar"` or `foo=bar`
PARAM_RE = re.compile(
    r"(?:Key|param)?\s*[:]?\s*(\w+)\s*(?:\||=)\s*(?:Value\s*[:]\s*)?\"?([^\"\n|]+)\"?"
)


def _watch(checklist_path: str, timeout: int, spec_path: Optional[str] = None) -> int:
    cp = Path(checklist_path)
    if not cp.is_file():
        sys.stderr.write(f"ERROR: checklist not found: {cp}\n")
        return 2
    lines = cp.read_text(encoding="utf-8").splitlines()

    # Optional: load spec for live validation
    valid_events = _STANDARD_EVENTS.copy()
    valid_screens: Set[str] = set()
    valid_buttons: Set[str] = set()
    if spec_path:
        try:
            import yaml
            spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8")) or {}
            valid_screens = {_pascal(s["id"]) for s in (spec.get("screens") or [])}
            valid_buttons = {b.get("name", "") for b in (spec.get("buttons") or [])}
            for ev in spec.get("custom_events") or []:
                valid_events.add(ev["name"])
        except Exception as e:
            print(f"⚠ Could not load spec for validation: {e}", file=sys.stderr)

    pending_indices = [
        i for i, line in enumerate(lines)
        if re.match(r"^\s*-\s+\[\s+\]", line)
    ]
    if not pending_indices:
        print("Nothing to verify — all ticked or no items.")
        return 0

    use_color = sys.stdout.isatty()
    C = _Colors(use_color)

    print(f"{C.cyan}→ Watching Logcat — {len(pending_indices)} items pending. "
          f"Timeout {timeout}s.{C.reset}")
    print(f"{C.dim}  (Pipe adb output:  adb logcat -s AnalyticsEvents:V "
          f"AnalyticsUserProperties:V | python verify_events.py --mode watch ...){C.reset}")
    if spec_path:
        print(f"{C.dim}  Spec validation ON — unknown events highlighted{C.reset}")
    print()

    start = time.time()
    last_event = ""
    last_params: Dict[str, str] = {}

    # Stats
    event_counts: Dict[str, int] = {}
    unknown_events: Set[str] = set()
    ticked_count = 0

    try:
        for raw in sys.stdin:
            if time.time() - start > timeout:
                break
            line = raw.rstrip()
            m = EVENT_NAME_RE.search(line)
            if m:
                # New event begins; flush previous
                ticked = _try_tick(lines, pending_indices, last_event, last_params)
                if ticked:
                    ticked_count += 1
                    _print_event_line(last_event, last_params, C, status="✓ ticked",
                                       valid_events=valid_events, unknown_events=unknown_events)
                elif last_event:
                    _print_event_line(last_event, last_params, C, status="",
                                       valid_events=valid_events, unknown_events=unknown_events)
                last_event = m.group(1)
                event_counts[last_event] = event_counts.get(last_event, 0) + 1
                last_params = {}
                continue
            mp = PARAM_RE.search(line)
            if mp:
                last_params[mp.group(1)] = mp.group(2).strip()
                continue
        # flush last
        if last_event:
            ticked = _try_tick(lines, pending_indices, last_event, last_params)
            if ticked:
                ticked_count += 1
                _print_event_line(last_event, last_params, C, status="✓ ticked",
                                   valid_events=valid_events, unknown_events=unknown_events)
            else:
                _print_event_line(last_event, last_params, C, status="",
                                   valid_events=valid_events, unknown_events=unknown_events)
    except KeyboardInterrupt:
        pass

    cp.write_text("\n".join(lines), encoding="utf-8")
    remaining = [
        lines[i] for i in pending_indices
        if not re.match(r"^\s*-\s+\[x\]", lines[i])
    ]

    # ── Stats panel ──
    print()
    print(f"{C.cyan}─── Watch session summary ───{C.reset}")
    print(f"  Items ticked: {C.green}{ticked_count}{C.reset} / {len(pending_indices)}")
    total_events = sum(event_counts.values())
    print(f"  Events seen:  {total_events} ({len(event_counts)} distinct)")
    if event_counts:
        print(f"  Top events:")
        for ev, n in sorted(event_counts.items(), key=lambda kv: -kv[1])[:5]:
            tag = "" if ev in valid_events else f" {C.yellow}(unknown){C.reset}"
            print(f"    {n:>4}× {ev}{tag}")
    if unknown_events:
        print(f"  {C.yellow}Unknown events (not in spec):{C.reset}")
        for ev in sorted(unknown_events):
            print(f"    - {ev}")

    print(f"\n→ Updated {cp}")
    if remaining:
        print(f"\n{C.yellow}Missing ({len(remaining)}):{C.reset}")
        for r in remaining:
            print(f"  {r.strip()}")
        return 1
    print(f"\n{C.green}✓ All items verified.{C.reset}")
    return 0


# Standard events from module — known events that don't need to be in spec.custom_events
_STANDARD_EVENTS = {
    "screen_view_ev", "click_btn_ev", "iap_ev", "show_rate_dialog_ev",
    "load_ad_ev", "show_ad_ev", "click_ad_ev", "paid_ad_impression",
    "app_exit", "onboarding_step_ev", "time_open_app_ev", "open_app_from_ev",
}


class _Colors:
    """ANSI color helpers — disable when not TTY (e.g. piped to file)."""
    def __init__(self, enabled: bool):
        if enabled:
            self.reset = "\033[0m"
            self.dim = "\033[2m"
            self.bold = "\033[1m"
            self.cyan = "\033[36m"
            self.green = "\033[32m"
            self.yellow = "\033[33m"
            self.red = "\033[31m"
            self.magenta = "\033[35m"
        else:
            self.reset = self.dim = self.bold = ""
            self.cyan = self.green = self.yellow = self.red = self.magenta = ""


def _event_category(event: str) -> str:
    """Categorize for color: screen/click/ad/iap/lifecycle/other."""
    if event.startswith("screen_view"):
        return "screen"
    if event.startswith("click_btn") or event.startswith("click_ad"):
        return "click"
    if "ad" in event:
        return "ad"
    if event.startswith("iap"):
        return "iap"
    if event in ("app_exit", "time_open_app_ev", "open_app_from_ev",
                 "show_rate_dialog_ev", "onboarding_step_ev"):
        return "lifecycle"
    return "other"


def _print_event_line(
    event: str, params: Dict[str, str], C: "_Colors", status: str,
    valid_events: Set[str], unknown_events: Set[str],
) -> None:
    """Pretty-print 1 event with color theo category."""
    if not event:
        return
    cat = _event_category(event)
    color = {
        "screen": C.cyan,
        "click": C.green,
        "ad": C.magenta,
        "iap": C.yellow,
        "lifecycle": C.dim,
        "other": C.reset,
    }.get(cat, "")

    # Flag unknown events
    is_unknown = event not in valid_events
    if is_unknown:
        unknown_events.add(event)
        flag = f" {C.yellow}(unknown){C.reset}"
    else:
        flag = ""

    # Inline params
    param_str = " ".join(f"{k}={v}" for k, v in params.items()) if params else ""
    status_tag = f" {C.green}{status}{C.reset}" if status else ""

    print(f"  {color}{event}{C.reset}{flag} {C.dim}{param_str}{C.reset}{status_tag}")


def _try_tick(
    lines: List[str], indices: List[int],
    event_name: str, params: Dict[str, str],
) -> bool:
    """Find a pending item that matches event_name AND any param value
    appearing in its text — tick it. Return True if ticked."""
    if not event_name:
        return False
    for i in indices:
        line = lines[i]
        if not re.match(r"^\s*-\s+\[\s+\]", line):
            continue
        if event_name not in line:
            continue
        # Find quoted value matches
        quoted = re.findall(r'"([^"]+)"', line)
        match = False
        if not quoted:
            match = True
        else:
            for q in quoted:
                if any(q == v or q in v for v in params.values()):
                    match = True
                    break
        if match:
            lines[i] = re.sub(r"\[\s+\]", "[x]", lines[i], count=1)
            return True
    return False


def _pascal(snake: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in snake.split("_") if p)


if __name__ == "__main__":
    sys.exit(main())
