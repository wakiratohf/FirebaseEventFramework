# analytics-automation

Bộ công cụ tự động hoá triển khai analytics events cho các dự án Android dùng module `:firebase-events` v1.0.0.

## What's new — v1.14 (TOH-TowerVPN real-world divergence)

User đã sửa skill trực tiếp trên project — adopt toàn bộ:

**Stateful button types** (NEW big feature):
- `type: toggle` → sinh 2 const `_ON` / `_OFF` (vd `SPLIT_TUNNELING_TOGGLE_ALL_ON/OFF`). Call site: `logClickBtn(if (new) X_ON else X_OFF)`.
- `type: checkbox` → 2 const `_CHECKED` / `_UNCHECKED` cho Checkbox/TriStateCheckbox.
- `type: accordion` → 2 const `_EXPANDED` / `_COLLAPSED` cho FAQ items, expandable rows.
- `type: radio` + `options: [{value, label}, ...]` → N const `RADIO_{LABEL}` cho single-choice dialog (vd timeout selector). buttonName = `radio{Label}` (camelCase).
- 1 spec entry → N enum const (mirror trong audit P11 — wired check áp cho mọi variant).

**P15 — Shared dialog component** (NEW pattern):
- Detect call site `AppAlertDialog(positiveText=..., onPositive=..., negativeText=..., onNegative=...)` không có analytics call.
- Dialog là điểm TERMINAL: method-ref (`onPositive = vm::foo`) hay passthrough (`onPositive = { close() }`) đều cần tự log.
- Bỏ qua file định nghĩa component (call site = @Preview), bỏ qua call site đã có log, bỏ qua call site với mọi handler `{}` (preview/stub).
- Mở rộng tên component qua `project.dialog_components: [Name, ...]` (mặc định `{AppAlertDialog}`).

**Custom clickable wrapper detection** (NEW scaffold feature):
- Scan 2-pass tìm `@Composable fun X(onClick: () -> Unit)` + `.clickable` nội bộ + tham chiếu onClick param trong body.
- Pass 2: liệt kê mọi call site, lấy `title/text/label = stringResource(R.string.X)` hoặc literal làm identity gợi ý.
- Wrapper tự fire analytics hoặc call site đã có log → loại khỏi flag (tránh báo nhầm code đã wire).
- KHÔNG tự sinh button — flag vào `spec-review.md` mục "Custom clickable wrapper" để user quyết định track per-item hay 1 custom event chung.

**`logClickBtn(ev, value)` overload** trong wrapper — pass `value` param định danh item khi cùng 1 button lặp trên N phần tử (vd faqId, packageName). value vào `click_btn_ev_value`, KHÔNG vào event name.

**Reverted khỏi v1.13**:
- Complex back-navigation detection (BackHandler + funref + body content scan) — quá nhiều false positive trên codebase thực. Giữ P14 simple value-form only như user đã đề xuất ở turn trước.
- Mode field tự auto-set trong scaffold + validator warning — gây conflict với spec đã edit manual. Giữ build_spec đọc từ `summary.project_mode` (legacy).
- 6-tier identity extraction cho Modifier.clickable — thay bằng `find_clickable_enclosing_calls` (stack-based) + reuse `extract_compose_button_identity` (đã có sẵn cho Button family).
- findViewById bare-variable tracing — không cần thiết cho Compose-first project.

## What's new — v1.13 (test/debug screens + simpler P14)

Từ kinh nghiệm triển khai thật trên TOH-TowerVPN — patch user cung cấp:

- **`project.ignore_screens`** — danh sách screen_id KHÔNG track (màn chỉ hiện ở test/debug mode, không có giá trị phân tích sản phẩm). Audit bỏ qua **P11/P12/P13/P14** cho file thuộc các màn này.
- **Auto-detect màn test-only** — `scaffold_spec.py` quét code tìm `composable(Routes.X)` / nav trigger nằm trong block `if (isTestMode) { ... }` hoặc `if (BuildConfig.DEBUG) { ... }` → đánh dấu `test_only: true` + emit `project.ignore_screens: [...]` tự động.
- **`screen.test_only: true`** — manual marker cho từng screen entry. Tương đương ignore_screens nhưng inline.
- **build_spec lọc** popups/buttons thuộc file màn test-only — không kéo vào spec ngay từ đầu.
- **P14 simpler** — match **value-form only**: `onClick = onNavigateBack/onNavigateUp/onBackClick/onBackPressed/onBack`. Khi user wrap thành `onClick = { logClickBtn(...); onBack() }` → P14 hết match, P13 take over kiểm tra log. Cleaner mental model, ít false-positive.

