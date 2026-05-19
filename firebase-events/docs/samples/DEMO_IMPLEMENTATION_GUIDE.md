# Demo project — Implementation guide for `firebase-events`

> **Đối tượng**: dev đang làm 1 demo Android project và **đã** thực hiện
> xong các bước Gradle:
> - `settings.gradle.kts` có `include(":firebase-events")` và
>   `include(":firebase-events-lint")`.
> - `app/build.gradle.kts` có `implementation(project(":firebase-events"))`
>   và `lintChecks(project(":firebase-events-lint"))`.
> - `android.lint.error += setOf("ClickBtnEvUnderscore",
>   "ClickBtnEvBtnPrefix", "ClickBtnEvNotCamelCase",
>   "ClickBtnEvEmpty")` để promote Lint issue lên ERROR.
> - Đã có `google-services.json` cho từng flavor.
>
> Tài liệu này chỉ tập trung vào **wiring code** để có thể log được
> event đầu tiên + tổ chức catalog cho dài hạn (kèm interface
> `ClickBtnEv` để Lint rule fire).
>
> Nếu bạn vẫn còn ở bước copy module / cấu hình Gradle, hãy đọc
> [`../INTEGRATION.md`](../INTEGRATION.md) hoặc
> [`../FRESH_PROJECT_GUIDE.md`](../FRESH_PROJECT_GUIDE.md) trước.

---

## Mục tiêu sau khi làm xong tài liệu này

1. SDK được khởi tạo đúng 1 lần trong `Application.onCreate`.
2. App có catalog `ScreenName` / `ButtonName` / `PopupName` (tránh
   hard-code string ở call-site).
3. Có lớp wrapper `AnalyticsEventsUtils` — UI code chỉ gọi wrapper,
   không gọi trực tiếp `AnalyticsEvents.logXxx`.
4. Có ví dụ log đủ 3 nhóm: **screen view**, **click button**, **user
   property** (consent).
5. Có ví dụ định nghĩa 1 event project-specific qua interface
   `AnalyticsEvent` (không fork SDK).
6. Có cơ chế bật/tắt analytics theo GDPR consent.
7. Verify được trên Firebase **DebugView**.

---

## Bước 1 — Khởi tạo SDK trong `Application`

Tạo (hoặc mở) class `Application` của demo, gọi `AnalyticsModule.init`
**trước** mọi `Activity` đầu tiên:

```kotlin
package com.example.demo

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties

class DemoApp : Application() {

    @Volatile private var foregroundedAt: Long = 0L

    override fun onCreate() {
        super.onCreate()

        // 1. Khởi tạo SDK
        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = {
                if (foregroundedAt == 0L) null
                else System.currentTimeMillis() - foregroundedAt
            },
            isTestMode = BuildConfig.DEBUG
        )

        // 2. Theo dõi foreground/background để cấp session cho SDK
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foregroundedAt = System.currentTimeMillis()
                }
                override fun onStop(owner: LifecycleOwner) {
                    foregroundedAt = 0L
                }
            }
        )

        // 3. Khôi phục consent đã lưu (mặc định bật)
        val consented = getSharedPreferences("consent", MODE_PRIVATE)
            .getBoolean("analytics_consent", true)
        AnalyticsModule.setEnabled(consented)

        // 4. Đẩy user property phổ biến (rẻ, gọi mỗi lần app open được)
        AnalyticsUserProperties.logLanguageAndAppVersion(
            language = resources.configuration.locales[0].language,
            appVersion = BuildConfig.VERSION_NAME
        )
    }
}
```

Đăng ký trong `AndroidManifest.xml`:

```xml
<application
    android:name=".DemoApp"
    ... >
```

> ⚠ Không gọi `AnalyticsModule.init` từ `Activity.onCreate` —
> `sessionProvider` lambda có thể capture instance đã bị destroy.

---

## Bước 2 — Khai báo catalog (constants) cho project

Tạo package `com.example.demo.event` và các file sau. **Không**
hard-code các string này ở call-site (typo = mất data, không
detect được).

> ⚠ Phải tạo interface marker `ClickBtnEv` — Lint rule trong
> `:firebase-events-lint` chỉ fire trên enum implement interface
> tên `ClickBtnEv` (any package).

