---
name: firebase-events-impl
description: Tự động hoá tích hợp Firebase Analytics event tracking cho dự án Android dùng module `:firebase-events`. Sinh code Kotlin (ScreenName, PopupName, ClickBtnEv marker + per-screen enums (HomeBtnEv, SettingsBtnEv...), AnalyticsEventsUtils, AnalyticsBootstrap, BaseTrackedActivity, TrackedComposables) từ một file `event-spec.yaml` duy nhất, audit codebase để tìm chỗ thiếu tracking, sinh checklist verify cho QC, và scan Gradle compat giữa module với app target. Hỗ trợ đồng thời View Binding, findViewById truyền thống và Jetpack Compose (modes `view_binding`, `compose`, `hybrid`). Trigger skill này KHI: user nói "tích hợp Firebase Analytics", "log event", "screen tracking", "click tracking", "audit analytics", "setup tracking cho dự án Android", "QC checklist analytics", "thiết lập firebase-events", "import module firebase-events", "clone module firebase-events", "wire Gradle firebase-events", "scan Gradle compat", "kiểm tra compileSdk / minSdk", "version mismatch module và app", "generate code analytics", "kiểm tra tracking", "sinh code log event", "thêm screen vào analytics", "thêm button vào analytics", hoặc nhắc đến các slash command `/analytics-import-module`, `/analytics-align`, `/analytics-setup`, `/analytics-generate`, `/analytics-audit`, `/analytics-verify`, `/analytics-scaffold`. Cũng trigger khi user paste file `event-spec.yaml` hoặc hỏi cách tránh viết tay 5+ file Kotlin (ScreenName.kt, PopupName.kt, ClickBtnEv.kt + per-screen enum files, AnalyticsEventsUtils.kt) cho mỗi dự án Android mới, hoặc khi muốn import module `:firebase-events` từ GitHub repo public về project mới.
version: 1.15.0
---

# firebase-events-impl

**Mục tiêu:** biến việc tích hợp analytics tracking cho một project Android từ "2-3 ngày viết tay, sai naming 15-30%" thành "30 phút điền YAML + chạy 1 lệnh, sai naming <3%".

**Tiền đề:** project đã có module Android library `:firebase-events` (runtime layer, không sửa). Skill này là layer **design-time** — nó **biên dịch** một file YAML đặc tả analytics thành code Kotlin project-specific.

---

## Khi nào dùng skill này

| User nói gì | Bước đầu tiên |
|---|---|
| "Setup analytics cho project mới này" | Chạy `analytics-import-module` → `analytics-scaffold` → `analytics-setup` |
| "Import / clone module firebase-events" | Chạy `analytics-import-module` |
| "Thêm screen X vào tracking" | Sửa `event-spec.yaml` → chạy `analytics-generate` |
| "Tôi quên gắn log ở đâu" | Chạy `analytics-audit` |
| "QC cần checklist test" | Chạy `analytics-verify` |
| Paste 1 file `event-spec.yaml` | Validate spec → `analytics-generate` |
| "Project tôi dùng Compose" / "View Binding" / "findViewById" | Vẫn dùng skill — set đúng `project.mode` trong spec |

Nếu user chưa rõ flow, dẫn họ đọc `references/usage-guide.md`.

---

## Kiến trúc 3 lớp (cần nắm)

```
┌─────────────────────────────────────────────────────────────┐
│ LỚP 1 — RUNTIME: module :firebase-events (KHÔNG sửa)        │
│   AnalyticsEvents.logScreenViewEv / logClickBtnEv / logEvent│
│   AnalyticsUserProperties.logEventScreenOpen / setUserId    │
└─────────────────────────────────────────────────────────────┘
                              ▲ depends on
┌─────────────────────────────────────────────────────────────┐
│ LỚP 2 — DESIGN-TIME: SKILL NÀY                              │
│   scripts/validate_spec.py    — check naming + Firebase limit│
│   scripts/generate_kotlin.py  — Jinja2 → Kotlin             │
│   scripts/audit_project.py    — regex/scan tìm chỗ thiếu log │
│   scripts/verify_events.py    — sinh checklist QC            │
│   scripts/scaffold_spec.py    — scan code → candidate YAML  │
└─────────────────────────────────────────────────────────────┘
                              ▲ reads
┌─────────────────────────────────────────────────────────────┐
│ LỚP 0 — SPEC: docs/analytics/event-spec.yaml (per project)  │
│   PM/Leader maintain, versioned trong git.                  │
│   Single source of truth.                                   │
└─────────────────────────────────────────────────────────────┘
```