REMOVED so với v1.12:
- 4-channel back detection (body content + function-ref + BackHandler API + onBackPressed override). User's value-form approach single-pattern dứt khoát hơn.
- `tracked_components` wrapper bypass cho P14 (giờ chỉ check value-form ở value position, không depend wrapper detection).

Bug fix khi áp dụng patch:
- **`extract_test_gated_text` false-positive**: trước đó match `isTestMode` ở vị trí parameter declaration (`fun X(isTestMode: Boolean) {`) → lấy nhầm toàn bộ function body → MỌI screen flagged test_only. Fix: yêu cầu giữa gate token và `{` chỉ chứa `)` + whitespace (đặc trưng `if (gate) {`).

## What's new — v1.12

- **P14 — Back navigation tracking**: detect `onClick = onBack`, `onClick = navController::popBackStack`, `BackHandler { ... }`, `override fun onBackPressed` → flag MEDIUM. Override P13's hoisted-skip vì back là click có ý nghĩa funnel ngay cả khi hoisted/passthrough. Bypass khi wrapper tự track (vd `TrackedIconButton(buttonName="back", onClick=onBack)`).

## What's new — v1.11 (real-project hardening)

Fixes từ kinh nghiệm triển khai trên TOH-TowerVPN:

- **CRITICAL**: Compose `Modifier.clickable` multi-line chain giờ được detect (trước miss 80-90% click handler do regex yêu cầu `Modifier` cùng dòng `.clickable`)
- **Cross-file wrapper tracking** — auto-detect các `@Composable fun` tự fire analytics (vd `TrackedButton`), tránh báo nhầm P11/P13 khi parent dùng wrapper với `buttonName = "save"` String
- **Comment masking** — bỏ qua `/* .clickable {...} */` và `// Button(onClick = {...})` khi quét pattern → diệt false positives
- **No-op handler skip** — `onClick = { }` (consumeClicks pattern) không bị flag
- **Project mode fallback** — khi spec không có `project.mode`, suy luận từ `screens[*].kind` thay vì mặc định `view_binding` (mặc định cũ làm audit bỏ qua TOÀN BỘ Compose pattern)
- **Paren form body extraction** — `.clickable(enabled = true) { ... }` và `combinedClickable(onClick=..., onLongClick=...)` extract chính xác (trước có thể vớ nhầm block của composable kế tiếp)
- **`logClickBtn(ev: ClickBtnEv)` overload** trong wrapper — gọi `logClickBtn(ev)` thay vì truyền 3 field rời

Đã REMOVED (so với v1.10):
- Debug-only screens detection (3 channels: spec marker / annotation / nav inference)
- Debug guard skip (`if (BuildConfig.DEBUG)`, `isTestMode`, etc.)

Lý do remove: false-positive cao trên codebase thực, đồng thời pattern wrapper tracking + comment masking đã cover phần lớn use case mà debug-skip cố giải quyết. Nếu cần track production-only, dùng manual `if (BuildConfig.DEBUG)` rồi review report bỏ qua các flag không cần thiết.

## Tại sao?

Module `:firebase-events` đã chuẩn hoá 13 event + 9 user property runtime. Nhưng mỗi project mới vẫn phải:
- Viết tay `ScreenName.kt`, `ButtonName.kt`, `PopupName.kt`, `AnalyticsEventsUtils.kt`
- Gắn `logClickBtn` ở từng nút
- Extend `BaseTrackedActivity` ở từng Activity
- Verify thủ công với QC

→ Tốn 2–3 ngày/project, sai naming 15–30%, dễ thiếu chỗ tracking.