```kotlin
// event/ClickBtnEv.kt — marker để Lint rule hook
package com.example.demo.event

interface ClickBtnEv {
    val screenName: String
    val buttonName: String
    val popupName: String
}

// event/ScreenName.kt
package com.example.demo.event

object ScreenName {
    const val HOME = "home"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
}

// event/PopupName.kt
package com.example.demo.event

object PopupName {
    const val RATE_DIALOG = "rateDialog"
    const val PERMISSION_REQUEST = "permissionRequest"
    const val NONE = ""
}

// event/HomeBtnEv.kt — 1 enum cho mỗi screen
package com.example.demo.event

enum class HomeBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    REFRESH(ScreenName.HOME, "refresh", ""),
    SUBSCRIBE(ScreenName.HOME, "subscribe", ""),
    CLOSE(ScreenName.HOME, "close", ""),
}
```

Quy ước (xem [`../CONTEXT.md`](../CONTEXT.md) và
[`../NAMING_CONVENTION.md`](../NAMING_CONVENTION.md)):
- `screenName` / `popupName`: camelCase value, ≤ 40 ký tự.
- `buttonName`: camelCase, KHÔNG `_`, KHÔNG prefix `btn`, bắt đầu
  bằng chữ thường. **Vi phạm → Lint fail build** với issue ID
  `ClickBtnEvUnderscore` / `ClickBtnEvBtnPrefix` /
  `ClickBtnEvNotCamelCase` / `ClickBtnEvEmpty`.

---

## Bước 3 — Thin wrapper `AnalyticsEventsUtils`

Wrapper là anti-corruption layer giữa UI code và SDK. UI **không**
được gọi `AnalyticsEvents.logXxx` trực tiếp.

```kotlin
// event/AnalyticsEventsUtils.kt
package com.example.demo.event

import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsModule
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models._ClickBtnEv
import com.tohsoft.firebase_events.models._ScreenViewEv
import com.tohsoft.firebase_events.models._ScreenViewEv.State

object AnalyticsEventsUtils {

    fun logScreenStart(screenName: String, popupName: String = PopupName.NONE) {
        AnalyticsUserProperties.logEventScreenOpen(screenName, popupName)
    }

    fun logScreenStop(
        screenName: String,
        durationSec: Int,
        popupName: String = PopupName.NONE,
        state: State = State.STOP
    ) {
        AnalyticsEvents.logScreenViewEv(
            _ScreenViewEv(
                screenName = screenName,
                screenState = state,
                popupName = popupName,
                duration = durationSec
            )
        )
    }

    fun logClickBtn(
        screenName: String,
        buttonName: String,
        popupName: String = PopupName.NONE
    ) {
        AnalyticsEvents.logClickBtnEv(
            _ClickBtnEv(
                screenName = screenName,
                buttonName = buttonName,
                popupName = popupName,
                time = secondsSinceAppOpen()
            )
        )
    }

    private fun secondsSinceAppOpen(): Int {
        val ts = AnalyticsModule.getAppOpenedTimestamp() ?: return 0
        return ((System.currentTimeMillis() - ts) / 1000).toInt()
    }
}
```

---

## Bước 4 — Tự động hoá screen-view qua `BaseActivity` / `BaseFragment`

Khuyến nghị: viết 1 base class để mọi screen tự log start/stop, không
phải nhớ ở từng activity.

```kotlin
// ui/base/BaseTrackedActivity.kt
package com.example.demo.ui.base

import androidx.appcompat.app.AppCompatActivity
import com.example.demo.event.AnalyticsEventsUtils
import com.example.demo.event.PopupName

abstract class BaseTrackedActivity : AppCompatActivity() {

    private var screenStartAt: Long = 0L

    /** Override ở screen con để trả về `ScreenName.XXX`. */
    protected abstract fun screenName(): String

    /** Override khi có popup/dialog đang phủ lên. */
    protected open fun popupName(): String = PopupName.NONE

    override fun onResume() {
        super.onResume()
        screenStartAt = System.currentTimeMillis()
        AnalyticsEventsUtils.logScreenStart(screenName(), popupName())
    }

    override fun onPause() {
        super.onPause()
        val duration = ((System.currentTimeMillis() - screenStartAt) / 1000).toInt()
        AnalyticsEventsUtils.logScreenStop(
            screenName = screenName(),
            durationSec = duration,
            popupName = popupName()
        )
    }
}
```

Áp dụng:

