# FirebaseEventFramework

Kho upstream / reference cho thư viện **`firebase-events`** — một lớp tracking sự kiện Firebase Analytics + Crashlytics dạng *drop-in* cho Android, kèm app demo tối thiểu để smoke-test.

> Mục đích chính của repo: phát triển và bảo trì SDK ở đây, sau đó **copy-paste** module `firebase-events/` sang các project Android khác. App `:app` không phải sản phẩm — chỉ là host để chạy thử SDK.

---

## Cấu trúc repo

| Module | Vai trò | Đặc điểm |
|---|---|---|
| [`firebase-events/`](firebase-events/) | Thư viện SDK (public, có SemVer) | `minSdk 21`, `compileSdk 35`, JDK 17. Không phụ thuộc module nội bộ nào — copy-paste sang repo khác là chạy. |
| [`app/`](app/) | Demo / smoke-test (`com.example.firebaseeventframework`) | `minSdk 23`, JDK 11, Jetpack Compose. Chỉ minh hoạ pattern tích hợp, không tái sử dụng. |

> Đừng đặt abstraction dùng chung vào `:app`. Đừng thêm `project(":...")` dependency vào `:firebase-events` (sẽ phá tính copy-paste).

---

## Quick start

Yêu cầu: JDK 17, Android SDK 35, Gradle Wrapper (đã có trong repo).

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

1. Copy thư mục `firebase-events/` sang repo đích (đặt cạnh `:app`).
2. Thêm `include(":firebase-events")` vào `settings.gradle.kts` của repo đó.
3. `implementation(project(":firebase-events"))` trong `app/build.gradle.kts`.
4. Khởi tạo trong `Application.onCreate` (xem `app/.../DemoApp.kt` ở repo này để có mẫu).

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

> Lưu ý quan trọng khi debug: hai code path đi vào Firebase trong `AnalyticsEvents.flushEvents` và `AnalyticsUserProperties.logUserPropertyEv` hiện đang **bị comment** (đánh dấu `// Radar ko log event & properties`). Nếu DebugView không thấy event/user property dù mọi thứ wiring đúng — gần như chắc chắn đây là lý do. Xác nhận với chủ project trước khi enable lại.

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
