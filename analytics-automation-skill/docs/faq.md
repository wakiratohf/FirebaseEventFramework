# FAQ

## Câu hỏi chung

### Skill này khác gì với prompt mẫu `IMPLEMENT_PROMPT.md` trong module?

`IMPLEMENT_PROMPT.md` là prompt 1 lần — copy-paste cho Claude Code, member trả lời câu hỏi interactive. OK cho 1-2 project, nhưng:

- Không có format spec chính thức → mỗi project tự định nghĩa kiểu khác.
- Không có validation → spec sai sẽ ra code sai.
- Không có audit → không biết tracking thiếu chỗ nào.
- Không có verify → QC test thủ công.

Skill này formalize toàn bộ pipeline: spec YAML versioned → validator → generator → auditor → checklist generator. Mỗi step là 1 slash command, có thể chạy qua CI.

### Skill này thay thế `firebase-events` module không?

KHÔNG. Module `:firebase-events` là **runtime** (compile vào APK). Skill là **design-time tool** (chạy lúc dev, không có trong APK). Hai thứ độc lập.

### Mỗi project có cần copy skill vào `.claude/` không?

Có. Skill chỉ active khi nằm trong `.claude/skills/firebase-events-impl/` của project. Khuyến nghị copy từ repo `firebase-events`:
```bash
cp -R firebase-events/.claude-skill .claude
```
Hoặc dùng git submodule nếu team thích version explicit.

### Skill phụ thuộc Claude Code? Member không có quyền Claude Code thì sao?

Slash command thì cần Claude Code. Nhưng các script Python chạy độc lập:
```bash
python .claude/skills/firebase-events-impl/scripts/validate_spec.py ...
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py ...
python .claude/skills/firebase-events-impl/scripts/audit_project.py ...
python .claude/skills/firebase-events-impl/scripts/verify_events.py ...
```
Member không có Claude Code vẫn dùng được — chỉ thiếu phần "hỏi confirm từng diff".

## Spec YAML

### Tôi có thể không khai báo `class` cho screen được không?

Được, nhưng audit không map được Activity/Fragment với screen → sẽ báo INFO. Nên khai báo cho rõ ràng.

### Custom event có cần `_ev` suffix không?

Không bắt buộc, nhưng theo convention module (`screen_view_ev`, `click_btn_ev`) — nên giữ. Skill tự thêm `Ev` vào class name (`VpnConnectEv`) khi sinh.

### `user_properties` của tôi trùng tên với 1 cái built-in (vd: `language`) thì sao?

Validator warn — module đã có sẵn helper cho property đó. Skill sẽ skip không sinh wrapper.

### Spec dài quá (50+ screens) → file YAML khó đọc — split được không?

Hiện tại 1 spec = 1 file. Plan v1.1 sẽ support include:
```yaml
includes:
  - event-spec/screens.yaml
  - event-spec/buttons.yaml
```
Tạm thời: dùng `---` separator cho readability, hoặc dùng YAML anchor cho repeated parts.

## Generated code

### File `AnalyticsEventsUtils.kt` thiếu method tôi cần (vd: log connection_lost)

Đó là chỉ dấu rằng spec chưa khai báo event đó. Thêm vào `custom_events:` → regen.

### Tôi muốn custom logic trong `AnalyticsEventsUtils` (vd: throttle log)

Không sửa file generated. Tạo file extra:
```kotlin
// app/src/main/java/.../event/AnalyticsEventsUtilsExt.kt (KHÔNG có AUTO-GENERATED header)
fun AnalyticsEventsUtils.logClickBtnThrottled(...) { ... }
```

### Sinh code cho project dùng Compose có khác không?

V1.0 audit script chỉ scan XML View pattern. Cho Compose:
- Setup, generate vẫn chạy bình thường.
- Audit miss case `Button(onClick = { ... })` của Compose. Plan v1.1 thêm pattern Compose.
- Tạm thời: dev tự gắn `logClickBtn` ở `onClick` callback.

### Multi-module project (app + feature_x + feature_y)?

V1.0 chỉ audit folder `--src`. Workaround:
```bash
python audit_project.py --src app/src/main/...
python audit_project.py --src feature_x/src/main/...
```
Plan v1.1: hỗ trợ multi-source qua `--src` lặp lại.