```kotlin
class HomeActivity : BaseTrackedActivity() {
    override fun screenName() = ScreenName.HOME
    // ...
}
```

---

## Bước 5 — Log click từ UI

Truyền enum constant trực tiếp — nếu sửa convention sai, Lint catch
ngay tại dòng khai báo enum (chứ không lan ra mọi call-site):

```kotlin
class HomeActivity : BaseTrackedActivity() {
    override fun screenName() = ScreenName.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            AnalyticsEventsUtils.logClickBtn(
                screenName = HomeBtnEv.REFRESH.screenName,
                buttonName = HomeBtnEv.REFRESH.buttonName,
                popupName = HomeBtnEv.REFRESH.popupName,
            )
            refreshData()
        }
    }
}
```

---

## Bước 6 — Project-specific event (không fork SDK)

Khi 13 event built-in chưa đủ (ví dụ `pull_to_refresh`), implement
`AnalyticsEvent` trong `:app`, **không** sửa SDK:

```kotlin
// event/PullToRefreshEv.kt
package com.example.demo.event

import android.os.Bundle
import com.tohsoft.firebase_events.models.AnalyticsEvent

data class PullToRefreshEv(
    val screenName: String,
    val itemsLoaded: Int,
    val durationMs: Long
) : AnalyticsEvent {
    override val eventName: String = "pull_to_refresh"
    override fun toBundle(): Bundle = Bundle().apply {
        putString("screen_name", screenName)
        putInt("items_loaded", itemsLoaded)
        putLong("duration_ms", durationMs)
    }
}

// Gọi:
AnalyticsEvents.logEvent(
    PullToRefreshEv(screenName = ScreenName.HOME, itemsLoaded = 24, durationMs = 432)
)
```

Custom event vẫn được kill-switch + test-log + soft validator chi
phối như event built-in.

---

## Bước 7 — Consent (GDPR/CCPA)

```kotlin
// ConsentDialog.kt
fun onAccept() {
    AnalyticsModule.setEnabled(true)
    prefs.edit().putBoolean("analytics_consent", true).apply()
}

fun onDecline() {
    AnalyticsModule.setEnabled(false)
    prefs.edit().putBoolean("analytics_consent", false).apply()
}
```

⚠ SDK **không** tự persist `isEnabled` — app phải tự lưu và re-apply
trên mỗi `Application.onCreate` (đã làm ở Bước 1, mục 3).

---

## Bước 8 — Verify trên Firebase DebugView + Lint

```bash
adb shell setprop debug.firebase.analytics.app com.example.demo
```

Mở Firebase Console → **Analytics → DebugView**, chạy app, ấn nút,
chuyển màn. Trong vòng vài giây phải thấy:
- `screen_view_ev` với `screen_name = HomeScreen`, `screen_state = stop`
- `click_btn_ev` với `click_btn_ev_name = HomeBtnRefresh`
- User property: `language`, `app_version`, `screen_open`.

Trong build debug, mở Logcat lọc tag `AnalyticsEvents` để xem dump
event ngay (do `isTestMode = true`).

Chạy Lint để verify rule active:

```bash
./gradlew :app:lintDebug
```

Xanh = catalog hợp lệ. Đỏ với issue ID `[ClickBtnEvXxx from firebase-events-lint]`
= có enum vi phạm convention — fix tại dòng được trỏ.

---

## (Tuỳ chọn) Bước 9 — Test-log mode qua webhook

Khi QA cần xem stream event realtime (không chờ DebugView):

```kotlin
// event/SlackWebhookSender.kt
object SlackWebhookSender : com.tohsoft.firebase_events.utils.WebhookSender {
    private var url = ""
    override fun initialize(groupName: String, userName: String, password: String) {
        url = groupName    // map groupName -> webhook URL
    }
    override fun sendEvent(message: String) {
        if (url.isEmpty()) return
        // POST { "text": message } tới url
    }
}

// Trong DemoApp.onCreate, TRƯỚC AnalyticsModule.init
AnalyticsModule.setWebhookSender(SlackWebhookSender)

// Tester bật mode từ QA screen
AnalyticsModule.setWebhookInfo(
    context = this,
    groupName = "https://hooks.slack.com/services/XXX",
    userName = "#qa-events",
    password = "ignored"
)
```

Nếu không đăng ký `setWebhookSender`, `TestLogMode.WEBHOOK` no-op
(Telegram mode vẫn dùng được nếu cấu hình bot).