Code Kotlin sinh ra **luôn có header `// AUTO-GENERATED. DO NOT EDIT.`** — audit sẽ flag HIGH severity nếu thấy file generated bị sửa tay.

---

## 9 slash command (workflow chính)

### 0. `/analytics-import-module` — Bước -1: clone module + wire Gradle + align

Khi project chưa có module runtime `:firebase-events`. Skill clone từ GitHub repo public về cache local, copy module vào target, wire `settings.gradle(.kts)` + `app/build.gradle(.kts)`, **scan Gradle compat**. Mỗi lần chạy script luôn `git fetch + reset --hard` (pull code mới nhất) và force re-copy module vào target — đảm bảo target luôn sync với upstream dù VERSION chưa bump.

**5 module trong upstream repo** (theo `CLAUDE.md` của project):

| Module | Role | Default import? | Phụ thuộc |
|---|---|---|---|
| `:firebase-events` | Core SDK (public, SemVer) — log methods + models + transports. ZERO project deps. | ✅ | — |
| `:firebase-events-lint` | Lint rules enforce `buttonName` convention compile-time (`ClickBtnEvUnderscore`/`BtnPrefix`/`NotCamelCase`/`Empty`). | ✅ | (lintPublish) |
| `:app-events` | App-level wrappers: lifecycle (`time_open_app`/`app_exit`), intent (`open_app_from`), ads tracker, rate dialog. High-level: `AppEventsInstaller.install(app)`. | ✅ | `:firebase-events` |
| `:ads` | AdMob bridge — module DUY NHẤT biết Google Mobile Ads SDK. Compose `BannerAd` + `AdsManager.initialize()`. | ⚠ Opt-in | `:app-events` + `:firebase-events` |
| `:app` | Demo / smoke-test host. KHÔNG import. | ❌ | — |

**Mặc định import 3 module** (`firebase-events`, `firebase-events-lint`, `app-events`). Project dùng AdMob → thêm `--modules ads` vào lệnh.

5 phase:
1. **CLONE** — `git clone --depth 1` về `.claude-cache/firebase-events-upstream/`. Lần 2+: `git fetch + reset --hard origin/<branch>`.
2. **COPY** — copy module sang target (skip `.git/`, `build/`, `.gradle/`). LUÔN force-refresh, không skip dù VERSION match — code có thể đổi trước khi version bump.
3. **WIRE** — inject `include(":firebase-events")` + `include(":firebase-events-lint")` + `include(":app-events")` (+ optional `:ads`) vào `settings.gradle(.kts)` (idempotent).
4. **APPWIRE** — inject `implementation(project(":xxx"))` / `lintChecks(project(":firebase-events-lint"))` vào `app/build.gradle(.kts)`. Suffix `-lint` → `lintChecks`, còn lại → `implementation`. Module thiếu trong cache (vd fork) → graceful skip.
5. **RECONCILE** — scan catalog refs trong module's `build.gradle.kts`, đối chiếu với target's `libs.versions.toml`. Nếu target có lib cùng coordinate nhưng key khác → patch module file dùng key target. Báo missing libs (target catalog chưa có).
6. **ALIGN** — scan compat module vs target (SDK level, Kotlin, AGP, catalog entries), sinh report `docs/analytics/gradle-align-report.md`.

Idempotent — chạy lại không double-insert. Backup file `.bak` cho mỗi file Gradle bị sửa.

Lệnh chạy:
```bash
# 3-module default
python .claude/skills/firebase-events-impl/scripts/setup_module.py --target . --yes

# Thêm :ads cho project AdMob
python .claude/skills/firebase-events-impl/scripts/setup_module.py \
    --target . --modules firebase-events firebase-events-lint app-events ads --yes
```

Override URL nếu cần:
```bash
python .claude/skills/firebase-events-impl/scripts/setup_module.py \
    --target . \
    --repo-url https://github.com/your-fork/FirebaseEventFramework.git \
    --branch v1.2.0 --yes
```

### 0b. `/analytics-align` — Scan Gradle compat (standalone)

Re-chạy scan compat sau khi đã import module — tự động chạy trong phase 5 của `/analytics-import-module`, nhưng có thể chạy độc lập khi:
- Maintainer module bump version → kiểm tra lại compat
- Vừa sửa `app/build.gradle.kts` → confirm đã align
- CI/PR check để block merge

Phát hiện: **HIGH** SDK level mismatch (compileSdk/minSdk), **MEDIUM** Kotlin/AGP version mismatch + catalog entries missing, **LOW** targetSdk + dep version mismatch.