## CI / vận hành

### Có thể chạy skill này trong GitHub Actions không?

Có. Ví dụ workflow trong `03-claude-code-workflow.md` mục 9. Script Python chạy thẳng, không cần Claude Code.

### CI fail vì validate spec — fix ở đâu?

Đọc output script → fix `docs/analytics/event-spec.yaml`. Commit lại.

### CI fail vì audit phát hiện HIGH severity issue — fix ở đâu?

Đọc `docs/analytics/audit-report.md` (CI nên upload artifact). Fix theo suggestion. Commit lại.

### Khi nào nên bump version skill?

Khi:
- Thêm template Jinja mới
- Thay đổi format spec YAML (breaking → major bump)
- Thêm audit pattern mới

Update version trong `SKILL.md` + tag git.

### Member sửa file generated rồi commit — preventable không?

Có 2 lớp bảo vệ:
1. **Audit phát hiện drift** (`P7`): file generated có hash spec khác hash spec hiện tại → HIGH severity.
2. **CI gate**: `audit_project.py --fail-on=high` → CI fail.

Bonus: pre-commit hook chạy `audit_project.py` cũng được.

## Edge cases

### Tôi cần permission mới không có trong `AllowPermission` enum của module

Tạo enum riêng trong `:app`:
```kotlin
// app/src/main/.../event/AppPermission.kt
enum class AppPermission { CONTACTS, MICROPHONE }
```
Wrapper riêng:
```kotlin
fun logAllowContacts(granted: Boolean) {
    AnalyticsUserProperties.logCustomUserProperty(
        userPropertyName = "allow_contacts",
        property = if (granted) "1" else "0",
    )
}
```
KHÔNG sửa source `:firebase-events`.

### Custom event có > 25 param

Firebase limit là 25. Workaround:
1. Gộp param ít quan trọng thành JSON string (1 param).
2. Tách thành 2 event riêng.
3. Move sang user_property (nếu là attribute lâu dài).

### Tôi muốn log event cho 1 SDK khác (vd: Mixpanel) song song Firebase

Không phải scope skill này. Nhưng có thể:
1. Implement `WebhookSender` interface cho Mixpanel HTTP API.
2. `AnalyticsModule.setWebhookSender(MixpanelSender)`.
3. Module sẽ mirror mọi event sang Mixpanel song song Firebase.

### App có analytics consent screen — wire vào skill thế nào?

`/analytics-setup` đã sinh code mặc định:
```kotlin
val consented = getSharedPreferences("consent", MODE_PRIVATE)
    .getBoolean("analytics_consent", true)
AnalyticsModule.setEnabled(consented)
```
Member nối UI vào key `consent / analytics_consent`. Khi user toggle:
```kotlin
prefs.edit().putBoolean("analytics_consent", isConsented).apply()
AnalyticsModule.setEnabled(isConsented)
```

## File layout v1.3 (consolidated)

### Khi nào skill nên dùng consolidated vs split?

**Consolidated** (default v1.3+, khuyến nghị):
- 3 file core: `EventConstants.kt` + `AnalyticsEventsUtils.kt` + `ProjectEvents.kt`
- + optional: `TrackedComposables.kt` (Compose) hoặc `BaseTrackedActivity.kt` (XML)
- Tổng: 3-5 file

**Split** (legacy v1.0-1.2, opt-in qua `--layout=split`):
- 6+ file: `ScreenName.kt`, `ButtonName.kt`, `PopupName.kt`, `AnalyticsEventsUtils.kt`, `project_events/*.kt` × N, `TrackedComposables.kt`/`BaseTrackedActivity.kt`

Dùng consolidated khi: bạn maintain bằng tay (ít file → ít scroll). Dùng split khi: project rất lớn (>50 custom events) muốn 1 file 1 class cho git blame.

### Object name không đổi sau migration

`ScreenName`, `ButtonName`, `PopupName` vẫn là **top-level object** trong cùng package `event.*`. Call site:
```kotlin
AnalyticsEventsUtils.logClickBtn(ScreenName.HOME, ButtonName.BTN_CONNECT)
```
Không đổi sau migration. Member không cần edit gì.

