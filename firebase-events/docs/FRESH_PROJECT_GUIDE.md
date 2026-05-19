# Fresh project — Full integration guide for `firebase-events`

> **Đối tượng**: project Android **chưa từng** tích hợp module
> `firebase-events`, chưa có Firebase setup, chưa có version catalog
> entries phù hợp. Tài liệu này dẫn từ con số 0 đến lúc log được
> event đầu tiên trên Firebase DebugView.
>
> Nếu Gradle / `google-services.json` đã wire xong, hãy chuyển sang
> [`samples/DEMO_IMPLEMENTATION_GUIDE.md`](samples/DEMO_IMPLEMENTATION_GUIDE.md) —
> tài liệu đó chỉ tập trung vào wiring code.

---

## Lộ trình (ước lượng 30–45 phút)

| Phase | Nội dung | Output |
|---|---|---|
| A. Prerequisites | Firebase project + `google-services.json` | File JSON đặt đúng chỗ |
| B. Copy module | Bê thư mục `firebase-events/` vào project | Module hiện trong Project view |
| C. Gradle wiring | `settings.gradle.kts` + `libs.versions.toml` + project plugins + app plugins | Sync Gradle xanh |
| D. Code wiring | Application init + catalog + wrapper + BaseActivity | App build & log được 1 event |
| E. Verify | DebugView + Logcat | Thấy event trên Firebase Console |

---

## Phase A — Prerequisites

### A1. Tooling
- Android Studio + AGP 8.x
- JDK 17 (Project Structure → SDK Location → Gradle JDK = `Embedded JDK 17`)
- Module dùng `minSdk 21` / `compileSdk 36` — project host phải có
  `compileSdk ≥ 36`.

