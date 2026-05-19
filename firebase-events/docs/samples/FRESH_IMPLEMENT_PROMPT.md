# Prompt kích hoạt — Tích hợp `firebase-events` từ con số 0

> Copy block dưới đây và paste vào Claude Code (hoặc Cursor) ở **thư
> mục root** của project Android đang **chưa từng** tích hợp module
> `firebase-events`.

---

## Prompt (paste nguyên block dưới)

````text
Bạn là một Android engineer giúp tôi tích hợp module `firebase-events`
vào 1 project Android HOÀN TOÀN MỚI:

- Project hiện CHƯA có module `firebase-events` (chưa copy vào).
- Project có thể CHƯA có `google-services.json` hoặc CHƯA có Firebase
  setup. Tôi đã tạo Firebase project trên Console và sẽ tự download
  `google-services.json` khi bạn nhắc.
- Project ĐÃ có `app/build.gradle.kts` + `settings.gradle.kts` +
  `gradle/libs.versions.toml` (file version catalog có thể chưa đủ
  entries).
- Source module gốc tôi đã có sẵn ở đường dẫn local: <USER_FILL_IN>
  (tôi sẽ điền khi bạn hỏi).

NHIỆM VỤ
Đọc file `firebase-events/docs/FRESH_PROJECT_GUIDE.md` ở source
module gốc (đường dẫn tôi sẽ cung cấp) và thực thi 5 phase A → E
trong đó. Tuân thủ tuyệt đối phần "Anti-patterns" và "FAQ" cuối tài
liệu.

YÊU CẦU CỤ THỂ
1. Khảo sát trước khi sửa gì:
   - Hỏi tôi đường dẫn source `firebase-events/` để bạn `cp -R`.
   - Đọc `FRESH_PROJECT_GUIDE.md` từ source đó.
   - Liệt kê trạng thái hiện tại của project:
       * `applicationId` từ `app/build.gradle.kts`.
       * Đã có file `google-services.json` ở đâu chưa?
       * `compileSdk` / `minSdk` hiện tại — cảnh báo nếu
         `compileSdk < 36` hoặc `minSdk < 21`.
       * `libs.versions.toml` đã có entries nào trong bảng C2 của
         guide, thiếu entries nào.
       * Đã có Application class chưa, package name là gì.
       * Đã apply plugin `googleServices` / `firebaseCrashlytics`
         ở root + app chưa.

2. Hỏi tôi đúng 4 câu (không hơn) trước khi sinh code:
   a) Đường dẫn source `firebase-events/` để copy?
   b) `applicationId` cuối cùng tôi muốn dùng (xác nhận khớp
      với Firebase Android app đã đăng ký).
   c) Project có flavor không? Liệt kê các flavor đang có (nếu có)
      để bạn đặt `google-services.json` đúng vị trí.
   d) Tên 3–5 screen chính để bạn dựng catalog `ScreenName`.

3. Sau khi tôi trả lời, thực hiện theo thứ tự:
   - **Phase B**: `cp -R` thư mục `firebase-events/` vào project,
     giữ nguyên file `VERSION`.
   - **Phase C1**: thêm `include(":firebase-events")` vào
     `settings.gradle.kts` (đặt sau `include(":app")` nếu chưa có).
   - **Phase C2**: chỉ thêm các entry CÒN THIẾU vào
     `libs.versions.toml`. KHÔNG ghi đè version đã có; KHÔNG xoá
     entry không liên quan; KHÔNG nhân đôi. Trước khi sửa, in danh
     sách entries sẽ thêm để tôi confirm.
   - **Phase C3**: thêm `alias(libs.plugins.googleServices) apply
     false` và `alias(libs.plugins.firebaseCrashlytics) apply false`
     vào root `build.gradle.kts` nếu chưa có.
   - **Phase C4**: apply 2 plugin trên + thêm
     `implementation(project(":firebase-events"))` trong
     `app/build.gradle.kts`. Bổ sung permission `INTERNET` /
     `ACCESS_NETWORK_STATE` vào manifest nếu chưa có.
   - **Phase D**: tạo `Application` class, catalog `event/`,
     `AnalyticsEventsUtils`, `BaseTrackedActivity`, áp dụng cho
     ít nhất 1 Activity hiện có, thêm consent skeleton.
   - **Phase E (text only)**: in cho tôi 2 lệnh adb để verify
     (DebugView + Logcat).