KHÔNG tự sửa `app/build.gradle.kts` (rủi ro chain-break). Với `--apply`: chỉ ADD placeholder vào `libs.versions.toml`.

```bash
python .claude/skills/firebase-events-impl/scripts/align_gradle.py \
    --target . --fail-on=high
```

### 1. `/analytics-scaffold` — Bước 0: scan code, đề xuất spec

*v1.6 — audit thêm 4 patterns (P8 hard-code event name, P9 bypass AnalyticsEventsUtils, P10 direct user property, P15 shared dialog component). Verify watch mode có color output + spec validation + stats panel.*

*v1.5 — scanner Compose cải thiện đáng kể: bắt được 14 button variants (Button, IconButton, FAB, ExtendedFAB, FilledTonalButton, ...), `Modifier.clickable` trên Box/Row/Card, inline `AlertDialog`/`ModalBottomSheet`, NavHost route. Identity extract qua chain: `@analytics:` hint > `testTag` > `contentDescription` > `Text(stringResource)` > `Text(literal)` > fallback `btn_compose_l<line>`. Scan cả arglist VÀ trailing lambda nơi `Text()` thường nằm.*

Khi project chưa có `event-spec.yaml`. Skill scan toàn bộ `app/src/main/` để tìm:
- Activity/Fragment hiện có → candidate `screens`
- `setOnClickListener` (View Binding/findViewById) + `Button(onClick=...)` (Compose) → candidate `buttons`
- `Dialog`, `BottomSheet`, `AlertDialog`, `ModalBottomSheet` → candidate `popups`

Output: `docs/analytics/event-spec.yaml` (draft) + `docs/analytics/spec-review.md` (chỗ skill không chắc, cần user review).

**Auto-detect mode:** tỷ lệ `.kt` files có `@Composable`:
- `<20%` → `view_binding`
- `20-70%` → `hybrid`
- `>70%` → `compose`

Lệnh chạy:
```bash
python .claude/skills/firebase-events-impl/scripts/scaffold_spec.py \
    --src app/src/main/java \
    --out docs/analytics/event-spec.yaml \
    --review-out docs/analytics/spec-review.md
```

### 2. `/analytics-setup` — Bước 1: setup lần đầu

Khi đã có `event-spec.yaml` (manual hoặc từ scaffold). Skill:
1. Validate spec (chặn nếu sai naming / Firebase limit)
2. Generate **tất cả** Kotlin files
3. In hướng dẫn wiring `Application.onCreate`, `BaseTrackedActivity`, etc.

### 3. `/analytics-generate` — Tái sinh code khi spec đổi

Chạy mỗi khi:
- Thêm/sửa screen, button, popup, custom_event trong spec
- Module `:firebase-events` bump version (sinh helper mới)

Idempotent: chạy 2 lần liên tiếp không đổi gì. Diff trước khi ghi.

Flag hữu ích:
- `--apply-missing` — đọc `audit-fixes.json` và áp dụng auto-fix
- `--dry-run` — chỉ in diff, không ghi

### 4. `/analytics-audit` — Quét codebase tìm thiếu

Output: `docs/analytics/audit-report.md` với mỗi issue gồm file:line, severity (HIGH/MEDIUM/LOW), suggestion.

Patterns chính (chi tiết: `references/audit-patterns.md`):
- **P1**: Activity không extends `BaseTrackedActivity`
- **P2**: Fragment không có `logScreenStart/Stop` trong `onResume/onPause`
- **P3** (View Binding/findViewById): `setOnClickListener { ... }` không có `logClickBtn` gần đó
- **P4**: `Dialog.show()` / `BottomSheet.show()` không log popup
- **P5**: `custom_event` trong spec mà code chưa gọi
- **P6**: File generated bị sửa tay (drift)
- **P7**: Activity/Fragment chưa có trong spec
- **P8** (constants): Hard-code event name literal (`"screen_view_ev"`) thay vì dùng constant
- **P9** (constants): Gọi `AnalyticsEvents.logXxx` trực tiếp, bypass `AnalyticsEventsUtils`
- **P10** (constants): Gọi `AnalyticsUserProperties.xxx` trực tiếp từ UI
- **P11** (Compose): `Button(onClick = { ... })` không có `logClickBtn` trong handler
- **P12** (Compose): `Modifier.clickable { ... }` không có `logClickBtn`
- **P13** (Compose): hoisted button — `Button(onClick = onSave)` chỉ pass-through, parent phải track
- **P14** (Compose): Back navigation (`onClick = onBack`) không track
- **P15**: Shared dialog (`AppAlertDialog`...) có `positiveText`/`negativeText` nhưng call site thiếu `logClickBtn`
- **P16** (Compose) **[HIGH]**: Dialog render ngoài `TrackedScreen` scope (sibling của `NavHost`) → `LocalScreenName.current` rỗng → click event có screen_name rỗng. Fix: wrap `CompositionLocalProvider(LocalScreenName provides <screen từ route>)`
- **P17** **[HIGH]**: Application class chưa init analytics SDK (thiếu `AnalyticsBootstrap.init` hay `AnalyticsModule.init` trong `onCreate`)
- **P18** (Compose): Dialog Composable không bọc trong `TrackedPopup` → không fire `screen_view_ev` cho dialog