---

## (Tuỳ chọn) Bước 10 — `EventConfigs` qua Remote Config

Để có thể tắt 1 loại event noisy từ server không cần ship build mới:

```kotlin
val json = FirebaseRemoteConfig.getInstance().getString("event_configs")
val cfg = Gson().fromJson(json, EventConfigs::class.java)
AnalyticsModule.setEventConfigs(this, cfg)
```

Schema JSON xem [`CONFIGURATION.md`](CONFIGURATION.md). SDK tự
restore JSON đã lưu ở lần `init` kế tiếp.

---

## Checklist hoàn thành

- [ ] `DemoApp` đã `init` SDK trong `onCreate`, có `sessionProvider`
      đo theo `ProcessLifecycleOwner`.
- [ ] `AndroidManifest.xml` khai báo `android:name=".DemoApp"`.
- [ ] Tồn tại interface `ClickBtnEv` (marker cho Lint).
- [ ] Tồn tại `ScreenName`, `PopupName` constants, ít nhất 1 enum
      (vd `HomeBtnEv`) implement `ClickBtnEv`.
- [ ] Tồn tại `AnalyticsEventsUtils` wrapper; UI gọi qua wrapper.
- [ ] `BaseTrackedActivity` (hoặc tương đương) tự log screen view.
- [ ] Ít nhất 1 click button được log, truyền enum constant chứ
      không hard-code string.
- [ ] Consent toggle ghi vào SharedPreferences và re-apply ở
      `Application.onCreate`.
- [ ] Verify trên Firebase DebugView thấy ≥ 3 event khác nhau.
- [ ] `./gradlew :app:lintDebug` chạy (xanh hoặc fail rõ với issue
      ID `ClickBtnEv*`).
- [ ] (Optional) Có 1 event project-specific implement `AnalyticsEvent`.
- [ ] (Optional) Đăng ký `WebhookSender` cho QA mode.

---

## Anti-patterns cần tránh

| Sai | Vì sao |
|---|---|
| Hard-code `"home"`, `"btn_refresh"` ở call-site | Typo silently break analytics. Dùng constants. |
| Gọi `AnalyticsEvents.logXxx` trực tiếp ở UI | Mất layer chống đổi SDK; khó migrate sau này. Dùng `AnalyticsEventsUtils`. |
| Tạo enum button **không** implement `ClickBtnEv` | Lint rule không fire → mất tầng compile-time bảo vệ convention. Mọi enum chứa `buttonName` phải implement `ClickBtnEv`. |
| Đổi tên interface `ClickBtnEv` thành tên khác (vd `BtnEvent`) | Lint match theo simple name. Đổi tên = rule không fire. Giữ `ClickBtnEv` trong mọi project copy module. |
| Suppress Lint issue `ClickBtnEv*` bằng `@Suppress` hoặc baseline | Bypass convention 200+ entries hiện có → gãy dashboard. Fix value, không suppress. |
| Thêm enum project-specific vào `AllowPermission` trong SDK | Permission khác nhau theo app — định nghĩa enum riêng trong `:app`, gọi qua wrapper. |
| Khởi tạo SDK ở `Activity.onCreate` | Phải khởi tạo trong `Application.onCreate`. |
| Lưu `isEnabled` vào SDK rồi trông chờ persist | SDK **không** persist. App tự lưu + re-apply. |
| Quên unwind đã log: nghĩ `setEnabled(false)` xoá event đã gửi | Kill-switch chỉ chặn event mới. |

---

## Tài liệu tham khảo trong module

- [`README.md`](../README.md) — overview + version & SemVer.
- [`INTEGRATION.md`](INTEGRATION.md) — setup Gradle chi tiết (bước 1-2).
- [`EVENT_CATALOG.md`](EVENT_CATALOG.md) — bảng schema 13 event built-in + 9 user property.
- [`PROJECT_EVENT_TEMPLATE.md`](PROJECT_EVENT_TEMPLATE.md) — pattern thêm event project-specific.
- [`CONFIGURATION.md`](CONFIGURATION.md) — `EventConfigs`, Remote Config, TestLogMode, consent.
- [`SAMPLE_APP.md`](SAMPLE_APP.md) — sample code "hello world" (Application + Activity + IAP + consent).
- [`CONTEXT.md`](CONTEXT.md) — glossary và naming convention.
