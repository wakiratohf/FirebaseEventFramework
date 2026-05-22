# FirebaseEventFramework

Kho upstream / reference cho thư viện **`firebase-events`** — một lớp tracking sự kiện Firebase Analytics + Crashlytics dạng *drop-in* cho Android, kèm app demo tối thiểu để smoke-test.

> Mục đích chính của repo: phát triển và bảo trì SDK ở đây, sau đó **copy-paste** module `firebase-events/` sang các project Android khác. App `:app` không phải sản phẩm — chỉ là host để chạy thử SDK.

---

## Cấu trúc repo

| Module | Vai trò | Đặc điểm |
|---|---|---|
| [`firebase-events/`](firebase-events/) | Thư viện SDK lõi (public, có SemVer) | `minSdk 21`, `compileSdk 36`, JDK 17. Không phụ thuộc module nội bộ nào — copy-paste sang repo khác là chạy. |
| [`firebase-events-lint/`](firebase-events-lint/) | Lint rule đi kèm SDK | Pure-JVM (JDK 17). Enforce convention `buttonName` ở compile time (4 issue ID). Copy kèm `:firebase-events`. |
| [`app-events/`](app-events/) | Wrapper app-level (optional, `com.tohsoft.app_event`) | `minSdk 21`, JDK 17. Drop-in tracking cho lifecycle (time_open_app / app_exit), ads, rate dialog. Phụ thuộc `:firebase-events`. |
| [`TOH-Ad/`](TOH-Ad/) | Thư viện ads TOHSOFT + cầu nối → analytics (optional, `com.tohsoft.ad`) | `minSdk 23`, JDK 17, Compose. Library ads thật (AdMob + UMP, banner/inter/app-open/OPA). Bridge analytics ở `com.tohsoft.ads.analytics` track ad event qua `AdsEventTracker` (hiện chỉ banner). Phụ thuộc `:firebase-events` + `:app-events`. |
| [`app/`](app/) | Demo / smoke-test (`com.example.firebaseeventframework`) | `minSdk 23`, JDK 11, Jetpack Compose. Chỉ minh hoạ pattern tích hợp, không tái sử dụng. |

> `:firebase-events` là module lõi bắt buộc; `:firebase-events-lint`, `:app-events`, `:TOH-Ad` là tuỳ chọn — chỉ copy khi cần.
>
> Đừng đặt abstraction dùng chung vào `:app`. Đừng thêm `project(":...")` dependency vào `:firebase-events` (sẽ phá tính copy-paste) — các module khác được phép phụ thuộc *vào* nó, không phải chiều ngược lại.

---

## Quick start

Yêu cầu: JDK 17, Android SDK 36, Gradle Wrapper (đã có trong repo).

```bash
# Build SDK
./gradlew :firebase-events:assembleDebug

# Build & cài app demo lên device đang gắn
./gradlew :app:installDebug

# Bật Firebase DebugView cho app demo
adb shell setprop debug.firebase.analytics.app com.example.firebaseeventframework
adb logcat -s AnalyticsEvents:V AnalyticsValidator:V
```

### Test

Unit test của SDK chạy trên JVM (Robolectric-free). `testOptions.unitTests.isReturnDefaultValues = true` được bật có chủ đích — tránh viết test phụ thuộc semantics thật của `Bundle` trong `firebase-events/src/test`.

```bash
# Toàn bộ unit test SDK
./gradlew :firebase-events:testDebugUnitTest

# Chạy 1 class / 1 method
./gradlew :firebase-events:testDebugUnitTest \
  --tests "com.tohsoft.firebase_events.utils.EventNameValidatorTest"

./gradlew :firebase-events:testDebugUnitTest \
  --tests "*.AnalyticsModuleWebhookSenderTest.someMethod"
```

### Lint

```bash
./gradlew :firebase-events:lintDebug
./gradlew :app:lintDebug
```

---

## Tích hợp `firebase-events` vào project khác