### Custom event import thay đổi

Trước (split): `import com.example.app.event.project_events.PurchaseCompletedEv`
Sau (consolidated): `import com.example.app.event.PurchaseCompletedEv`

Member fix bằng Android Studio "Optimize Imports" hoặc:
```bash
# Trong project root
find app/src/main -name '*.kt' -exec sed -i 's|\.event\.project_events\.|\.event\.|g' {} +
```

### Skill detect cả 2 layout cùng tồn tại

Nếu cả `ScreenName.kt` (legacy) và `EventConstants.kt` (new) cùng có → trùng const → Kotlin compile fail "redeclaration of object ScreenName".

Generator phát hiện và prompt migrate. Trả lời `y` để xóa legacy. Hoặc dùng flag `--migrate-legacy`:

```bash
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
    --spec docs/analytics/event-spec.yaml \
    --src app/src/main/java \
    --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
    --with-helpers --migrate-legacy --no-confirm
```

### Tôi muốn stay với split layout cũ

OK, dùng `--layout=split`:
```bash
python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
    --spec docs/analytics/event-spec.yaml --src app/src/main/java \
    --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
    --with-helpers --layout=split
```

Hoặc khai báo trong spec:
```yaml
output:
  layout: split
```
Skill sẽ dùng split mode mỗi lần `/analytics-generate`.

---

## Compose (v1.2+)

### Project tôi dùng Compose 100% — scaffold có scan được không?

Có. Từ v1.2 scaffold detect:
- `@Composable fun XxxScreen()`, `XxxRoute()`, `XxxPage()` → screens
- `NavHost { composable("route") { ... } }` → routes (dedup với composable functions)
- `Button`, `IconButton`, `OutlinedButton`, `TextButton`, `FAB`, `ElevatedButton`, `FilledTonalButton`, etc. → buttons
- `@Composable fun XxxDialog/XxxSheet/XxxModal()` → popups
- Inline `AlertDialog(...)`, `Dialog(...)`, `ModalBottomSheet(...)` → synthetic popups

Identity extraction theo priority chain: `@analytics:` comment → `Modifier.testTag()` → `contentDescription` → `Text(...)` label → fallback.

Chi tiết: đọc `.claude/skills/firebase-events-impl/references/compose-guide.md`.

### Scaffold báo nhiều button "ASK" — fix sao?

Tức là button không có identity rõ ràng. Chọn 1 trong 3:

1. **Migrate sang TrackedComposables** (best): replace `Button(...)` bằng `TrackedButton(buttonName = ButtonName.X, ...)`. Auto-track không cần annotate.
2. **Thêm `Modifier.testTag("btn_xxx")`** cho buttons miss identity. Cũng useful cho UI test.
3. **Thêm `// @analytics: btn_xxx`** comment ngay trên button.

### TrackedComposables.kt là gì?

File template Skill auto-emit khi `project.mode == compose|hybrid`. Cung cấp `TrackedScreen`, `TrackedButton`, `TrackedIconButton`, `TrackedPopup`, ... wrap call site bằng nó thay vì gọi `AnalyticsEventsUtils.logClickBtn` thủ công.

`TrackedScreen` cung cấp `LocalScreenName` qua CompositionLocal → nested `Tracked*` composable tự lấy screen name, không cần truyền thủ công.

### Project tôi hybrid (1 phần Compose, 1 phần XML)

Scaffold auto-detect mode `hybrid` khi 20-70% kt files có `@Composable`. Cả XML setOnClickListener và Compose Button đều scan. Generate sinh CẢ `BaseTrackedActivity.kt` (cho XML Activities) và `TrackedComposables.kt` (cho Compose screens).

### Compose Modifier.clickable {} có scan không?

Hiện tại CHƯA. v1.2 chỉ scan `Button` family. Workaround:
- Convert `Modifier.clickable` → `IconButton` / `Card(onClick = ...)` nếu phù hợp UX
- Hoặc thêm `// @analytics: btn_xxx` comment + log manually trong handler

Roadmap v1.3: detect `Modifier.clickable` với fallback identity từ comment/testTag.

