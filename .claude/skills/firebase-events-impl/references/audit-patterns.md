# Audit Patterns P1–P18

`audit_project.py` scan codebase Kotlin với regex để tìm chỗ thiếu / sai analytics tracking. Mỗi pattern có ID `Pxx`, severity (HIGH/MEDIUM/LOW), và optional auto-fix.

## Tổng quan theo mode

| Pattern | view_binding | compose | hybrid |
|---|:-:|:-:|:-:|
| P1 — Activity không extends BaseTrackedActivity | ✓ | ✓ | ✓ |
| P2 — Fragment thiếu logScreenStart/Stop | ✓ |   | ✓ |
| P3 — setOnClickListener không có logClickBtn | ✓ |   | ✓ |
| P4 — Dialog.show() không track popup | ✓ | ✓ | ✓ |
| P5 — custom_event spec chưa có call site | ✓ | ✓ | ✓ |
| P6 — File generated bị sửa tay (drift) | ✓ | ✓ | ✓ |
| P7 — Activity/Fragment có code, không có spec | ✓ | ✓ | ✓ |
| P11 — Compose Button onClick thiếu logClickBtn |   | ✓ | ✓ |
| P12 — Modifier.clickable thiếu logClickBtn |   | ✓ | ✓ |
| P13 — Hoisted button (onClick=onSave) cần track ở parent |   | ✓ | ✓ |
| P14 — Back navigation thiếu logClickBtn |   | ✓ | ✓ |
| P15 — Shared dialog có buttonText nhưng call site thiếu logClickBtn | ✓ | ✓ | ✓ |
| P16 — Dialog render ngoài TrackedScreen scope → screen_name rỗng |   | ✓ | ✓ |
| P17 — Application class chưa init analytics SDK | ✓ | ✓ | ✓ |
| P18 — Compose dialog không bọc trong TrackedPopup |   | ✓ | ✓ |

`ignore_screens` trong `project:` skip P11–P14, P16, P18 cho file thuộc screen đó.

---

## P1 — Activity không extends BaseTrackedActivity (HIGH)

**Phát hiện:** Class extends `AppCompatActivity` (hoặc `ComponentActivity`, `FragmentActivity`) trực tiếp, không qua `BaseTrackedActivity`, mà tên class match `screens[].class` trong spec.

**Regex:**
```
class\s+(\w+Activity)\s*[:(].*?(?:AppCompatActivity|ComponentActivity|FragmentActivity)\s*\(
```

**Fix tự động (`--apply-missing`):** Đổi `: AppCompatActivity()` → `: BaseTrackedActivity()` và inject override `getScreenName()` với hằng số match từ spec.

**Bypass:** Class abstract (không phải production screen), hoặc class có comment `// @no-analytics`.

---

## P2 — Fragment thiếu logScreenStart/Stop (HIGH) — view_binding/hybrid

**Phát hiện:** Class extends `Fragment`/`DialogFragment`/`BottomSheetDialogFragment` mà tên match `screens[].class`, nhưng `onResume`/`onPause` body không chứa `logScreenStart` hoặc `extends BaseTrackedFragment`.

**Lý do nghiêm trọng:** Fragment lifecycle là chỗ silent fail thường gặp nhất — Activity wrapper-only không tự log fragment view.

---

## P3 — setOnClickListener không có logClickBtn (MEDIUM) — view_binding/hybrid

**Phát hiện:** `setOnClickListener { ... }` block không có `logClickBtn` / `AnalyticsEventsUtils.logClickBtn` trong context window 8 dòng quanh lambda.

**Regex:**
```
(?:binding\.\w+|findViewById<[^>]+>\(R\.id\.\w+\)|\w+)\.setOnClickListener\s*\{
```

**False positive đã chặn:**
- Comment `//` cùng dòng → bị mask trước khi match
- String literal `"setOnClickListener {"` → bị mask
- `setOnClickListener(null)` (clear listener) → không match `{`

**Bypass:** Lambda body có comment `// @no-click-track` ở dòng đầu.

---

## P4 — Dialog/BottomSheet.show() không track popup (MEDIUM)

**Phát hiện:** `<expr>.show(...)` mà expr là `Dialog`/`BottomSheetDialog`/`AlertDialog`/`DialogFragment`, nhưng trong context window không có `logScreenStart(..., PopupName.X)`.

**Bypass:** Toast, Snackbar (không tính popup). Skill nhận diện tên class kết thúc `Dialog`/`Sheet`/`Popup`/`Modal`.

---

## P5 — custom_event chưa có call site (MEDIUM)

**Phát hiện:** `custom_events[].name` trong spec, nhưng grep toàn codebase không thấy ký tự `XxxEv(` (PascalCase của name).