4. RÀNG BUỘC TUYỆT ĐỐI
   - KHÔNG sửa file nào trong thư mục `firebase-events/` sau khi
     copy — đó là SDK đã đóng.
   - KHÔNG ghi đè version đã có trong `libs.versions.toml`. Nếu
     version cũ < version guide đề xuất, BÁO tôi để tôi tự quyết.
   - KHÔNG commit `google-services.json` vào git — kiểm tra
     `.gitignore` đã ignore chưa, nếu chưa thì append.
   - KHÔNG gọi `AnalyticsEvents.logXxx` trực tiếp từ UI — luôn qua
     `AnalyticsEventsUtils`.
   - KHÔNG hard-code screen/button string ở call-site — luôn qua
     constants.
   - KHÔNG tự chạy `./gradlew assemble` mà không hỏi tôi (build có
     thể tốn vài phút).
   - KHÔNG tự `git commit` / `git push` / `git tag`.
   - Import phải dùng package thực tế của project, không copy
     nguyên `com.example.demo` trong guide.

5. SAU KHI XONG
   - In checklist 4 phase (A/B/C/D/E) với trạng thái ✅ / ⬜.
   - Liệt kê các file đã thay đổi, định dạng `path:line` nếu sửa
     file có sẵn.
   - Nhắc tôi 3 việc còn lại cần làm bằng tay:
       * Tải `google-services.json` từ Firebase Console.
       * Bật Crashlytics trong Firebase Console.
       * (Optional) tạo Remote Config key `event_configs`.
````

---

## Quy trình dùng

1. **Trước khi paste prompt**:
   - Đã tạo Firebase project + Android app trên
     [Firebase Console](https://console.firebase.google.com/).
   - Đã có sẵn source code module `firebase-events/` ở 1 đường dẫn
     local (ví dụ clone repo `toh-weather`).
   - Mở terminal/IDE tại **thư mục root** của project mới (project
     chưa tích hợp).

2. **Paste prompt → trả lời 4 câu hỏi của agent → confirm các thay
   đổi `libs.versions.toml` agent đề xuất → để agent generate code.**

3. **Việc còn lại bạn tự làm tay** (agent không thể thay):
   - Đặt `google-services.json` vào `app/google-services.json`
     (hoặc `app/src/<flavor>/google-services.json` nếu có flavor).
   - Vào Firebase Console → **Crashlytics** → **Enable**.
   - (Tuỳ chọn) Tạo Remote Config key `event_configs` để sau này
     toggle event mà không cần ship build.

4. **Build & verify**:
   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app:installDebug
   adb shell setprop debug.firebase.analytics.app <your.app.id>
   adb logcat -s AnalyticsEvents:V AnalyticsValidator:V
   ```
   Mở Firebase Console → **Analytics → DebugView**, phải thấy
   event xuất hiện trong < 30 giây sau khi ấn nút trong app.

---

## Biến thể prompt cho các tình huống khác

### Khi project KHÔNG dùng version catalog (`libs.versions.toml`)

Prepend block sau vào prompt chính:

```text
LƯU Ý GRADLE STYLE:
- Project KHÔNG dùng version catalog — `app/build.gradle.kts` đang
  khai báo dependency dạng "group:artifact:version" trực tiếp.
- Thay vì Phase C2 (sửa libs.versions.toml), bạn cần chỉnh
  `firebase-events/build.gradle.kts` thay tất cả `libs.xxx` thành
  coordinate trực tiếp. Trước khi sửa, in danh sách thay đổi để
  tôi confirm. Đây là exception duy nhất — sau đó SDK code vẫn
  không được sửa.
```

### Khi project đã có Firebase legacy nhưng chưa có analytics SDK chuẩn

```text
LƯU Ý LEGACY:
- Project đã có `google-services.json` từ trước, đã có
  Crashlytics, có thể đã gọi `FirebaseAnalytics.getInstance()`
  rải rác. KHÔNG xoá code legacy.
- Đọc và liệt kê các call site `FirebaseAnalytics.getInstance()`
  hiện có trước khi đề xuất migrate. Tôi sẽ quyết migrate từng
  cái sau.
```

### Khi project là KMM / multi-module phức tạp

```text
LƯU Ý MULTI-MODULE:
- Project là multi-module: `:app` depend trên `:feature-home`,
  `:feature-detail`, `:core-ui`, …
- Module `:firebase-events` chỉ cần `:app` depend trực tiếp.
- Các feature module nếu cần log thì depend trên 1 module wrapper
  mới (ví dụ `:analytics-wrapper`) chứa `AnalyticsEventsUtils`,
  để feature module KHÔNG biết đến SDK. Đề xuất layer này khi
  bạn liệt kê thay đổi.
```

### Khi bạn chỉ muốn agent setup Gradle, không sinh code Phase D

Bỏ điểm 3 từ "Phase D" trở đi, thay bằng:

```text
- Dừng sau Phase C5. KHÔNG tạo Application class, catalog,
  wrapper, BaseActivity. Tôi sẽ tự đọc DEMO_IMPLEMENTATION_GUIDE.md
  và viết phần code wiring sau.
```