### Compose item trong LazyColumn click — track sao?

Hiện tại CHƯA auto. Wrap card content bằng `TrackedButton` với `containerColor = Color.Transparent`, hoặc gọi manually trong `onClick`:
```kotlin
Card(onClick = {
    AnalyticsEventsUtils.logClickBtn(ScreenName.HOME, ButtonName.BTN_ITEM_CLICK)
    onItemClick(item)
}) { ... }
```

---



### Skill có biết về API mới của module sau khi module bump version không?

Có. Mỗi slash command chạy auto `sync_from_module.py` ngầm → đọc `firebase-events/VERSION`, so với cached version. Nếu khác → re-sync `firebase-events/docs/*.md` + index event classes vào `references/module-docs/`.

Member chỉ cần `git pull` module → lần slash command tiếp theo skill tự cập nhật.

### Module của tôi không ở `firebase-events/` mà ở vị trí lạ — skill có detect được không?

Auto-detect theo thứ tự:
1. `firebase-events/`, `firebase_events/` cùng cấp `app/`
2. `../firebase-events/` (project trong subdir)
3. `libs/firebase-events/`, `modules/firebase-events/`, `shared/firebase-events/`
4. Parse `settings.gradle.kts` tìm `include(":firebase-events")` → lấy projectDir nếu có
5. Parse `.gitmodules` tìm submodule chứa "firebase" + "event"
6. Scan 1 level deep từ root

Nếu vẫn không detect → dùng `--path=path/to/firebase-events`:
```bash
python .claude/skills/firebase-events-impl/scripts/sync_from_module.py --path=external/fb-events
```

### Project tôi dùng Maven dependency (không có module local) — skill có chạy được không?

Có, nhưng degraded. Skill fallback dùng cached `references/module-docs/` (snapshot lúc setup), hoặc nếu cache trống thì dùng hand-written `references/*.md`. Có thể stale so với module mới nhất.

Workaround:
- Clone module repo về local 1 lần → `--path=…/firebase-events`
- Hoặc upload `firebase-events/docs/*` manual vào `references/module-docs/` + tạo `.sync-info.json` với version tương ứng.

### CI fail vì sync mismatch — fix sao?

CI nên gọi `sync_from_module.py --check`. Nếu mismatch (exit code 2) → CI fail. Member fix bằng cách:
```bash
python .claude/skills/firebase-events-impl/scripts/sync_from_module.py
git add .claude/skills/firebase-events-impl/references/module-docs/
git commit -m "Sync firebase-events docs to vX.Y.Z"
```

### Tôi không muốn cached docs vào git — chúng có nên gitignore không?

KHÔNG nên gitignore. Reasons:
- CI cần sync OK trước khi build → nếu không có cache, CI phải clone module nữa
- Reviewer xem được docs version nào skill đang dựa vào
- Khi pull project về, không cần chạy sync tay

Recommend: commit `references/module-docs/` vào git, treat như source.

---



---

## Troubleshooting

### `validate_spec.py` báo `'parent' không tồn tại trong screens[]`

Spec có screen `parent: home` nhưng `home` không khai báo trong `screens:`. Fix typo hoặc bổ sung screen cha.

### `/analytics-generate` không sinh file gì

- Spec validate fail → fix spec.
- Spec không có `screens:` / `buttons:` / `custom_events:` → đúng, không có gì để sinh. Khai báo trước.

### `BaseTrackedActivity` đã tồn tại nhưng tôi muốn regen

Xoá file rồi `/analytics-generate --with-base-activity`. Hoặc edit manually rồi commit.

### Audit ra 0 issue nhưng tôi biết có chỗ thiếu tracking

- Có thể audit không scan đến module đó → check `--src` arg.
- Audit dùng regex, có false negative cho Compose / ViewBinding xa khỏi setOnClickListener. Bổ sung pattern vào `audit_project.py`.
- Tạm thời: code-review thủ công cho phần Compose.

### Generate ra code không compile

- Module `:firebase-events` chưa được integrate (check `app/build.gradle.kts`).
- Package trong spec sai (check `project.package`).
- Activity/Fragment trong spec không tồn tại trong project — chỉnh spec.
