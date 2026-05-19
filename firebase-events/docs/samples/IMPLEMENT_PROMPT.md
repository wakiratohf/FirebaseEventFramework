# Prompt kích hoạt — Implement `firebase-events` cho demo project

> Copy nguyên block dưới đây và paste vào Claude Code (hoặc Cursor /
> bất kỳ agent nào hỗ trợ đọc file local) ở **thư mục root của demo
> project** đã include sẵn module `firebase-events`.

---

## Prompt (paste nguyên block dưới)

````text
Bạn là một Android engineer hỗ trợ tôi tích hợp tracking events cho 1
demo Android project. Demo này:

- Đã include module `:firebase-events` trong `settings.gradle.kts`.
- `app/build.gradle.kts` đã có `implementation(project(":firebase-events"))`.
- Đã có `google-services.json` đúng cho từng flavor.
- Đã apply plugin `com.google.gms.google-services` và `com.google.firebase.crashlytics`.

NHIỆM VỤ
Đọc file `firebase-events/docs/samples/DEMO_IMPLEMENTATION_GUIDE.md` trong
project này và thực thi 10 bước trong đó để wire SDK vào demo app.
Tuyệt đối tuân thủ tài liệu — đặc biệt là phần "Anti-patterns cần
tránh" cuối file.

YÊU CẦU CỤ THỂ
1. Khảo sát trước:
   - Đọc `firebase-events/docs/samples/DEMO_IMPLEMENTATION_GUIDE.md` đầy đủ.
   - Đọc `firebase-events/docs/EVENT_CATALOG.md` để biết 13 event built-in.
   - Mở `app/src/main/AndroidManifest.xml` xem đã có class
     `Application` hay chưa, applicationId / package name là gì.
   - Liệt kê các Activity / Fragment hiện có trong demo (giới hạn 1
     mức depth, đừng đệ quy quá sâu).

2. Hỏi tôi đúng 3 câu (chỉ 3 câu, không hơn) trước khi sinh code:
   a) Application class — tạo mới `DemoApp` hay tái sử dụng class hiện
      có? Nếu có sẵn, đường dẫn?
   b) Tên các screen chính (≤ 5) để tôi xác định catalog `ScreenName`.
   c) Có cần wiring webhook (Bước 9) và Remote Config (Bước 10) trong
      lần đầu này không? (mặc định = Không, chỉ làm Bước 1-8.)

3. Sau khi tôi trả lời, thực hiện:
   - Tạo / sửa Application class (Bước 1).
   - Update AndroidManifest nếu cần.
   - Tạo package `event/` với `ScreenName.kt`, `ButtonName.kt`,
     `PopupName.kt`, `AnalyticsEventsUtils.kt` (Bước 2-3).
   - Tạo `BaseTrackedActivity` (Bước 4) — đặt trong package
     `ui/base/` (tạo nếu chưa có).
   - Cho ÍT NHẤT 1 Activity hiện có extends `BaseTrackedActivity` và
     log 1 click button thực tế trong Activity đó (Bước 5).
   - Thêm 1 `PullToRefreshEv` ví dụ implement `AnalyticsEvent`
     (Bước 6) — chưa cần wire vào UI, chỉ để làm mẫu.
   - Thêm consent toggle skeleton (Bước 7) — lưu vào SharedPreferences
     `consent`, key `analytics_consent`.

4. RÀNG BUỘC
   - Không sửa file nào trong thư mục `firebase-events/` — đó là SDK
     đã đóng.
   - Không gọi `AnalyticsEvents.logXxx` trực tiếp từ Activity /
     Fragment / ViewModel — luôn qua `AnalyticsEventsUtils`.
   - Không hard-code string screen/button ở call-site — luôn qua
     constants `ScreenName.X` / `ButtonName.X`.
   - Không thêm dependency mới vào `app/build.gradle.kts` (SDK đã đủ).
   - Mọi import phải dùng package thực tế của demo project, không
     copy nguyên `com.example.demo`.
   - Code Kotlin, tuân thủ style hiện có của project (4-space hoặc
     theo `.editorconfig` nếu có).

5. SAU KHI XONG
   - In ra checklist từ cuối tài liệu `DEMO_IMPLEMENTATION_GUIDE.md`
     với trạng thái ✅ / ⬜ tương ứng.
   - Hướng dẫn tôi 2 lệnh để verify:
       * Cách bật DebugView (`adb shell setprop ...`).
       * Logcat filter tag để xem dump.
   - KHÔNG tự chạy `./gradlew assemble`, KHÔNG tự commit / push git.
````

---

## Cách dùng

1. **Trước khi paste prompt**: kiểm tra trong `settings.gradle.kts` đã
   có `include(":firebase-events")`, trong `app/build.gradle.kts` đã
   có `implementation(project(":firebase-events"))`, và đã copy
   `google-services.json` đúng vị trí.
2. **Paste prompt** ở thư mục root project (`cd <demo-project>`).
3. Agent sẽ hỏi 3 câu → trả lời → agent generate code theo đúng 8-10
   bước trong [`DEMO_IMPLEMENTATION_GUIDE.md`](DEMO_IMPLEMENTATION_GUIDE.md).
4. Sau khi agent xong, build app:

   ```bash
   ./gradlew :app:assembleDebug
   ```

5. Bật DebugView và verify:

   ```bash
   adb shell setprop debug.firebase.analytics.app <your.app.id>
   ```

6. Mở Firebase Console → **Analytics → DebugView** để xem stream
   event.

---

## Biến thể prompt cho các tình huống khác

### Khi demo project chưa có Application class

Thêm vào câu hỏi 2a của prompt: yêu cầu agent tạo mới `DemoApp` và
register trong manifest.

### Khi demo project đã có analytics legacy (cần migrate)

Prepend block sau vào prompt:

```text
LƯU Ý MIGRATION:
- Project đã có sẵn code log analytics legacy ở <path/to/old/file>.
- Trước khi sinh code mới, đọc file đó để hiểu screen/button name
  đang dùng và GIỮ NGUYÊN naming cho `ScreenName` / `ButtonName`
  constants — tránh phá histogram cũ trên Firebase.
- Đánh dấu file cũ là `@Deprecated`, không xoá ngay.
```

### Khi muốn agent chỉ scaffolding, không sửa Activity

Bỏ điểm 3 con thứ 5 ("Cho ÍT NHẤT 1 Activity hiện có…") và thay bằng:

```text
- Chỉ tạo các file event/, base/, không sửa Activity hiện có. Tôi sẽ
  tự áp dụng `BaseTrackedActivity` cho từng screen sau.
```