### A2. Firebase project
1. Vào [Firebase Console](https://console.firebase.google.com/) → **Add project**.
2. Sau khi tạo project, ấn **Add app → Android**:
   - Nhập đúng `applicationId` của app (bao gồm suffix nếu có
     flavor / build type, ví dụ `com.example.demo.debug`).
   - Tải `google-services.json`.
3. Đặt file vào:
   - **Project không có flavor**: `app/google-services.json`.
   - **Có nhiều flavor**: 1 Firebase Android app cho **mỗi**
     applicationId, mỗi file đặt trong
     `app/src/<flavor>/google-services.json`. Plugin
     `google-services` sẽ tự merge.

> ⚠ KHÔNG commit `google-services.json` lên repo public.

### A3. Bật product trong Firebase Console
- **Analytics**: bật mặc định khi tạo Android app.
- **Crashlytics**: vào tab **Crashlytics** → **Enable**. Nếu lần đầu
  Crashlytics sẽ chờ event crash thật, không sao — module sẽ tự gọi
  `recordException(...)` khi gặp lỗi nội bộ.
- **Remote Config** (tuỳ chọn, để dùng `EventConfigs`):
  vào **Remote Config** → khởi tạo bằng key `event_configs`,
  default `{}`.

---

## Phase B — Copy module vào project

Từ thư mục source `toh-weather`:

```bash
cp -R /path/to/toh-weather/firebase-events /path/to/your-project/firebase-events
```

Cây thư mục phải giống:

```
your-project/
├── app/
├── firebase-events/
│   ├── build.gradle.kts
│   ├── VERSION
│   ├── README.md
│   ├── docs/
│   ├── proguard-rules.pro
│   ├── consumer-rules.pro
│   └── src/main/java/com/tohsoft/firebase_events/...
├── gradle/
│   └── libs.versions.toml
└── settings.gradle.kts
```

> **KHÔNG** sửa file `VERSION` — giữ nguyên để sau này diff được với
> bản upstream nếu cần lấy bug fix.

---

## Phase C — Gradle wiring

### C1. `settings.gradle.kts`

Thêm dòng `include(":firebase-events")`. Block hoàn chỉnh điển hình:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "YourProject"
include(":app")
include(":firebase-events")     // ← thêm dòng này
```

### C2. Version catalog `gradle/libs.versions.toml`

Module `firebase-events/build.gradle.kts` references các alias sau —
project host **phải** có đủ trong `libs.versions.toml`:

#### Bảng entries bắt buộc

| Vai trò | Alias dùng trong module | `[versions]` key |
|---|---|---|
| AGP plugin | `libs.plugins.androidLibrary` | `agp` |
| Kotlin plugin | `libs.plugins.kotlin.android` | `kotlin` |
| AndroidX core | `libs.androidx.core.ktx` | `coreKtx` |
| AndroidX appcompat | `libs.androidx.appcompat` | `appcompat` |
| Lifecycle ViewModel KTX | `libs.androidx.lifecycle.viewmodel.ktx` | `lifecycleViewmodelKtx` |
| Navigation Fragment KTX | `libs.androidx.navigation.fragment.ktx` | `navigationFragmentKtx` |
| Gson | `libs.gson` | `gson` |
| Material | `libs.material` | `material` |
| Firebase Core | `libs.firebase.core` | `firebaseCore` |
| Firebase Config | `libs.firebase.config` | `firebaseConfig` |
| Firebase Analytics | `libs.firebase.analytics` | `firebaseAnalytics` |
| Firebase Crashlytics | `libs.firebase.crashlytics` | `firebaseCrashlytics` |

#### Snippet copy-paste

Nếu catalog của project **trống**, dán nguyên block này (chỉnh
version về số mới nhất bạn muốn). Nếu đã có entry trùng tên, chỉ
thêm những entry còn thiếu — không nhân đôi.

```toml
[versions]
agp = "8.6.0"
kotlin = "2.1.0"
pluginGoogleService = "4.4.2"
pluginCrashlytics = "3.0.2"

coreKtx = "1.13.1"
appcompat = "1.7.0"
material = "1.12.0"
lifecycleViewmodelKtx = "2.8.6"
navigationFragmentKtx = "2.8.1"
gson = "2.10.1"

firebaseCore = "21.1.1"
firebaseConfig = "22.0.0"
firebaseAnalytics = "22.1.0"
firebaseCrashlytics = "19.1.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleViewmodelKtx" }
androidx-navigation-fragment-ktx = { group = "androidx.navigation", name = "navigation-fragment-ktx", version.ref = "navigationFragmentKtx" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

firebase-core = { group = "com.google.firebase", name = "firebase-core", version.ref = "firebaseCore" }
firebase-config = { group = "com.google.firebase", name = "firebase-config", version.ref = "firebaseConfig" }
firebase-analytics = { group = "com.google.firebase", name = "firebase-analytics", version.ref = "firebaseAnalytics" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics-ktx", version.ref = "firebaseCrashlytics" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
googleServices = { id = "com.google.gms.google-services", version.ref = "pluginGoogleService" }
firebaseCrashlytics = { id = "com.google.firebase.crashlytics", version.ref = "pluginCrashlytics" }
```

> **Nếu project KHÔNG dùng version catalog**: mở
> `firebase-events/build.gradle.kts` và đổi mỗi `libs.xxx` thành
> coordinate trực tiếp. Không khuyến khích — version catalog là
> khoản đầu tư nhỏ, lợi ích lớn.

### C3. Project-level `build.gradle.kts`

Khai báo plugin (không apply ở root):

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}
```

### C4. `app/build.gradle.kts`

Apply plugin Google Services + Crashlytics, add dependency module:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

android {
    namespace = "com.example.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.demo"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // SDK tracking
    implementation(project(":firebase-events"))

    // Firebase runtime — module đã expose qua api/implementation,
    // KHÔNG cần khai báo lại nếu module để là `api(...)`.
    // Module hiện để `implementation(...)`, nên app cần khai
    // báo các Firebase libs nó dùng trực tiếp (Crashlytics,
    // Analytics). Nếu app chỉ dùng wrapper, KHÔNG cần thêm gì.
}
```

> Lưu ý: module để các Firebase libs ở mức `implementation` →
> transitive dependency được truyền xuống `:app` ở compile classpath
> nhưng **không** lộ trong API. App vẫn dùng `AnalyticsEvents.logXxx`
> bình thường. Chỉ cần khai báo trực tiếp nếu app gọi
> `FirebaseAnalytics.getInstance()` / `FirebaseCrashlytics.getInstance()`
> bằng tay.

### C5. Đồng bộ Gradle

```bash
./gradlew :app:assembleDebug --dry-run
```

Phải xanh. Nếu lỗi thường gặp:

| Lỗi | Nguyên nhân | Fix |
|---|---|---|
| `Could not find libs.firebase.crashlytics` | Thiếu entry catalog | Quay lại C2, thêm entry |
| `Plugin [id: 'com.google.gms.google-services'] was not found` | Quên alias plugin ở root | C3 thêm `alias(...) apply false` |
| `File google-services.json is missing` | Chưa đặt JSON đúng chỗ | A2 lại |
| `Manifest merger failed: Apps targeting Android 12+ ... android:exported` | Activity / Service trong manifest thiếu `android:exported` | Bổ sung thuộc tính (không liên quan SDK) |

---

## Phase D — Code wiring

Phase này giống hệt 8 bước của
[`samples/DEMO_IMPLEMENTATION_GUIDE.md`](samples/DEMO_IMPLEMENTATION_GUIDE.md). Tóm
tắt nhanh:

### D1. Tạo `Application` class

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

        AnalyticsModule.init(
            appProvider = { this },
            sessionProvider = {
                if (foregroundedAt == 0L) null
                else System.currentTimeMillis() - foregroundedAt
            },
            isTestMode = BuildConfig.DEBUG
        )

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

        val consented = getSharedPreferences("consent", MODE_PRIVATE)
            .getBoolean("analytics_consent", true)
        AnalyticsModule.setEnabled(consented)

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
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.App">
    ...
</application>
```

Cần thêm permission Internet (cho Firebase/Crashlytics ping):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### D2. Catalog constants

Tạo package `com.example.demo.event`:

```kotlin
// event/ScreenName.kt
object ScreenName {
    const val HOME = "home"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
}

// event/ButtonName.kt
object ButtonName {
    const val REFRESH = "btn_refresh"
    const val SUBSCRIBE = "btn_subscribe"
    const val CLOSE = "btn_close"
}

// event/PopupName.kt
object PopupName {
    const val RATE_DIALOG = "rate_dialog"
    const val PERMISSION_REQUEST = "permission_request"
    const val NONE = ""
}
```

Quy ước (xem [`CONTEXT.md`](CONTEXT.md)): snake_case, ≤ 40 ký tự, bắt
đầu bằng chữ cái, không prefix project slug.

### D3. Wrapper `AnalyticsEventsUtils`

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

### D4. Tự động hoá screen-view qua `BaseTrackedActivity`

```kotlin
// ui/base/BaseTrackedActivity.kt
package com.example.demo.ui.base

import androidx.appcompat.app.AppCompatActivity
import com.example.demo.event.AnalyticsEventsUtils
import com.example.demo.event.PopupName

abstract class BaseTrackedActivity : AppCompatActivity() {

    private var screenStartAt: Long = 0L

    protected abstract fun screenName(): String
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

### D5. Log click ở screen

```kotlin
class HomeActivity : BaseTrackedActivity() {
    override fun screenName() = ScreenName.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        findViewById<View>(R.id.btn_refresh).setOnClickListener {
            AnalyticsEventsUtils.logClickBtn(
                screenName = ScreenName.HOME,
                buttonName = ButtonName.REFRESH
            )
        }
    }
}
```

### D6. Consent skeleton

```kotlin
fun onConsentAccept() {
    AnalyticsModule.setEnabled(true)
    prefs.edit().putBoolean("analytics_consent", true).apply()
}

fun onConsentDecline() {
    AnalyticsModule.setEnabled(false)
    prefs.edit().putBoolean("analytics_consent", false).apply()
}
```

SDK **không** persist `isEnabled`; app phải tự lưu và re-apply mỗi
lần `Application.onCreate` (đã làm ở D1).

> **Đầy đủ hơn?** Xem nguyên bản
> [`samples/DEMO_IMPLEMENTATION_GUIDE.md`](samples/DEMO_IMPLEMENTATION_GUIDE.md) cho
> custom event project-specific (`AnalyticsEvent`), webhook test-log
> mode, Remote Config push `EventConfigs`.

---

## Phase E — Verify

### E1. Build & install

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

### E2. Bật DebugView

```bash
adb shell setprop debug.firebase.analytics.app com.example.demo
```

Thay `com.example.demo` bằng `applicationId` thật. Sau đó mở app,
chuyển vài màn, ấn nút.

### E3. Kiểm tra Firebase Console

- **Analytics → DebugView**: phải thấy stream event trong < 30 giây:
  - `screen_view_ev` (`screen_name = HomeScreen`, `screen_state = stop`)
  - `click_btn_ev` (`click_btn_ev_name = HomeBtnRefresh`)
  - User properties: `language`, `app_version`, `screen_open`.
- **Crashlytics → Dashboard**: lần đầu sẽ "Waiting for first
  report" — không sao.

### E4. Logcat (debug builds)

`isTestMode = true` → SDK in dump event ra Logcat:

```bash
adb logcat -s AnalyticsEvents:V AnalyticsValidator:V
```

- `AnalyticsEvents` — dump payload mỗi event.
- `AnalyticsValidator` — cảnh báo nếu vi phạm Firebase naming limits
  (≤ 40 char event name, ≤ 100 char string value, v.v.).

---

## Checklist hoàn tất

### Phase A (Firebase)
- [ ] Firebase project đã tạo.
- [ ] `google-services.json` đặt đúng chỗ (`app/` hoặc `app/src/<flavor>/`).
- [ ] Analytics đã bật, Crashlytics đã bật.

### Phase B (Copy)
- [ ] Thư mục `firebase-events/` đã ở cấp sibling của `app/`.
- [ ] `VERSION` giữ nguyên.

### Phase C (Gradle)
- [ ] `settings.gradle.kts` có `include(":firebase-events")`.
- [ ] `libs.versions.toml` đủ 12 entries bảng C2.
- [ ] Root `build.gradle.kts` khai báo `googleServices` + `firebaseCrashlytics` plugin.
- [ ] `app/build.gradle.kts` apply plugin + `implementation(project(":firebase-events"))`.
- [ ] Manifest có `INTERNET` + `ACCESS_NETWORK_STATE` permission.
- [ ] `./gradlew :app:assembleDebug --dry-run` thành công.

### Phase D (Code)
- [ ] `DemoApp` extends `Application`, `init` SDK trong `onCreate`.
- [ ] Manifest có `android:name=".DemoApp"`.
- [ ] Catalog `ScreenName` / `ButtonName` / `PopupName` đã tạo.
- [ ] `AnalyticsEventsUtils` wrapper đã có.
- [ ] `BaseTrackedActivity` đã có, ít nhất 1 activity extends nó.
- [ ] Ít nhất 1 click button được log qua wrapper.
- [ ] Consent toggle ghi vào SharedPreferences và re-apply.

### Phase E (Verify)
- [ ] DebugView thấy ≥ 3 event khác loại.
- [ ] Logcat thấy dump `AnalyticsEvents`.
- [ ] Không có warning `AnalyticsValidator`.

---

## FAQ / pitfalls thường gặp

| Triệu chứng | Nguyên nhân | Fix |
|---|---|---|
| Build OK nhưng DebugView trống | Quên `adb shell setprop debug.firebase.analytics.app <appId>` | Set rồi cold-start lại app |
| `Could not resolve com.google.firebase:firebase-analytics` | Quên `dependencyResolutionManagement.repositories { google() }` | C1 |
| Event log được nhưng không hiện trên report (sau 24h) | Tên event > 40 char hoặc chứa ký tự lạ | Xem warning trong Logcat tag `AnalyticsValidator` |
| `AnalyticsModule.getApplication()` trả `null` | `init` chưa được gọi (ví dụ class app sai trong manifest) | Đảm bảo `android:name=".DemoApp"` đúng package |
| Crashlytics không hiện crash | Build debug R8/ProGuard chưa upload mapping | Đợi đến khi build release — debug đôi khi bị filter |
| `setEnabled(false)` rồi mở lại app, event vẫn được gửi | SDK không persist `isEnabled` | App phải tự lưu vào prefs + re-apply trong `Application.onCreate` |
| `TestLogMode.WEBHOOK` không gửi gì | Chưa `setWebhookSender(...)` | Đăng ký transport (xem [`CONFIGURATION.md`](CONFIGURATION.md)) |
| Multiple flavor, chỉ 1 flavor track được | Mỗi `applicationId` cần 1 Firebase Android app + 1 `google-services.json` riêng | A2 |

---

## Tài liệu tham khảo

| File | Nội dung |
|---|---|
| [`README.md`](../README.md) | Overview + SemVer + danh sách public API |
| [`INTEGRATION.md`](INTEGRATION.md) | Setup Gradle (overlap với Phase B-C, ngắn hơn) |
| [`samples/SAMPLE_APP.md`](samples/SAMPLE_APP.md) | Code mẫu Application + Activity + IAP + consent |
| [`EVENT_CATALOG.md`](EVENT_CATALOG.md) | Schema chi tiết 13 event + 9 user property |
| [`PROJECT_EVENT_TEMPLATE.md`](PROJECT_EVENT_TEMPLATE.md) | Pattern thêm event project-specific |
| [`CONFIGURATION.md`](CONFIGURATION.md) | `EventConfigs`, Remote Config, TestLogMode |
| [`CONTEXT.md`](CONTEXT.md) | Glossary + naming convention |
| [`samples/DEMO_IMPLEMENTATION_GUIDE.md`](samples/DEMO_IMPLEMENTATION_GUIDE.md) | Guide phiên bản "đã wire Gradle xong" (Phase D rút gọn) |
| [`MIGRATION.md`](MIGRATION.md) | Migration notes giữa các version bump |
