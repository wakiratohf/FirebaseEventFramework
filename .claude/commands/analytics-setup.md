---
description: Setup analytics lần đầu — validate spec, generate toàn bộ Kotlin files, in hướng dẫn wiring.
---

Khi project đã có `event-spec.yaml` (sau scaffold hoặc viết tay), chạy `/analytics-setup` để khởi tạo lần đầu.

## Quy trình

1. Detect spec path (default: `docs/analytics/event-spec.yaml`). Hỏi nếu khác.
2. Validate:
   ```bash
   python .claude/skills/firebase-events-impl/scripts/validate_spec.py <SPEC>
   ```
   Nếu fail → in lỗi, dừng. User fix spec rồi chạy lại.
3. Generate (lần đầu, no diff vì chưa có file):
   ```bash
   python .claude/skills/firebase-events-impl/scripts/generate_kotlin.py \
       --spec <SPEC> \
       --src app/src/main/java \
       --templates .claude/skills/firebase-events-impl/assets/kotlin_templates \
       --no-confirm
   ```
4. List file đã tạo cho user.
5. In hướng dẫn wiring kế tiếp dựa trên `project.mode`:

**Với `view_binding`/`hybrid`:**
- `Application.onCreate` thêm: `AnalyticsModule.init(this)` + set user properties chuẩn
- Mỗi Activity trong `screens[]` (kind=activity) → đổi `: AppCompatActivity()` → `: BaseTrackedActivity()` + override `getScreenName()`
- Mỗi Fragment (kind=fragment) → đổi `: Fragment()` → `: BaseTrackedFragment()` + override `getScreenName()`. Nếu fragment trong ViewPager → set `useUserVisibleHint = true`
- Mỗi `setOnClickListener` → thêm `logClickBtn(ClickBtnEvCatalog.X)` ở dòng đầu lambda

**Với `compose`/`hybrid`:**
- Mỗi `*Screen()` composable → wrap với `TrackedScreen(ScreenName.X) { ... }`
- Mỗi `Button` action chính → đổi `Button(onClick=...)` → `TrackedButton(buttonName=ButtonName.X, onClick=...)`
- Back arrow / IconButton → `TrackedIconButton`
- Row/Card click → `Modifier.trackedClickable(buttonName=...)`
- Dialog content → wrap `TrackedPopup(PopupName.X) { ... }`

6. Đề xuất chạy `/analytics-audit` để verify wiring.

## Mã wiring sample (in cho user khi setup)

Application class:
```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsModule.init(this)
        AnalyticsUserProperties.setAppVersion(BuildConfig.VERSION_NAME)
        AnalyticsUserProperties.setDeviceModel(android.os.Build.MODEL)
        AnalyticsUserProperties.setOsVersion(android.os.Build.VERSION.RELEASE)
    }
}
```

Activity:
```kotlin
class HomeActivity : BaseTrackedActivity() {
    override fun getScreenName(): String = ScreenName.HOME
    // ... existing code
}
```