### 5. `/analytics-verify` — Sinh checklist QC

Đọc spec → sinh `docs/analytics/test-checklist.md`:
```markdown
## Màn hình Home (HomeActivity)
- [ ] Mở app → log `screen_view_ev` với screen_name="Home"
- [ ] Bấm "ConnectButton" → log `click_btn_ev` với name="Home_ConnectButton"
- [ ] Bấm "PremiumButton" → log `iap_ev` với where="home_pro_icon"
```

Bonus mode `--watch`: tail Logcat trong khi tester click → auto-tick item nào fire đúng.

---

## Các bước cụ thể khi user yêu cầu

### A. User vừa drop skill vào project Android lần đầu

1. Hỏi (hoặc tự detect) **project root** chứa `app/`.
2. Detect module `:firebase-events` location:
   - Path mặc định: `firebase-events/` cùng cấp `app/`
   - Hoặc parse `settings.gradle.kts` tìm `include(":firebase-events")` → projectDir
   - Hoặc scan 1 level deep
   - Nếu không tìm được → hỏi user `--path=...`
3. Hỏi: "Bạn có sẵn `event-spec.yaml` chưa hay cần scaffold?"
4. Nếu chưa: chạy `scaffold_spec.py` → user review draft → chạy `validate_spec.py` → `generate_kotlin.py`.
5. Nếu có sẵn: validate → generate trực tiếp.
6. In hướng dẫn wiring:
   - `Application.onCreate` → `AnalyticsModule.init(...)`
   - Mỗi Activity → extends `BaseTrackedActivity` + override `getScreenName()` = `ScreenName.X`
   - Mỗi Fragment có screen riêng → extends `BaseTrackedFragment`
   - Mỗi `setOnClickListener` (XML) hoặc `Button` (Compose) → gắn `logClickBtn(ScreenName.X, ButtonName.Y)`
7. Chạy audit để verify wiring có đầy đủ chưa.

### B. User sửa spec rồi muốn cập nhật code

1. Validate spec — nếu fail, chỉ ra dòng + lý do.
2. Chạy generate với `--dry-run` trước → cho user xem diff.
3. Apply nếu OK.
4. Chạy audit để check trip-wire.

### C. User chạy audit, có nhiều issue

1. Đọc `audit-report.md`, group theo severity.
2. Đề xuất fix theo thứ tự: HIGH (drift, missing custom event) → MEDIUM (missing click log) → LOW (style).
3. Cho phép apply auto-fix qua `--apply-missing`.
4. Edge case: nếu nhiều file flagged P1/P2 (chưa extends Base*) → propose batch refactor.

### D. User project là Compose 100% / View Binding 100% / Hybrid

Skill **tự thích nghi** dựa trên `project.mode` trong spec:

| Mode | Files generate | Audit patterns active |
|---|---|---|
| `view_binding` | `BaseTrackedActivity.kt`, `BaseTrackedFragment.kt` | P1-P7 |
| `compose` | `TrackedComposables.kt` (TrackedScreen/TrackedButton/...) | P1, P4-P10, P11-P18 |
| `hybrid` | CẢ HAI bộ trên | TẤT CẢ P1-P18 |

Compose mode thêm hỗ trợ:
- `TrackedScreen(screenName=...)` — fire `screen_view_ev` qua DisposableEffect lifecycle
- `TrackedButton(buttonName=...)` — wrap `Button`, auto fire `click_btn_ev`
- `LocalScreenName` qua `CompositionLocalProvider` để nested composable lấy screen name không cần truyền tham số

Chi tiết Compose: `references/compose-guide.md`. Chi tiết View Binding: `references/view-binding-guide.md`.