1. Copy thư mục `firebase-events/` (và nên kèm `firebase-events-lint/` để có lint compile-time) sang repo đích, đặt cạnh `:app`.
2. Thêm `include(":firebase-events")` (và `include(":firebase-events-lint")`) vào `settings.gradle.kts` của repo đó.
3. `implementation(project(":firebase-events"))` + `lintChecks(project(":firebase-events-lint"))` trong `app/build.gradle.kts`.
4. Khởi tạo trong `Application.onCreate` (xem `app/.../DemoApp.kt` ở repo này để có mẫu).
5. (Tuỳ chọn) Copy thêm `app-events/` cho drop-in lifecycle tracking, và `TOH-Ad/` nếu app dùng thư viện ads TOHSOFT (AdMob + UMP) — bridge analytics nằm ở package `com.tohsoft.ads.analytics`.

Chi tiết từng bước, kể cả cấu hình Firebase project, flavor, Remote Config, consent, custom webhook:

- [`firebase-events/README.md`](firebase-events/README.md) — entry point của SDK
- [`firebase-events/docs/INTEGRATION.md`](firebase-events/docs/INTEGRATION.md) — setup cho project mới
- [`firebase-events/docs/FRESH_PROJECT_GUIDE.md`](firebase-events/docs/FRESH_PROJECT_GUIDE.md) — hướng dẫn tiếng Việt, đầy đủ hơn
- [`firebase-events/docs/EVENT_CATALOG.md`](firebase-events/docs/EVENT_CATALOG.md) — schema bundle của mọi event/user property có sẵn
- [`firebase-events/docs/PROJECT_EVENT_TEMPLATE.md`](firebase-events/docs/PROJECT_EVENT_TEMPLATE.md) — cách tự định nghĩa event của app mà không cần fork SDK
- [`firebase-events/docs/CONFIGURATION.md`](firebase-events/docs/CONFIGURATION.md) — `EventConfigs`, `TestLogMode`, master kill-switch
- [`firebase-events/docs/MIGRATION.md`](firebase-events/docs/MIGRATION.md) — ghi chú khi bump version
- [`firebase-events/VERSION`](firebase-events/VERSION) — phiên bản hiện tại (SemVer)

---

## Hiểu nhanh kiến trúc

```
firebase-events/
├── AnalyticsModule              # init, master kill-switch, transport injection
├── AnalyticsEvents              # 13 typed log methods + generic logEvent
├── AnalyticsUserProperties      # 9 user-property setters
├── models/                      # data class cho mỗi loại event, có toBundle()
└── utils/                       # transports, prefs, validator, test-log helpers
```

Ba mối quan tâm xuyên suốt được nối qua `AnalyticsModule`:

- **Master kill-switch** (`isEnabled`) — early-return cho mọi `logEvent`. SDK không tự lưu, host app phải lưu consent và áp lại mỗi lần `init`.
- **`EventConfigs`** — bật/tắt từng loại event qua boolean, serialize JSON xuống prefs, có thể push từ Remote Config.
- **`TestLogMode`** (`NONE` / `TELEGRAM` / `WEBHOOK`) — khi `isTestMode == true`, event chỉ dump xuống Logcat + side-channel, **không** lên Firebase.

> Lưu ý khi debug: nếu `isTestMode == true` thì event/user property **không** lên Firebase (chỉ mirror xuống Logcat + side-channel). Muốn thấy dữ liệu trong DebugView, đảm bảo `isTestMode == false` và master kill-switch đang bật.

---

## Conventions cần nhớ

- Event name: `lowercase_snake_case`, ≤ 40 chars, bắt đầu bằng chữ cái. Event built-in của SDK kết thúc bằng `_ev`.
- Param key ≤ 40 chars, string value ≤ 100 chars. Vi phạm bị `EventNameValidator` cảnh báo runtime — chỉ khi `isTestMode == true`.
- Đừng commit `google-services.json` thật của project khác. File trong `app/` ở đây chỉ phục vụ demo.
- Module SDK nhắm `minSdk 21` để giữ tính tương thích rộng — đừng thống nhất với `minSdk 23` của `:app` nếu chưa có lý do.

---

## Đóng góp

- Khi đổi public API surface của `:firebase-events`, **bump `firebase-events/VERSION`** theo SemVer (xem mục "Versioning" trong [`firebase-events/README.md`](firebase-events/README.md)).
- Khi đổi behavior, cập nhật doc tương ứng trong `firebase-events/docs/` **trong cùng commit** — đó là source of truth của contract.
- Hướng dẫn dành riêng cho Claude Code: [`CLAUDE.md`](CLAUDE.md).

---

## License

Apache 2.0 — xem [`firebase-events/LICENSE`](firebase-events/LICENSE).