**Solution:** spec YAML versioned + Claude Code skill + 4 slash command = setup analytics mới **< 1 ngày**, sai naming < 3%.

## Đọc theo thứ tự nào?

| Vai trò | Lộ trình |
|---|---|
| PM / Leader | `01-overview.md` → `04-roadmap.md` → `templates/event-spec.weather-example.yaml` |
| Senior Dev Android (setup tool) | `02-architecture.md` → `.claude/skills/firebase-events-impl/SKILL.md` → `03-claude-code-workflow.md` |
| Member triển khai dự án | `docs/usage-guide.md` (đủ) |
| QC tester | `docs/usage-guide.md` mục "QC checklist" |

## Cấu trúc

```
analytics-automation/
├── 01-overview.md                       Tổng quan giải pháp 3 lớp
├── 02-architecture.md                   Mapping spec YAML ↔ firebase-events API
├── 03-claude-code-workflow.md           Chi tiết 4 slash command
├── 04-roadmap.md                        Lộ trình 2 tuần
│
├── .claude/                             Drop vào root mỗi project
│   ├── skills/firebase-events-impl/
│   │   ├── SKILL.md                     Bộ não của skill
│   │   ├── references/                  Reference docs (Claude đọc on-demand)
│   │   │   ├── event-catalog.md
│   │   │   ├── naming-conventions.md
│   │   │   ├── kotlin-templates.md
│   │   │   └── audit-patterns.md
│   │   ├── scripts/                     Python scripts (chạy độc lập được)
│   │   │   ├── sync_from_module.py      ← Auto sync docs từ module local
│   │   │   ├── scaffold_spec.py         ← Bước 0a: scan code → JSON candidates
│   │   │   ├── build_spec.py            ← Bước 0b: candidates → YAML + review.md (fully auto)
│   │   │   ├── validate_spec.py
│   │   │   ├── generate_kotlin.py
│   │   │   ├── audit_project.py
│   │   │   ├── verify_events.py
│   │   │   └── requirements.txt
│   │   └── assets/kotlin_templates/     Jinja2 templates
│   │       ├── ScreenName.kt.jinja
│   │       ├── ButtonName.kt.jinja
│   │       ├── PopupName.kt.jinja
│   │       ├── AnalyticsEventsUtils.kt.jinja
│   │       ├── CustomEvent.kt.jinja
│   │       └── BaseTrackedActivity.kt.jinja
│   └── commands/                        Slash command definitions
│       ├── analytics-sync-module.md     ← Sync module docs (auto-chạy ngầm)
│       ├── analytics-scaffold.md        ← Bước 0: scan code, đề xuất spec
│       ├── analytics-setup.md
│       ├── analytics-generate.md
│       ├── analytics-audit.md
│       └── analytics-verify.md
│
├── templates/
│   ├── event-spec.template.yaml         Template trống cho project mới
│   ├── event-spec.weather-example.yaml  Ví dụ cho app Weather (13 screen, 30 btn, 5 custom)
│   └── event-spec.vpn-example.yaml      Ví dụ cho app VPN (8 screen, 28 btn, 4 custom)
│
└── docs/
    ├── usage-guide.md                   Hướng dẫn dùng hằng ngày
    └── faq.md                           Câu hỏi thường gặp
```

## Quick start cho project mới

### Linux / macOS / WSL / Git Bash

```bash
# Option A: dùng installer (recommended)
./install.sh                            # prompt nhập target
./install.sh /path/to/android/project   # silent mode

# Option B: thủ công
cp -R analytics-automation/.claude .
mkdir -p docs/analytics
pip install -r .claude/skills/firebase-events-impl/scripts/requirements.txt
```

### Windows — PowerShell

```powershell
# Option A: prompt nhập target
.\install.ps1

# Option B: silent với path truyền vào
.\install.ps1 C:\Users\Me\AndroidStudioProjects\MyApp
.\install.ps1 -Target ~\code\android-app    # tilde expansion

# Nếu gặp execution policy error:
powershell -ExecutionPolicy Bypass -File install.ps1
```

### Windows — Command Prompt (cmd.exe)

