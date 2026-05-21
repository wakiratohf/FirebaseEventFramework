# Cơ chế log event hiển thị (display) cho Dialog

> **Template tái sử dụng** — Tài liệu mô tả ở mức pattern/kiến trúc để áp dụng cho bất kỳ dự án Android nào dùng Firebase Analytics. Code mẫu ở phần [§6](#6-code-mẫu-generic) đã tách rời khỏi class/package cụ thể, copy sang dự án khác là dùng được.

---

## 1. Mục đích & phạm vi

Tài liệu trả lời một câu hỏi: **"Làm sao ghi nhận một dialog/popup đã hiển thị, và hiển thị trong bao lâu?"**

- **Trong phạm vi**: `Dialog`, `MaterialDialog`, bottom sheet, popup overlay — bất kỳ thứ gì có 2 thời điểm `show` và `dismiss`.
- **Ngoài phạm vi** (không bàn ở đây): screen tracking cho `Activity`/`Fragment`, event click nút trong dialog (`click_btn_ev`), quy ước đặt tên đầy đủ.

Ý tưởng cốt lõi: dialog không có lifecycle `onResume/onPause` như screen, nên ta gắn analytics vào 2 callback có sẵn của mọi `Dialog`: **`OnShowListener`** và **`OnDismissListener`**.

> **Xem thêm:** Tracking cho Activity/Fragment (state machine `overlap`/`home_recent`, `BaseActivityEv`, `BaseFragmentEv`) — xem [`SCREEN_VIEW_GUIDE.md`](SCREEN_VIEW_GUIDE.md) (B2–B4).

---

## 2. Mô hình khái niệm: tách làm 2 thời điểm

Đây là điểm dễ hiểu nhầm nhất. "Log hiển thị dialog" **không phải** một event bắn lúc dialog xuất hiện. Nó là **2 hành động ở 2 thời điểm khác nhau**:

| Thời điểm | Hành động Analytics | Bản chất | Mục đích phân tích |
|---|---|---|---|
| **Lúc dialog show** (`onShow`) | Set **User Property** `screen_open` = `sr_<screenName>[_<popupName>]` | Thuộc tính người dùng (trạng thái) | Biết user "đã từng thấy" dialog nào → audience/segment |
| **Lúc dialog đóng** (`onClosed`/`onDismiss`) | Bắn **Event** `screen_view_ev` kèm `duration` (giây) | Sự kiện (hành vi) | Đo user xem dialog **bao lâu** → mức độ tương tác |

**Vì sao bắn event lúc đóng chứ không phải lúc mở?** Vì chỉ khi đóng ta mới biết `duration`. `duration` được tính tại thời điểm dismiss:

```
duration = (elapsedRealtime_lúc_đóng - elapsedRealtime_lúc_mở) / 1000
```

> Dùng `SystemClock.elapsedRealtime()` chứ không dùng `System.currentTimeMillis()` để miễn nhiễm với việc người dùng chỉnh đồng hồ hệ thống.

---

## 3. Các thành phần kiến trúc

```
┌─────────────────────┐   onShow()/onClosed()   ┌────────────────────────┐
│  Dialog (UI layer)  │ ──────────────────────► │  DialogScreenViewEv     │  ← helper gắn vào dialog
└─────────────────────┘                         └───────────┬────────────┘
                                                            │
                              ┌─────────────────────────────┼──────────────────────────────┐
                              ▼ (lúc show)                                                   ▼ (lúc đóng)
                  ┌────────────────────────────┐                          ┌─────────────────────────────────┐
                  │ User Property: screen_open  │                          │ Event: screen_view_ev           │
                  │ value = sr_<screen>[_popup] │                          │ params: screen_name,            │
                  └────────────────────────────┘                          │         screen_state, duration  │
                                                                           └────────────────┬────────────────┘
                                                                                            ▼
                                                                            FirebaseAnalytics.logEvent(...)
                                                                            (qua kill-switch + per-event toggle)
```

### 3.1. `DialogScreenViewEv` — helper gắn vào dialog
Class nhỏ giữ state của một lần hiển thị dialog. Trách nhiệm duy nhất: nhận `onShow()` / `onClosed()` từ dialog, tính `duration`, gọi xuống tầng analytics. Đây là **pattern khuyến nghị tái sử dụng**.
- Field: `openTimestamp` (mốc thời gian mở), `onClosed` (cờ chống set property trùng).
- Input khởi tạo: `screenName` (màn hình chứa dialog) + `popupName` (tên dialog).

### 3.2. Model event `screen_view_ev` (`_ScreenViewEv`)
Đối tượng dữ liệu → Bundle gửi Firebase.

| Field | Kiểu | Ghi chú |
|---|---|---|
| `screenName` | String | Tên màn hình |
| `screenState` | enum `OVERLAP` / `STOP` / `HOME_RECENT` | **Dialog luôn dùng `STOP`** (đóng hẳn) |
| `popupName` | String | Tên dialog/popup, rỗng nếu không có |
| `duration` | Int (giây) | Thời lượng hiển thị |

`toBundle()` xuất ra đúng 3 key Firebase:
```
screen_name  = "<ScreenName>_<PopupName>"   // ghép + chuẩn hoá camelCase
screen_state = "stop"                        // enum.lowercase()
duration     = <Int>
```
Tên màn hình được ghép `screenName_[popupName]` rồi đưa qua `convertSnakeCaseToCamelCase()` (vd `daily_news` + `popup_rate` → `DailyNews_Rate`).

### 3.3. User Property `screen_open`
Đặt qua `AnalyticsUserProperties.logEventScreenOpen(screenName, popupName)`. Value có prefix cố định `sr_`:
```
sr_<screenName>            // nếu không có popup
sr_<screenName>_<popupName> // nếu là dialog
```
> Lưu ý: phần User Property **không** đi qua `convertSnakeCaseToCamelCase`, giữ nguyên chuỗi gốc + prefix `sr_`.

### 3.4. Tầng gửi & công tắc
`AnalyticsEvents.logScreenViewEv(...)` → `FirebaseAnalytics.logEvent("screen_view_ev", bundle)`. Trước khi gửi đi qua:
- **Kill-switch tổng**: `AnalyticsModule.isEnabled` (tắt toàn bộ — phục vụ consent GDPR/CCPA).
- **Toggle theo từng event**: `EventConfigs.screenViewEvEnable`.

---

## 4. Luồng chạy (sequence)

### Khi dialog hiển thị
```
Dialog.show()
   └─► OnShowListener
          └─► DialogScreenViewEv.onShow()
                 ├─ openTimestamp = elapsedRealtime()
                 └─ nếu (onClosed == true && screenName != null):
                        AnalyticsUserProperties.logEventScreenOpen(screenName, popupName)
                        // → set User Property screen_open = sr_<screen>_<popup>
                 └─ onClosed = false
```

### Khi dialog đóng
```
Dialog.dismiss()
   └─► OnDismissListener
          └─► DialogScreenViewEv.onClosed()
                 ├─ nếu openTimestamp == 0L → return (chưa từng show, bỏ qua)
                 ├─ onClosed = true
                 ├─ deltaTime = (elapsedRealtime() - openTimestamp) / 1000
                 └─ nếu deltaTime > 0:
                        AnalyticsEvents.logScreenViewEv(
                            _ScreenViewEv(screenName, state = STOP, popupName, duration = deltaTime)
                        )
                        // → bắn Event screen_view_ev
                 └─ openTimestamp = 0
```

---

## 5. Cách tích hợp vào dialog (3 pattern)

Tuỳ cách dialog được tạo, chọn 1 trong 3 cách gắn hook. Cả 3 đều quy về việc gọi `onShow()` / `onClosed()` của helper.

### Pattern A — Builder tự gắn listener *(ít việc nhất cho dev)*
Bọc trong một `Dialog.Builder` chung: trong `init {}` tự `setOnShowListener`/`setOnDismissListener`, override để gọi helper. Mọi dialog tạo qua builder này được tracking tự động, dev không phải làm gì thêm.
```kotlin
class TrackedDialogBuilder(ctx: Context, screenName: String?, popupName: String?) : SomeDialog.Builder(ctx) {
    private val ev = if (screenName.isNullOrEmpty() || popupName.isNullOrEmpty()) null
                     else DialogScreenViewEv(screenName, popupName)
    init {
        setOnShowListener { ev?.onShow() }
        setOnDismissListener { ev?.onClosed() }
    }
}
```

### Pattern B — Base wrapper
Dialog kế thừa một base class nhận sẵn `screenName`, gọi `super.onShow()` thủ công ngay sau `dialog.show()`.
```kotlin
open class BaseTrackedDialog(screenName: String) {
    private val ev = DialogScreenViewEv(screenName, popupName = "")
    protected fun onShow() = ev.onShow()
    protected fun onClosed() = ev.onClosed()
}

class MyDialog : BaseTrackedDialog("home") {
    fun show() { dialog.show(); onShow() }   // gọi tay khi show
}
```

### Pattern C — Gắn trực tiếp listener
Dùng khi dialog tạo lẻ, không qua builder/base:
```kotlin
private val ev by lazy { DialogScreenViewEv("lock", "popupWeather") }
dialog.setOnShowListener    { ev.onShow() }
dialog.setOnDismissListener { ev.onClosed() }
```

---

## 6. Code mẫu generic

Phiên bản rút gọn, **không phụ thuộc** package/class của dự án gốc — chỉ cần thay 2 lời gọi xuống tầng analytics của bạn (đánh dấu `// TODO`).

```kotlin
import android.os.SystemClock

/**
 * Theo dõi một lần hiển thị dialog/popup.
 * - onShow():   ghi mốc thời gian + set User Property "đã thấy dialog".
 * - onClosed(): tính thời lượng hiển thị + bắn event screen_view_ev (chỉ khi > 0 giây).
 *
 * Mỗi lần show/dismiss tái sử dụng được cùng 1 instance.
 */
class DialogScreenViewEv(
    private val screenName: String?,
    private val popupName: String,
) {
    private var openTimestamp = 0L
    private var closed = true   // chống set property trùng khi onShow gọi nhiều lần

    fun onShow() {
        openTimestamp = SystemClock.elapsedRealtime()
        if (closed && !screenName.isNullOrEmpty()) {
            // TODO: set User Property -> "sr_<screenName>[_<popupName>]"
            Analytics.setScreenOpenProperty(screenName, popupName)
        }
        closed = false
    }

    fun onClosed() {
        if (openTimestamp == 0L) return          // chưa từng show -> bỏ qua
        closed = true
        val name = screenName ?: return
        val durationSec = ((SystemClock.elapsedRealtime() - openTimestamp) / 1000).toInt()
        openTimestamp = 0L
        if (durationSec > 0) {                   // tránh nhiễu khi mở/đóng tức thì
            // TODO: bắn event "screen_view_ev" với params: screen_name, screen_state="stop", duration
            Analytics.logScreenViewEv(screenName = name, popupName = popupName, durationSec = durationSec)
        }
    }
}
```

Helper tầng analytics (tham khảo — phần ghép tên + Bundle):
```kotlin
object Analytics {
    fun setScreenOpenProperty(screenName: String, popupName: String) {
        val suffix = if (popupName.isNotEmpty()) "_$popupName" else ""
        firebaseAnalytics.setUserProperty("screen_open", "sr_$screenName$suffix")
    }

    fun logScreenViewEv(screenName: String, popupName: String, durationSec: Int) {
        val name = listOf(screenName, popupName).filter { it.isNotEmpty() }
            .joinToString("_") { it.toCamelCase() }   // tự chuẩn hoá camelCase
        firebaseAnalytics.logEvent("screen_view_ev", bundleOf(
            "screen_name"  to name,
            "screen_state" to "stop",
            "duration"     to durationSec,
        ))
    }
}
```

### Checklist tích hợp
- [ ] Tạo 1 instance `DialogScreenViewEv(screenName, popupName)` cho mỗi dialog.
- [ ] Gọi `onShow()` từ `OnShowListener` (hoặc ngay sau `dialog.show()`).
- [ ] Gọi `onClosed()` từ `OnDismissListener`.
- [ ] Cài 2 lời gọi `// TODO` xuống Firebase: set User Property `screen_open` + log event `screen_view_ev`.
- [ ] Đảm bảo tên event/param tuân thủ giới hạn Firebase (xem §7).

---

## 7. Quyết định & lưu ý quan trọng

- **Event chỉ bắn khi `duration > 0`.** User mở rồi đóng tức thì (vd auto-dismiss) sẽ không tạo nhiễu dữ liệu.
- **Cờ `closed`/`onClosed` chống set User Property trùng** khi `onShow()` bị gọi nhiều lần liên tiếp (vd dialog re-show) mà chưa qua `onClosed()`.
- **Dialog luôn dùng `screen_state = STOP`** (đóng hẳn). Ba trạng thái `OVERLAP`/`HOME_RECENT`/`STOP` là dành cho screen-level tracking — dialog không cần phân biệt.
- **`popupName` để phân biệt nhiều dialog trên cùng 1 screen.** Cùng `screenName = "home"` nhưng `popupName` khác nhau → tên gộp khác nhau (`Home_Rate`, `Home_Permission`...).
- **Giới hạn Firebase** (validate ở debug/test mode): event name ≤ 40 ký tự, khớp `^[A-Za-z][A-Za-z0-9_]*$`; param key ≤ 40; param value (string) ≤ 100.
- **Hai lớp tắt/bật** phục vụ consent: kill-switch tổng (`isEnabled`) + toggle theo từng loại event (`EventConfigs`). Khi `isEnabled = false`, không gửi gì cả.
- **Dùng `SystemClock.elapsedRealtime()`** thay vì wall-clock để `duration` không bị sai khi user đổi giờ hệ thống.

---

## 8. Tham chiếu (reference implementation trong toh-weather)

| Thành phần | File |
|---|---|
| Helper dialog | `app/.../event/DialogScreenViewEv.kt` |
| Helper screen tổng quát | `app/.../event/ScreenViewEventHelper.kt` |
| Model event | `firebase-events/.../models/_ScreenViewEv.kt` |
| User Property `screen_open` | `firebase-events/.../AnalyticsUserProperties.kt` |
| Pattern A (builder) | `app/.../ui/base/dialog/BaseMaterialDialogBuilder.kt` |
| Pattern B (base wrapper) | `app/.../ui/dialogs/BaseDialogWrapperEv.kt`, `DailyWeatherNewsDialog.kt` |
| Pattern C (listener trực tiếp) | `app/.../ui/dialogs/LockScreenDialog.kt` |