**Refine khi có `trigger_hint`:**
```yaml
trigger_hint:
  file: VpnConnectionManager.kt
  method: onConnectionResult
```
→ audit chỉ check trong file đó, method đó. Nếu không khớp → report cụ thể file:method.

---

## P6 — File generated bị sửa tay (HIGH)

**Phát hiện:** File có header `// AUTO-GENERATED ...` mà spec-hash trong file khác spec-hash hiện tại của `event-spec.yaml`.

**Hành vi:** Báo lỗi HIGH; `--apply-missing` không tự overwrite (rủi ro mất data tay). User phải chạy `/analytics-generate` để xác nhận overwrite.

**Lý do:** Sửa tay file generated → lần generate sau bị overwrite mất → đêm trắng debug.

---

## P7 — Activity/Fragment có code, không có spec (LOW)

**Phát hiện:** File có `class XxxActivity : AppCompatActivity()` mà không có entry tương ứng trong `screens[]` của spec.

**Severity LOW** vì có thể là screen debug, test config, hoặc screen chưa kịp khai báo. User review report rồi quyết định thêm vào spec hay add comment `// @no-analytics`.

---

## P11 — Compose Button onClick thiếu logClickBtn (MEDIUM) — compose/hybrid

**Phát hiện:** Composable nào dùng các Button family sau với `onClick = { ... }` mà lambda không chứa `logClickBtn`/`logComposeClick`/`AnalyticsEventsUtils.logClickBtn`:

```
Button, OutlinedButton, TextButton, ElevatedButton, FilledTonalButton,
FloatingActionButton, ExtendedFloatingActionButton,
IconButton, IconToggleButton, FilledIconButton, OutlinedIconButton
```

**Bypass:** Đã wrap trong `TrackedButton`/`TrackedIconButton` (skill nhận diện qua context window).

---

## P12 — Modifier.clickable thiếu logClickBtn (MEDIUM) — compose/hybrid

**Phát hiện:** `Modifier.clickable { ... }` block không chứa `logClickBtn`. Skill handle cả paren form `clickable(onClick = { ... })` và chained multi-line.

**Bypass:** Modifier đã là `.trackedClickable(buttonName=...)` (đã wrap).

---

## P13 — Hoisted button (onClick=onSave) cần track ở parent (LOW info)

**Phát hiện:** `Button(onClick = onSave)` — onClick là function reference, không phải lambda. Đây là pattern hoisting đúng nhưng có nghĩa **parent composable** mới là chỗ phải fire `logClickBtn`.

**Severity LOW** vì có thể parent đã track đúng. Report là INFO để user verify chain hoisting.

---

## P14 — Back navigation thiếu logClickBtn (MEDIUM) — compose/hybrid

**Phát hiện:** `IconButton(onClick = onBack)` / `onClick = onBackPressed` / `onClick = onNavigateBack` / `onClick = onNavigateUp` / `onClick = onBackClick` / `onClick = popBackStack`.

Back button là chỗ user thoát screen — bắt buộc track để đo retention.

**Bypass:** Đã dùng `TrackedIconButton` (skill grep context window).

---

## P15 — Shared dialog có buttonText nhưng call site thiếu logClickBtn (MEDIUM)

**Phát hiện:** Dialog component dùng chung (tên kết thúc `Dialog`/`Alert`/`Confirm`/`Sheet`, không phải platform/Compose dialog) có param `positiveText`/`negativeText`/`neutralText` nhưng body call site không chứa `logClickBtn`/`AnalyticsEventsUtils.log`.

**Lý do:** Dialog là điểm **TERMINAL** — handler `onPositive = vm::foo` hoặc passthrough `onPositive = { close() }` không có caller sâu hơn track hộ, P13 (hoisted) bỏ sót. Phải tự `logClickBtn` trong handler.

**Bypass:** Platform dialog (`AlertDialog`, `Dialog`, `ModalBottomSheet`, ...) — P4 lo. File `AnalyticsEventsUtils.kt` và file generated.

---

## P16 — Dialog render ngoài TrackedScreen scope → screen_name rỗng (HIGH) — compose/hybrid

**Phát hiện:** File có `NavHost(`; một dialog component custom (cùng nhận diện như P15) được gọi **ngoài** block `NavHost(...) { ... }` (sibling của NavHost) mà không có `CompositionLocalProvider(LocalScreenName provides ...)` hoặc `TrackedScreen(` bao quanh.

**Root cause:** `LocalScreenName = staticCompositionLocalOf { "" }` — chỉ `TrackedScreen` cấp giá trị. Dialog global khai báo ở nav-graph level (overlay mọi màn) không có TrackedScreen ancestor → `LocalScreenName.current` rỗng → `click_btn_ev` ra screen rỗng (vd event name `_Ok` thay vì `HomeOk`).

