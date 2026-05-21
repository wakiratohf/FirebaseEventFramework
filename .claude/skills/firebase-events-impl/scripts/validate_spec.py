#!/usr/bin/env python3
"""
validate_spec.py — Validate event-spec.yaml against Firebase naming limits +
project conventions.

Usage:
    python validate_spec.py <path-to-event-spec.yaml>

Exit codes:
    0 — OK
    1 — Validation errors
    2 — Spec file not found or YAML parse error

Validations:
    - Firebase limits:
        event_name      ≤ 40 chars, [a-zA-Z0-9_], start with letter
        param_name      ≤ 40 chars, [a-zA-Z0-9_], start with letter
        param_value     ≤ 100 chars
        user_property   ≤ 24 chars, [a-zA-Z0-9_], start with letter
    - Project conventions:
        screen.id        snake_case, ≤40
        button.id        snake_case, prefix btn_ or radio_, ≤40
        button.name      CamelCase letters/digits only
        popup.id         snake_case, no "popup" word
        custom_event     snake_case, ends with _ev
        custom_event.params[].name  snake_case
    - Structural:
        button.screen → must exist in screens
        button.popup  → must exist in popups
        no duplicate ids in each section
        project.mode ∈ {view_binding, compose, hybrid}
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

try:
    import yaml
except ImportError:
    sys.stderr.write(
        "ERROR: PyYAML missing.  pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt\n"
    )
    sys.exit(2)


# --- Firebase limits ---
FB_EVENT_NAME_MAX = 40
FB_PARAM_NAME_MAX = 40
FB_USER_PROP_MAX = 24
FB_PARAM_VALUE_MAX = 100

# --- Patterns ---
# IDs accept both snake_case (manage_location) and camelCase (manageLocation).
# Both produce identical SCREAMING_SNAKE for const + identical camelCase for value
# after generator processing. NAMING_CONVENTION.md picks camelCase as canonical.
SNAKE_CASE = re.compile(r"^[a-z][a-z0-9_]*$")
ID_PATTERN = re.compile(r"^[a-z][a-zA-Z0-9_]*$")  # snake_case OR camelCase, lowercase first
# Module's NAMING_CONVENTION.md: camelCase = first letter lowercase, vd "back", "addLocation".
# (Tên cũ CAMEL_CASE giữ lại tránh break các chỗ tham chiếu, semantic giờ là camelCase chuẩn.)
CAMEL_CASE = re.compile(r"^[a-z][A-Za-z0-9]*$")
BUTTON_ID = re.compile(r"^(btn|radio)_[a-z0-9_]+$")
EVENT_NAME = re.compile(r"^[a-z][a-z0-9_]*_ev$")
FB_VALID_ID = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]*$")
RESERVED_PREFIXES = ("ga_", "google_", "firebase_")

VALID_MODES = {"view_binding", "compose", "hybrid"}
VALID_KINDS = {"activity", "fragment", "composable", None}
VALID_TYPES = {"toggle", "checkbox", "accordion", "radio", None}
VALID_PARAM_TYPES = {"string", "int", "long", "double", "boolean"}


class ValidationError(Exception):
    pass


def main() -> int:
    if len(sys.argv) >= 2 and sys.argv[1] in ("-h", "--help"):
        print(
            "Usage: validate_spec.py <path-to-event-spec.yaml>\n"
            "\n"
            "Validate event-spec.yaml against Firebase naming limits + project\n"
            "conventions. Returns exit code 0 if OK, 1 on validation errors,\n"
            "2 on file/YAML errors. See script docstring for full validation list."
        )
        return 0

    if len(sys.argv) != 2:
        sys.stderr.write("Usage: validate_spec.py <path>\n")
        return 2

    spec_path = Path(sys.argv[1])
    if not spec_path.is_file():
        sys.stderr.write(f"ERROR: spec file not found: {spec_path}\n")
        return 2

    try:
        spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
    except yaml.YAMLError as e:
        sys.stderr.write(f"ERROR: YAML parse failed: {e}\n")
        return 2

    if not isinstance(spec, dict):
        sys.stderr.write("ERROR: top-level YAML must be a mapping\n")
        return 2

    errors: List[str] = []
    warnings: List[str] = []

    _validate_project(spec, errors)
    screens = _validate_screens(spec, errors)
    popups = _validate_popups(spec, errors)
    _validate_buttons(spec, errors, warnings, screens, popups)
    _validate_custom_events(spec, errors)
    _validate_user_properties(spec, errors)

    if warnings:
        print("WARNINGS:")
        for w in warnings:
            print(f"  ⚠ {w}")

    if errors:
        print(f"\nVALIDATION FAILED — {len(errors)} error(s):")
        for e in errors:
            print(f"  ✗ {e}")
        return 1

    print(f"✓ Spec valid — {len(screens)} screens, "
          f"{len(popups)} popups, "
          f"{len(spec.get('buttons') or [])} buttons, "
          f"{len(spec.get('custom_events') or [])} custom events, "
          f"{len(spec.get('user_properties') or [])} user properties.")
    return 0


# --- Section validators ---

def _validate_project(spec: Dict[str, Any], errors: List[str]) -> None:
    project = spec.get("project")
    if not isinstance(project, dict):
        errors.append("`project:` section missing or not a mapping")
        return

    for required in ("name", "package", "application_class"):
        v = project.get(required)
        if not isinstance(v, str) or not v.strip() or v.startswith("TODO_"):
            errors.append(f"project.{required} missing or still placeholder")

    mode = project.get("mode", "view_binding")
    if mode not in VALID_MODES:
        errors.append(
            f"project.mode = '{mode}' invalid. Must be one of {sorted(VALID_MODES)}"
        )

    style = project.get("event_name_style", "pascal_case")
    if style not in ("pascal_case", "camel_case"):
        errors.append(
            f"project.event_name_style = '{style}' invalid. "
            f"Must be 'pascal_case' (default — module convention) "
            f"or 'camel_case' (first letter lowercase, eg `settings_settingsItem`)."
        )

    features = project.get("features") or {}
    if not isinstance(features, dict):
        errors.append("project.features must be a mapping")

    perms = features.get("permissions") if isinstance(features, dict) else None
    if perms is not None and not isinstance(perms, list):
        errors.append("project.features.permissions must be a list")


def _validate_screens(
    spec: Dict[str, Any], errors: List[str]
) -> Dict[str, Dict[str, Any]]:
    screens_raw = spec.get("screens") or []
    if not isinstance(screens_raw, list):
        errors.append("`screens:` must be a list")
        return {}

    seen: Dict[str, Dict[str, Any]] = {}
    for i, entry in enumerate(screens_raw):
        loc = f"screens[{i}]"
        if not isinstance(entry, dict):
            errors.append(f"{loc} must be a mapping")
            continue

        sid = entry.get("id")
        if not isinstance(sid, str):
            errors.append(f"{loc}.id missing")
            continue
        if not ID_PATTERN.match(sid):
            errors.append(f"{loc}.id = '{sid}' must be snake_case or camelCase (lowercase first)")
        if len(sid) > FB_EVENT_NAME_MAX:
            errors.append(
                f"{loc}.id = '{sid}' too long ({len(sid)} > {FB_EVENT_NAME_MAX})"
            )
        if sid in seen:
            errors.append(f"{loc}.id = '{sid}' duplicate")
            continue

        cls = entry.get("class")
        if not isinstance(cls, str) or not cls.strip():
            errors.append(f"{loc}.class missing")

        kind = entry.get("kind")
        if kind not in VALID_KINDS:
            errors.append(f"{loc}.kind = '{kind}' invalid (use activity/fragment/composable)")

        seen[sid] = entry

    # validate parent FK
    for sid, entry in seen.items():
        parent = entry.get("parent")
        if parent is not None and parent not in seen:
            errors.append(f"screens['{sid}'].parent = '{parent}' not found")

    # validate ignore_screens FK
    project = spec.get("project") or {}
    ignore = project.get("ignore_screens") or []
    if isinstance(ignore, list):
        for x in ignore:
            # Accept screen.id (in spec) OR raw class name (vd SplashActivity,
            # AdActivity — not tracked but skill needs to know to return null in
            # resolveScreenName lambda). Per INTEGRATION.md Step 5.
            if x not in seen and not _looks_like_class_name(x):
                errors.append(
                    f"project.ignore_screens contains '{x}' — must be a screen id "
                    f"from screens[] or a raw class name (PascalCase ending Activity/Fragment/Dialog/...)"
                )

    return seen


def _looks_like_class_name(s: str) -> bool:
    """Heuristic: PascalCase string ending in Activity/Fragment/etc → class name."""
    if not s or not s[0].isupper():
        return False
    return s.endswith(("Activity", "Fragment", "DialogFragment", "Dialog", "Sheet", "BottomSheet"))


def _validate_popups(
    spec: Dict[str, Any], errors: List[str]
) -> Dict[str, Dict[str, Any]]:
    popups_raw = spec.get("popups") or []
    if not isinstance(popups_raw, list):
        errors.append("`popups:` must be a list")
        return {}

    seen: Dict[str, Dict[str, Any]] = {}
    for i, entry in enumerate(popups_raw):
        loc = f"popups[{i}]"
        if not isinstance(entry, dict):
            errors.append(f"{loc} must be a mapping")
            continue
        pid = entry.get("id")
        if not isinstance(pid, str):
            errors.append(f"{loc}.id missing")
            continue
        if not ID_PATTERN.match(pid):
            errors.append(f"{loc}.id = '{pid}' must be snake_case")
        if "popup" in pid:
            errors.append(
                f"{loc}.id = '{pid}' should not contain word 'popup' (module already strips it)"
            )
        if pid in seen:
            errors.append(f"{loc}.id = '{pid}' duplicate")
            continue
        seen[pid] = entry
    return seen


def _validate_buttons(
    spec: Dict[str, Any],
    errors: List[str],
    warnings: List[str],
    screens: Dict[str, Any],
    popups: Dict[str, Any],
) -> None:
    buttons_raw = spec.get("buttons") or []
    if not isinstance(buttons_raw, list):
        errors.append("`buttons:` must be a list")
        return

    seen: Set[Tuple[str, str]] = set()  # (screen, id)
    for i, entry in enumerate(buttons_raw):
        loc = f"buttons[{i}]"
        if not isinstance(entry, dict):
            errors.append(f"{loc} must be a mapping")
            continue

        screen = entry.get("screen")
        if screen not in screens:
            errors.append(f"{loc}.screen = '{screen}' not in screens[]")

        bid = entry.get("id")
        if not isinstance(bid, str):
            errors.append(f"{loc}.id missing")
            continue
        if not BUTTON_ID.match(bid):
            errors.append(
                f"{loc}.id = '{bid}' must start with `btn_` or `radio_` and be snake_case"
            )
        if len(bid) > FB_PARAM_NAME_MAX:
            errors.append(
                f"{loc}.id = '{bid}' too long ({len(bid)} > {FB_PARAM_NAME_MAX})"
            )

        name = entry.get("name")
        if not isinstance(name, str) or not name.strip():
            errors.append(f"{loc}.name missing")
        elif not CAMEL_CASE.match(name):
            errors.append(
                f"{loc}.name = '{name}' must be CamelCase letters/digits only "
                "(no underscore, no `btn` prefix)"
            )

        popup = entry.get("popup")
        if popup is not None and popup not in popups:
            errors.append(f"{loc}.popup = '{popup}' not in popups[]")

        btype = entry.get("type")
        if btype not in VALID_TYPES:
            errors.append(
                f"{loc}.type = '{btype}' invalid (use toggle/checkbox/accordion/radio)"
            )

        if btype == "radio":
            options = entry.get("options")
            if not isinstance(options, list) or not options:
                errors.append(f"{loc}.options required for type=radio")
            else:
                for j, opt in enumerate(options):
                    if not isinstance(opt, dict):
                        errors.append(f"{loc}.options[{j}] must be a mapping")
                        continue
                    if not isinstance(opt.get("value"), str):
                        errors.append(f"{loc}.options[{j}].value missing")
                    if not isinstance(opt.get("label"), str):
                        errors.append(f"{loc}.options[{j}].label missing")
                    elif not CAMEL_CASE.match(opt["label"]):
                        warnings.append(
                            f"{loc}.options[{j}].label = '{opt['label']}' should be CamelCase"
                        )

        triggers = entry.get("triggers")
        if triggers is not None:
            if not isinstance(triggers, list):
                errors.append(f"{loc}.triggers must be a list")
            else:
                for j, trg in enumerate(triggers):
                    if not isinstance(trg, dict):
                        errors.append(f"{loc}.triggers[{j}] must be a mapping")
                        continue
                    evname = trg.get("event")
                    if not isinstance(evname, str):
                        errors.append(f"{loc}.triggers[{j}].event missing")
                    params = trg.get("params") or {}
                    if not isinstance(params, dict):
                        errors.append(f"{loc}.triggers[{j}].params must be a mapping")

        key = (screen or "", bid or "")
        if key in seen:
            errors.append(f"{loc}: button '{bid}' duplicate on screen '{screen}'")
        seen.add(key)

    # ─── Cross-entry constraint: same id → same name + same type ───
    # Button name là MỘT const dùng chung (ButtonName.BTN_BACK = "Back"); ghép
    # với screen name lúc log. Nếu cùng `btn_back` ở 2 screen có name khác nhau
    # → ambiguity, generator không xử lý được.
    id_to_first: Dict[str, Tuple[int, str, str, Optional[str]]] = {}
    for i, entry in enumerate(buttons_raw):
        if not isinstance(entry, dict):
            continue
        bid = entry.get("id")
        if not isinstance(bid, str):
            continue
        name = entry.get("name", "")
        btype = entry.get("type")
        if bid not in id_to_first:
            id_to_first[bid] = (i, name, entry.get("screen", ""), btype)
            continue
        first_i, first_name, first_screen, first_type = id_to_first[bid]
        if name != first_name:
            errors.append(
                f"buttons[{i}]: button id `{bid}` has name='{name}' but "
                f"buttons[{first_i}] (screen={first_screen}) has name='{first_name}'. "
                f"Same id MUST have same name — button const is shared across screens."
            )
        if btype != first_type:
            errors.append(
                f"buttons[{i}]: button id `{bid}` has type={btype} but "
                f"buttons[{first_i}] has type={first_type}. Same id MUST have same type."
            )


def _validate_custom_events(spec: Dict[str, Any], errors: List[str]) -> None:
    events = spec.get("custom_events") or []
    if not isinstance(events, list):
        errors.append("`custom_events:` must be a list")
        return

    seen: Set[str] = set()
    for i, ev in enumerate(events):
        loc = f"custom_events[{i}]"
        if not isinstance(ev, dict):
            errors.append(f"{loc} must be a mapping")
            continue
        name = ev.get("name")
        if not isinstance(name, str):
            errors.append(f"{loc}.name missing")
            continue
        if not EVENT_NAME.match(name):
            errors.append(
                f"{loc}.name = '{name}' must be snake_case ending with `_ev`"
            )
        if len(name) > FB_EVENT_NAME_MAX:
            errors.append(
                f"{loc}.name = '{name}' too long ({len(name)} > {FB_EVENT_NAME_MAX})"
            )
        if any(name.startswith(p) for p in RESERVED_PREFIXES):
            errors.append(
                f"{loc}.name = '{name}' uses Firebase-reserved prefix"
            )
        if name in seen:
            errors.append(f"{loc}.name = '{name}' duplicate")
            continue
        seen.add(name)

        params = ev.get("params") or []
        if not isinstance(params, list):
            errors.append(f"{loc}.params must be a list")
            continue
        if len(params) > 25:
            errors.append(
                f"{loc}.params has {len(params)} entries > Firebase limit 25"
            )
        seen_p: Set[str] = set()
        for j, p in enumerate(params):
            ploc = f"{loc}.params[{j}]"
            if not isinstance(p, dict):
                errors.append(f"{ploc} must be a mapping")
                continue
            pname = p.get("name")
            ptype = p.get("type")
            if not isinstance(pname, str):
                errors.append(f"{ploc}.name missing")
                continue
            if not SNAKE_CASE.match(pname):
                errors.append(f"{ploc}.name = '{pname}' must be snake_case")
            if len(pname) > FB_PARAM_NAME_MAX:
                errors.append(
                    f"{ploc}.name = '{pname}' too long ({len(pname)} > {FB_PARAM_NAME_MAX})"
                )
            if pname in seen_p:
                errors.append(f"{ploc}.name = '{pname}' duplicate")
            seen_p.add(pname)
            if ptype not in VALID_PARAM_TYPES:
                errors.append(
                    f"{ploc}.type = '{ptype}' invalid (use {sorted(VALID_PARAM_TYPES)})"
                )


def _validate_user_properties(spec: Dict[str, Any], errors: List[str]) -> None:
    props = spec.get("user_properties") or []
    if not isinstance(props, list):
        errors.append("`user_properties:` must be a list")
        return

    seen: Set[str] = set()
    for i, p in enumerate(props):
        loc = f"user_properties[{i}]"
        if not isinstance(p, dict):
            errors.append(f"{loc} must be a mapping")
            continue
        name = p.get("name")
        if not isinstance(name, str):
            errors.append(f"{loc}.name missing")
            continue
        if not SNAKE_CASE.match(name):
            errors.append(f"{loc}.name = '{name}' must be snake_case")
        if len(name) > FB_USER_PROP_MAX:
            errors.append(
                f"{loc}.name = '{name}' too long ({len(name)} > {FB_USER_PROP_MAX})"
            )
        if name in seen:
            errors.append(f"{loc}.name = '{name}' duplicate")
        seen.add(name)
        ptype = p.get("type")
        if ptype not in {"string", "int", "boolean"}:
            errors.append(f"{loc}.type = '{ptype}' invalid (use string/int/boolean)")


if __name__ == "__main__":
    sys.exit(main())