```cmd
REM Option A: prompt nhập target
install.bat

REM Option B: silent
install.bat C:\Users\Me\AndroidStudioProjects\MyApp
install.bat "C:\path with space\my project"
```

`install.bat` tự động delegate sang PowerShell với `-ExecutionPolicy Bypass`, set UTF-8 code page (chcp 65001) để hiển thị tiếng Việt.

### Sau khi install xong

```
# Mở Claude Code trong project dir
claude
> /analytics-sync-module          # auto chạy ngầm
> /analytics-scaffold             # FULLY AUTO: scan code, sinh spec ~30 giây
> /analytics-setup                # sinh code Kotlin + patch Application
> /analytics-apply-tracked        # [Compose] batch wrap @Composable screens
> /analytics-audit                # quét điểm gắn tracking còn thiếu
> /analytics-generate --apply-missing  # sửa từng chỗ (hỏi confirm)
> /analytics-watch --source webhook    # live event stream debug
> /analytics-verify               # sinh checklist cho QC
```

→ Không cần viết YAML. Module docs **tự sync** khi bump version (sau `git pull`).
→ Total time cho project mới: **~3-5 phút** thay vì 2-3 ngày.

## 8 slash commands

| Command | Khi dùng |
|---|---|
| `/analytics-sync-module` | Sync docs từ module local — **auto chạy ngầm** trước các command khác |
| `/analytics-scaffold` | **Bước 0** — scan code, **fully-auto** sinh spec (~30s, 0 prompt) |
| `/analytics-setup` | 1 lần / project — khởi tạo (sinh code + patch Application) |
| `/analytics-generate` | Re-gen khi spec đổi (auto-migrate legacy split → consolidated) |
| `/analytics-apply-tracked` | **Compose only** — batch wrap `@Composable fun *Screen` với `TrackedScreen(...)` |
| `/analytics-audit` | Quét điểm log thiếu trong code |
| `/analytics-watch` | **Live event stream** với màu, validation, stats. Logcat / webhook / replay |
| `/analytics-verify` | Sinh checklist test cho QC |

## Tích hợp module `:firebase-events` qua git

Skill auto-detect module local theo thứ tự:

| Setup | Cách detect |
|---|---|
| Sibling folder `firebase-events/` | Default location |
| `libs/firebase-events/`, `modules/`, `shared/` | Default scan paths |
| `../firebase-events` (project ở subdir) | Default scan |
| Git submodule trong `.gitmodules` | Parse `.gitmodules` cho path |
| Custom path qua Gradle | Parse `settings.gradle.kts` → `include(":firebase-events")` |
| Maven/AAR (không có local) | Fallback dùng cached docs trong `references/module-docs/` |

→ User **không cần config** path nếu setup theo convention. Nếu không, dùng `--path=path/to/firebase-events`.

## Cũng dùng được không qua Claude Code

```bash
# Validate spec
python .claude/skills/firebase-events-impl/scripts/validate_spec.py docs/analytics/event-spec.yaml

# Generate
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main/java \
    --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
    --with-base-activity --with-helpers

# Audit
python .claude/skills/firebase-events-impl/scripts/audit_project.py \
    --spec docs/analytics/event-spec.yaml --src app/src/main \
    --out docs/analytics/audit-report.md \
    --fixes-out docs/analytics/.audit-fixes.json \
    --fail-on=high

# Verify
python .claude/skills/firebase-events-impl/scripts/verify_events.py \
    --mode=generate --spec docs/analytics/event-spec.yaml \
    --out docs/analytics/test-checklist.md
```

Phù hợp cho CI/CD (xem `03-claude-code-workflow.md` mục 9 — GitHub Actions example).

## Trạng thái

- v1.0.0 — Initial release
  - Support: XML View + AppCompatActivity/Fragment
  - Pattern audit: regex-based
  - Spec format: YAML versioned
- v1.1 (planned): Compose, multi-module, AST-based audit
- v1.2 (planned): Spec YAML include/import, sync từ Google Sheet

## Yêu cầu

- Python 3.11+
- PyYAML, Jinja2 (xem `requirements.txt`)
- Project Android dùng module `:firebase-events` v1.0.0+

## License

Theo license của module `firebase-events` (MIT).