**Trước fix (báo P16):**
```kotlin
if (showDialog) {
    AppAlertDialog(positiveText = "OK", onPositive = { ... })   // ❌ screen rỗng
}
```

**Sau fix (không báo, idempotent):**
```kotlin
val currentScreenName = routeToScreenName(
    navController.currentBackStackEntryAsState().value?.destination?.route
)
if (showDialog) {
    CompositionLocalProvider(LocalScreenName provides currentScreenName) {  // ✅
        AppAlertDialog(positiveText = "OK", onPositive = { ... })
    }
}
```

**Lưu ý:** Không wrap bằng `TrackedScreen` cho dialog overlay — nó còn fire `screen_view_ev` START/STOP, ghi nhầm 1 screen view. Chỉ cần `CompositionLocalProvider` cấp `LocalScreenName`.

**Giới hạn:** Detector dựa vào sự hiện diện `NavHost(` trong file — dialog root-level ngoài file nav-graph (vd trong Activity) chưa được quét.

---



---

## P17 — Application class chưa init analytics SDK (HIGH)

**Phát hiện:** File có `class XxxApp : Application()` (extends Application) nhưng `onCreate()` không gọi `AnalyticsBootstrap.init(...)` hay `AnalyticsModule.init(...)`.

**Sub-cases:**

- **HIGH**: Không có cả `AnalyticsBootstrap.init` lẫn `AnalyticsModule.init` → SDK chưa init, mọi log call early-return (kill-switch off).
- **MEDIUM**: Có `AnalyticsModule.init` nhưng thiếu `FirebasePrefs.saveAppOpenedTimestamp(...)` → `app_exit` event silent vì timestamp = 0.
- **MEDIUM**: Có `AnalyticsModule.init` nhưng thiếu `AppEventsInstaller.install(this)` → `time_open_app_ev` và `app_exit` không fire.

**Fix một dòng:**
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsBootstrap.init(app = this, isTestMode = BuildConfig.DEBUG)
    }
}
```

`AnalyticsBootstrap.init` (sinh tự động bởi skill) wrap 3 API: `AnalyticsModule.init` + `FirebasePrefs.saveAppOpenedTimestamp` + `AppEventsInstaller.install`.

---

## P18 — Compose dialog không bọc trong TrackedPopup (MEDIUM) — compose/hybrid

**Phát hiện:** Compose Dialog call (`AlertDialog`, `Dialog`, `ModalBottomSheet`, `BottomSheetDialog`, `DatePickerDialog`, `TimePickerDialog`) không có `TrackedPopup(` trong 600 chars ancestor → dialog không fire `screen_view_ev` riêng khi mở/đóng.

**Khác P16:** P16 catch dialog ở **nav-graph level** (sibling NavHost) → screen_name rỗng trong `click_btn_ev`. P18 catch dialog **trong screen** (Compose call) → MISS hẳn `screen_view_ev` event.

**Bypass:** Đã wrap `TrackedPopup(PopupName.X) { AlertDialog(...) { ... } }`.

**Trade-off**: P18 chỉ MEDIUM vì:
- Click trong dialog vẫn track (qua TrackedScreen của parent)
- Chỉ thiếu `screen_view_ev` riêng cho dialog (mất duration metric của dialog)

## False positive masking

Trước khi chạy regex, skill mask:

1. Comment `//` đến cuối dòng
2. Comment khối `/* ... */` (multi-line)
3. String literal `"..."` và `'...'`

Lý do: ví dụ trong KDoc `/** ... setOnClickListener { ... } ... */` từng false-positive P3. Sau khi mask, dấu `{` trong comment biến mất → không trigger.

## CI integration

```yaml
# .github/workflows/analytics-audit.yml
- name: Audit analytics
  run: |
    python .claude/skills/firebase-events-impl/scripts/audit_project.py \
      --spec docs/analytics/event-spec.yaml \
      --src app/src/main \
      --out audit-report.md \
      --fail-on=high
```

`--fail-on=high` → exit code 1 khi có HIGH severity → block merge.
`--fail-on=medium` → strict hơn, fail cả medium.
`--fail-on=low` → fail mọi issue.

## Auto-fix JSON

Khi chạy `audit_project.py --emit-fixes`, sinh thêm `.audit-fixes.json`:

```json
{
  "fixes": [
    {
      "pattern": "P1",
      "file": "app/src/main/java/.../HomeActivity.kt",
      "line": 12,
      "action": "extend_base",
      "from": "AppCompatActivity()",
      "to": "BaseTrackedActivity()",
      "inject": "override fun getScreenName() = ScreenName.HOME"
    }
  ]
}
```

`/analytics-generate --apply-missing` đọc file này để apply fix sau khi user xác nhận.