---

## Constraint Claude PHẢI tuân thủ

- ❌ KHÔNG sửa file trong module `:firebase-events/` (đóng theo SemVer)
- ❌ KHÔNG hard-code chuỗi event/screen/button vào Activity/Fragment/Composable — phải dùng `ScreenName.X`, `ButtonName.Y`
- ❌ KHÔNG bypass `AnalyticsEventsUtils` để gọi thẳng `AnalyticsEvents.logXxx` (trừ custom event implement `AnalyticsEvent`)
- ❌ KHÔNG sửa file có header `// AUTO-GENERATED` (overwrite lần sau)
- ❌ KHÔNG invent pattern mới — luôn map về 13 event chuẩn module hoặc define custom event qua interface `AnalyticsEvent`
- ✅ Khi custom event chưa có trong module → implement `AnalyticsEvent`, gọi `AnalyticsEvents.logEvent(myEvent)`
- ✅ Khi permission mới (không có trong enum `AllowPermission` của module) → tạo enum riêng trong `:app`, KHÔNG fork module

---

## Cấu trúc thư mục skill

```
firebase-events-impl/
├── SKILL.md                    ← FILE NÀY
├── references/                 ← Claude đọc on-demand
│   ├── event-catalog.md            13 event chuẩn + 9 user property
│   ├── naming-conventions.md       Snake_case, CamelCase, Firebase limits
│   ├── audit-patterns.md           P1-P18 chi tiết regex
│   ├── compose-guide.md            Tracking trong Jetpack Compose
│   ├── view-binding-guide.md       Tracking với ViewBinding/findViewById
│   ├── kotlin-templates-guide.md   Map spec → file generated
│   └── usage-guide.md              Cheat sheet cho member
├── scripts/                    ← Chạy qua python, không load context
│   ├── setup_module.py            (v1.1 — clone + import module + chạy align)
│   ├── align_gradle.py            (v1.2 — scan compat module vs target)
│   ├── reconcile_catalog.py       (v1.4 — patch module catalog refs match target keys)
│   ├── apply_track_screen.py      (NEW v1.6 — auto-wrap Composable với TrackedScreen)
│   ├── validate_spec.py
│   ├── generate_kotlin.py
│   ├── audit_project.py
│   ├── verify_events.py
│   ├── scaffold_spec.py
│   └── requirements.txt
├── assets/
│   └── kotlin_templates/       ← Jinja2 template
│       ├── ScreenName.kt.jinja
│       ├── ButtonName.kt.jinja
│       ├── PopupName.kt.jinja
│       ├── ClickBtnEv.kt.jinja            (interface để Lint hoạt động)
│       ├── AnalyticsEventsUtils.kt.jinja
│       ├── BaseTrackedActivity.kt.jinja   (view_binding/hybrid)
│       ├── BaseTrackedFragment.kt.jinja   (view_binding/hybrid)
│       ├── TrackedComposables.kt.jinja    (compose/hybrid)
│       └── CustomEvent.kt.jinja
├── templates/
│   └── event-spec.template.yaml
└── commands/                   ← Claude Code slash commands
    ├── analytics-import-module.md  (v1.1)
    ├── analytics-align.md          (v1.2)
    ├── analytics-reconcile.md      (v1.4)
    ├── analytics-wrap.md           (NEW v1.6)
    ├── analytics-scaffold.md
    ├── analytics-setup.md
    ├── analytics-generate.md
    ├── analytics-audit.md
    └── analytics-verify.md
```

---

## Sử dụng độc lập (không qua Claude)

Mọi script đều chạy được trực tiếp bằng Python — phù hợp CI/CD:

```bash
pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt

# Validate
python .claude/skills/firebase-events-impl/scripts/validate_spec.py \
    docs/analytics/event-spec.yaml

# Generate
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main/java \
    --templates .claude/skills/firebase-events-impl/assets/kotlin_templates

# Audit (CI fail-on-high)
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main \
    --out docs/analytics/audit-report.md \
    --fail-on=high

# Verify
python .claude/skills/firebase-events-impl/scripts/verify_events.py \
    --spec docs/analytics/event-spec.yaml \
    --out docs/analytics/test-checklist.md
```

GitHub Actions example: xem `references/usage-guide.md`.

---

## Yêu cầu

- Python 3.11+
- PyYAML, Jinja2 (xem `scripts/requirements.txt`)
- Project Android dùng module `:firebase-events` v1.0.0+

## License

Theo license của module `firebase-events` (Apache 2.0 / MIT).
