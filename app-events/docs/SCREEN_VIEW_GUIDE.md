# screen_view_ev + click_btn_ev — Integration playbook

> Playbook để **Claude Code triển khai screen tracking sang một app Android khác**.
> Đọc hết file này rồi làm theo phần **"Các bước triển khai"**. Cuối file có sẵn
> một **prompt mẫu** để paste vào Claude Code ở app mới.

Theo dõi **user vào màn hình nào, ở bao lâu** (`screen_view_ev`), **bấm nút nào**
(`click_btn_ev`), và set user property **`screen_open`** mỗi lần mở màn hình.

---

## Khác biệt với 3 guide còn lại của module này

`open_app_from`, `time_open_app`, `app_exit` được đóng gói **trong `:app-event`** vì
chúng chỉ phụ thuộc vòng đời *process*. Screen tracking thì khác: nó bám vào vòng đời
**UI của từng app** (base Activity/Fragment, dialog stack, ViewPager tab…), nên
**không** nằm trong module này. Theo đúng quy tắc ở `README.md` ("project-specific screen
name catalog belongs in `:app`"), bạn sẽ **tái tạo các lớp tích hợp ngay trong `:app`**
của app đích — playbook này cung cấp source để copy-paste + chỗ cần chỉnh.

```
:firebase-events  → log layer dùng lại nguyên (KHÔNG sửa)
   AnalyticsEvents.logScreenViewEv / logClickBtnEv
   AnalyticsUserProperties.logEventScreenOpen
   models/_ScreenViewEv, models/_ClickBtnEv
        ▲
        │ gọi vào
:app (app đích)  → TÁI TẠO theo playbook này
   ScreenViewEventHelper   ← state machine + đo thời gian
   BaseActivityEv / BaseFragmentEv ← hook lifecycle
   BaseActivity (popup stack hooks)
   ScreenName / PopupName  ← catalog hằng số của riêng app
```

> ℹ️ **Bản demo trong repo này (`:app`) chỉ là subset Compose, KHÔNG phải bản đầy đủ.**
> [`ui/base/BaseTrackedActivity.kt`](../../app/src/main/java/com/example/firebaseeventframework/ui/base/BaseTrackedActivity.kt)
> +[`event/AnalyticsEventsUtils.kt`](../../app/src/main/java/com/example/firebaseeventframework/event/AnalyticsEventsUtils.kt)
> minh hoạ đúng **nguyên tắc cốt lõi** (mở → set property `screen_open`; đóng → fire
> `screen_view_ev` kèm `duration`) và `click_btn_ev`, nhưng **lược bỏ** có chủ đích:
> state machine `home_recent`/`overlap` (luôn dùng `STOP`), dialog-as-screen tracking
> (`BaseDialogEv`/`DialogScreenViewEv`), và `BaseFragmentEv`/tab tracking — vì `:app` là
> host smoke-test thuần Compose (không Fragment, không dialog stack). Demo hook `onPause`
> thay vì `onStop` nên không cần state `overlap`. Dùng playbook đầy đủ bên dưới cho app
> production có Fragment/dialog; đừng coi `BaseTrackedActivity` là bản tham chiếu hoàn chỉnh.

---

## Những gì được log

### Event `screen_view_ev` — fire khi **RỜI/ĐÓNG** màn hình

| Param | Kiểu | Ví dụ | Ghi chú |
|---|---|---|---|
| `screen_name` | string | `home`, `home_selectLocation` | camelCase; nếu có popup thì `screen_popup` |
| `screen_state` | string | `stop` / `home_recent` / `overlap` | xem bảng dưới |
| `duration` | int | `45` | số giây user ở lại màn hình |

| `screen_state` | Nghĩa |
|---|---|
| `stop` | thoát hẳn màn hình (`isRemoving`/`isDetached`/`isFinishing`) |
| `home_recent` | app xuống background (Home/Recent) — xác định sau `delay(500ms)` |
| `overlap` | bị màn hình/dialog khác đè lên nhưng app vẫn foreground |

### Event `click_btn_ev` — fire khi user bấm nút

| Param | Kiểu | Ví dụ | Ghi chú |
|---|---|---|---|
| `click_btn_ev_name` | string | `home_selectLocation_addLocation` | `screen_[popup_]button` (lowerCamelCase) |
| `click_btn_ev_time` | int | `120` | số giây tính từ lúc mở app |

### User property `screen_open` — set khi **MỞ** màn hình

`screen_open = "sr_<screen>[_<popup>]"`, ví dụ `sr_home`, `sr_home_selectLocation`.

> **Nguyên tắc cốt lõi:** event `screen_view_ev` **chỉ fire lúc rời màn hình** (để biết
> `duration`). Lúc *mở* màn hình chỉ set **user property** `screen_open`, **không** fire event.

> **Dialog cũng phát `screen_view_ev`.** Mỗi dialog/popup được tính là **một screen view
> riêng**: `screen_name = <screenCha>_<popup>` (vd `home_rate`), `screen_state` **luôn
> `stop`**, `duration` = số giây dialog mở. Xem section **"Dialog tracking"** bên dưới.

---

## Prerequisites (app đích phải có sẵn)

1. **Module `:firebase-events`** đã add và đã gọi `AnalyticsModule.init(...)`
   (xem `TIME_OPEN_APP_GUIDE.md` / `APP_EXIT_GUIDE.md` để biết cách init). Tài liệu này
   chỉ dùng lại 3 API: `AnalyticsEvents.logScreenViewEv`, `AnalyticsEvents.logClickBtnEv`,
   `AnalyticsUserProperties.logEventScreenOpen` — đều đã có sẵn trong module đó.
2. App có **một `BaseActivity` chung** (để gắn dialog stack) và **một `BaseFragment` chung**.
3. Có cách biết **app đang ở background hay không** (dùng cho state `home_recent`/`overlap`).
   Trong `toh-weather` là `BaseApplication.isAppInBackground()`. App khác có thể dùng
   `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)`.

---

## Các bước triển khai

### B1 — Thêm dialog/popup stack vào `BaseActivity`

Để event biết màn hình hiện có popup nào, `BaseActivity` giữ một stack các dialog đang hiện.
Thêm vào `BaseActivity` của app đích:

```kotlin
// data class dùng chung cho screen + popup
data class PopupViewEv(val screenName: String, val popupName: String)

// trong BaseActivity:
private val mShowingDialog: LinkedHashMap<PopupViewEv, BaseDialogEv?> = linkedMapOf()

fun onDialogShowing(popupViewEv: PopupViewEv, dialog: BaseDialogEv? = null) {
    if (popupViewEv.screenName.isEmpty() || popupViewEv.popupName.isEmpty()) return
    mShowingDialog[popupViewEv] = dialog
}

fun onDialogDismiss(popupViewEv: PopupViewEv) {
    mShowingDialog.remove(popupViewEv)
}

fun getShowingPopup(): PopupViewEv? = mShowingDialog.keys.lastOrNull()
fun getShowingPopupName(): String? = mShowingDialog.keys.lastOrNull()?.popupName
```

> Nếu app đích không có kiểu `BaseDialogEv`, đổi value type của map thành `Any?` hoặc
> bỏ value (dùng `LinkedHashSet<PopupViewEv>`). Cũng thêm `getShowingPopupName()` tương tự
> vào `BaseFragment` (trả về của activity host) để fragment lấy được popup đang hiện.

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/ui/base/BaseActivity.kt` (đoạn `mShowingDialog`).

### B2 — Tạo `ScreenViewEventHelper.kt`

Đây là **state machine + đo thời gian**. Copy nguyên (đổi import cho khớp app đích):

```kotlin
package <your.pkg>.event

import android.os.SystemClock
import androidx.fragment.app.Fragment
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.AnalyticsUserProperties
import com.tohsoft.firebase_events.models._ScreenViewEv
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// import BaseApplication, BaseActivity của app đích

class ScreenViewEventHelper(private val screenName: String, val activity: BaseActivity? = null) {
    private var openTimestamp = 0L
    private var state: _ScreenViewEv.State = _ScreenViewEv.State.STOP

    fun getOpenTimestamp(): Long = openTimestamp

    fun onShow(popupName: String? = null) {
        if (openTimestamp == 0L) {
            logScreenViewProperty(popupName)   // set user property "screen_open"
        }
        openTimestamp = SystemClock.elapsedRealtime()
    }

    companion object {
        fun logScreenOpen(screenName: String, popupName: String?) {
            if (screenName.isEmpty()) return
            AnalyticsUserProperties.logEventScreenOpen(screenName, popupName ?: "")
            // (toh-weather còn gọi AppExitTracker.setLastActiveScreen(screenName) ở đây
            //  để liên kết với event app_exit — thêm nếu app đích cũng dùng app_exit)
        }
    }

    fun logScreenViewProperty(popupName: String? = null) = logScreenOpen(screenName, popupName)

    @OptIn(DelicateCoroutinesApi::class)
    private fun onScreenStop(checkDialogTag: Boolean? = null, deltaTime: Long) {
        GlobalScope.launch {
            delay(500)
            state = if (BaseApplication.instance?.isAppInBackground() == true) {
                _ScreenViewEv.State.HOME_RECENT
            } else {
                _ScreenViewEv.State.OVERLAP
            }
            logEvent(deltaTime, checkDialogTag ?: false)
        }
    }

    @JvmOverloads
    fun onScreenClosed(fragment: Fragment? = null) {
        if (openTimestamp == 0L) return
        val deltaTime = ((SystemClock.elapsedRealtime() - openTimestamp) / 1000)
        openTimestamp = 0L
        if (fragment != null) {
            fragment.apply {
                if (isRemoving || isDetached || activity?.isFinishing == true) {
                    state = _ScreenViewEv.State.STOP
                    logEvent(deltaTime, true)
                } else {
                    onScreenStop(true, deltaTime)
                }
            }
        } else {
            onScreenClosed(deltaTime, null)
        }
    }

    @JvmOverloads
    fun onScreenClosed(deltaTime: Long, fragment: Fragment? = null) { // deltaTime tính bằng giây
        fragment?.apply {
            if (isRemoving || isDetached || activity?.isFinishing == true) {
                state = _ScreenViewEv.State.STOP
                logEvent(deltaTime)
            } else {
                onScreenStop(true, deltaTime)
            }
        } ?: let {
            state = if (activity == null || activity.isFinishing) _ScreenViewEv.State.STOP
                    else _ScreenViewEv.State.HOME_RECENT
            logEvent(deltaTime)
        }
    }

    private fun logEvent(duration: Long, checkShowingDialog: Boolean = false) {
        if (screenName.isEmpty()) return
        var popupName = ""
        if (checkShowingDialog) {
            popupName = activity?.getShowingPopup()?.takeIf { it.screenName == screenName }?.popupName ?: ""
        }
        AnalyticsEvents.logScreenViewEv(
            _ScreenViewEv(
                screenName = screenName,
                screenState = state,
                popupName = popupName,
                duration = duration.toInt()
            )
        )
    }
}
```

**2 dependency cần map sang app đích:**
- `BaseApplication.instance?.isAppInBackground()` → cách app đích kiểm tra background.
- `activity?.getShowingPopup()` → đã thêm ở **B1**.

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/event/ScreenViewEventHelper.kt`.

### B3 — Tạo `BaseActivityEv.kt`

Hook lifecycle cho **Activity**. Mọi Activity muốn track sẽ extend lớp này và override `getScreenName()`:

```kotlin
abstract class BaseActivityEv : BaseActivity() {
    private var mScreenViewEventHelper: ScreenViewEventHelper? = null

    open fun getScreenName(): String = ""   // BẮT BUỘC override ở Activity con

    open fun getScreenViewEvHelper(): ScreenViewEventHelper {
        if (mScreenViewEventHelper == null) {
            mScreenViewEventHelper = ScreenViewEventHelper(getScreenName(), this)
        }
        return mScreenViewEventHelper!!
    }

    override fun onResume() {
        super.onResume()
        getScreenViewEvHelper().onShow(getShowingPopupName())
    }

    override fun onStop() {
        super.onStop()
        logScreenViewEv()
    }

    open fun logScreenViewEv() {
        // GUARD tùy chọn: bỏ nếu app đích không có "welcome screen".
        // val prefs = ApplicationModules.instant.getPreferencesHelper(this)
        // if (prefs.mustShowWelcomeScreen()) return
        getScreenViewEvHelper().onScreenClosed()
    }
}
```

> Đoạn `mustShowWelcomeScreen()` trong `toh-weather` để **không** log khi app đang bắt user
> qua màn hình welcome. App đích không có khái niệm này thì **bỏ guard** đi.

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/ui/base/BaseActivityEv.kt`.

### B4 — Tạo `BaseFragmentEv.kt`

Fragment có **2 cơ chế**, chọn theo loại fragment:

**(a) Fragment thường** — vào/ra qua `onResume`/`onStop` như Activity. Cơ chế giống B3.

**(b) Fragment dạng tab (ViewPager)** — `onResume`/`onStop` **không** phản ánh việc user
chuyển tab, nên dùng cờ `mScreenState` + tự đo `activeTimestamp`/`deltaTimeInMs`, và gọi
`setFragmentVisible(true/false)` từ adapter/`OnPageChangeCallback`.

```kotlin
abstract class BaseFragmentEv : BaseFragment() {
    private var activeTimestamp = 0L
    private var deltaTimeInMs = 0L
    private var mScreenState: ScreenState = ScreenState.NONE  // NONE/INITIALIZING/VISIBLE/HIDDEN
    private var mScreenViewEventHelper: ScreenViewEventHelper? = null

    open fun getScreenName(): String = ""

    open fun getScreenViewEvHelper(): ScreenViewEventHelper {
        if (mScreenViewEventHelper == null)
            mScreenViewEventHelper = ScreenViewEventHelper(getScreenName(), activity as? BaseActivity)
        return mScreenViewEventHelper!!
    }

    protected fun initializingTab() { mScreenState = ScreenState.INITIALIZING }

    /** Chỉ gọi cho fragment dạng tab. Gọi true khi tab hiện, false khi tab ẩn. */
    open fun setFragmentVisible(fragmentVisible: Boolean) {
        if (fragmentVisible && hasOverlayFragment()) return
        if (fragmentVisible && mScreenState != ScreenState.VISIBLE) {
            ScreenViewEventHelper.logScreenOpen(getScreenName(), getShowingPopupName())
        }
        mScreenState = if (fragmentVisible) ScreenState.VISIBLE else ScreenState.HIDDEN
        setActiveTimestamp()
        if (mScreenState == ScreenState.HIDDEN && activeTimestamp > 0L) logScreenViewEv()
    }

    private fun setActiveTimestamp() {
        if (mScreenState == ScreenState.VISIBLE && activeTimestamp == 0L)
            activeTimestamp = SystemClock.elapsedRealtime()
    }
    private fun calculateDeltaTime() {
        if (activeTimestamp > 0) {
            deltaTimeInMs = SystemClock.elapsedRealtime() - activeTimestamp
            activeTimestamp = 0
        }
    }

    fun hasOverlayFragment(): Boolean {
        // true nếu có fragment khác đang nằm trên cùng back stack (đè lên fragment này)
        // (xem bản gốc: dùng FragmentUtils.getTopInStack so sánh simpleName)
        return false
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayFragment()) return
        if (mScreenState != ScreenState.NONE) {            // fragment tab
            setFragmentVisible(mScreenState == ScreenState.VISIBLE)
        } else if (getScreenName().isNotEmpty()) {         // fragment thường
            getScreenViewEvHelper().onShow(getShowingPopupName())
        }
    }

    override fun onStop() { super.onStop(); logScreenViewEv() }

    open fun logScreenViewEv() {
        // GUARD tùy chọn (welcome screen) — bỏ nếu không cần.
        calculateDeltaTime()
        if (mScreenState != ScreenState.NONE) {            // fragment tab → theo deltaTimeInMs
            if (mScreenState != ScreenState.INITIALIZING) mScreenState = ScreenState.HIDDEN
            if (deltaTimeInMs == 0L) return
            getScreenViewEvHelper().onScreenClosed(deltaTimeInMs / 1000, this)
            deltaTimeInMs = 0
        } else {                                           // fragment thường
            getScreenViewEvHelper().onScreenClosed(this)
        }
    }

    /** Helper log click button — gọi từ click listener của fragment. */
    fun logClickBtnEv(buttonName: String, popupName: String = "") {
        if (getScreenName().isEmpty()) return
        AnalyticsEvents.logClickBtnEv(
            _ClickBtnEv(
                screenName = getScreenName(),
                buttonName = buttonName,
                popupName = popupName.ifEmpty { getShowingPopupName().orEmpty() },
                time = /* số giây từ lúc mở app */ 0
            )
        )
    }
}
```

> `ScreenState` là enum `NONE, INITIALIZING, VISIBLE, HIDDEN`. `time` của `logClickBtnEv`
> là số giây từ lúc mở app — lấy từ session timestamp của app đích (trong `toh-weather`
> là `FirebaseEvents.getEvTime()`).

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/ui/base/BaseFragmentEv.kt`.

### B5 — Catalog `ScreenName` / `PopupName`

Tập trung mọi tên màn hình/popup vào một nơi (giá trị **camelCase**). Sinh theo các màn hình
thực tế của app đích, ví dụ:

```kotlin
object ScreenName {
    const val HOME_SCREEN = "home"
    const val HOURLY_SCREEN = "hourly"
    const val DAILY_SCREEN = "daily"
    const val MANAGE_LOCATION_SCREEN = "manageLocation"
    const val SETTINGS_SCREEN = "settings"
    // … thêm theo app
}

object PopupName {
    const val SELECT_LOCATION = "selectLocation"
    const val ENABLE_GPS_DIALOG = "enableGps"
    const val RATE_DIALOG = "Rate"
    // … thêm theo app
}
```

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/event/ScreenName.kt`.

---

## Dialog tracking — dialog cũng là một `screen_view_ev`

Mỗi dialog/popup tự fire **một** `screen_view_ev` riêng khi đóng:
`screen_name = <screenCha>_<popup>`, `screen_state = stop`, `duration` = thời gian dialog mở.
Có **hai đường bổ sung nhau** (không loại trừ):

| | Dialog **trong** Activity | Dialog **ngoài** Activity (service/overlay) |
|---|---|---|
| Lớp dùng | `BaseDialogEv` / builder + `DialogScreenViewEv` | `DialogScreenViewEv` standalone |
| Tự fire `screen_view_ev`? | ✅ khi `onDismiss` | ✅ khi `onClosed` |
| Đăng ký stack `mShowingDialog`? | ✅ (`onDialogShowing`/`onDialogDismiss`) | ❌ |
| Ví dụ | Rate, permission, chọn đơn vị… | `LockScreenDialog` (chạy từ service) |

> **Tại sao cần đăng ký stack?** Nếu user **rời màn hình trong khi dialog đang mở**, event
> của **screen cha** cũng được ghép `popupName` (qua `getShowingPopup()` ở
> `ScreenViewEventHelper.logEvent(checkShowingDialog=true)`). Nhờ vậy popup phản ánh ở cả
> event của chính nó lẫn event của màn hình nền.

### B6 — Tạo `DialogScreenViewEv.kt`

Wrapper tối giản: đo thời gian + tự fire `screen_view_ev` STOP. Copy nguyên:

```kotlin
package <your.pkg>.event

import android.os.SystemClock
import com.tohsoft.firebase_events.AnalyticsEvents
import com.tohsoft.firebase_events.models._ScreenViewEv

class DialogScreenViewEv(val screenName: String?, val popupName: String) {
    companion object {
        fun newInstance(screenName: String?, popupName: String) = DialogScreenViewEv(screenName, popupName)
    }

    private var openTimestamp = 0L
    private var onClosed = true

    fun onShow() {
        openTimestamp = SystemClock.elapsedRealtime()
        if (onClosed && !screenName.isNullOrEmpty()) {
            ScreenViewEventHelper.logScreenOpen(screenName, popupName) // set user property "screen_open"
        }
        onClosed = false
    }

    fun onClosed() {
        if (openTimestamp == 0L) return
        onClosed = true
        screenName?.let {
            val deltaTime = ((SystemClock.elapsedRealtime() - openTimestamp) / 1000)
            logScreenViewEndEv(it, popupName, deltaTime)
            openTimestamp = 0
        }
    }

    private fun logScreenViewEndEv(screenName: String, popupName: String = "", deltaTime: Long) {
        try {
            if (screenName.isEmpty() || deltaTime <= 0) return
            AnalyticsEvents.logScreenViewEv(
                _ScreenViewEv(
                    screenName = screenName,
                    screenState = _ScreenViewEv.State.STOP,
                    popupName = popupName,
                    duration = deltaTime.toInt()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

Cờ `onClosed` + guard `openTimestamp == 0L` chống fire trùng khi `onShow`/`onClosed` bị gọi lặp.

Nguồn đối chiếu: `app/src/main/java/com/tohsoft/weather/event/DialogScreenViewEv.kt`.

### B7 — Base dialog trong Activity (`BaseDialogEv`)

Mọi dialog hiển thị bên trong một Activity nên extend lớp này. Nó override `onShow`/`onDismiss`
(được wire sẵn bởi `BaseDialog.showDialog()` qua `setOnShowListener`/`setOnDismissListener`):

```kotlin
abstract class BaseDialogEv(
    activity: FragmentActivity,
    open val screenName: String,
    open var screenOpenTimestamp: Long = 0L,
) : BaseDialog(activity) {

    abstract fun getPopupName(): String   // trả về hằng số PopupName.XXX

    /** Log click button trong dialog → click_btn_ev */
    fun onBtnClicked(buttonName: String) {
        AnalyticsEvents.logClickBtnEv(
            _ClickBtnEv(
                screenName = screenName,
                buttonName = buttonName,
                popupName = getPopupName(),
                time = /* số giây từ lúc mở app */ 0
            )
        )
    }

    /** Fire screen_view_ev STOP khi dialog đóng */
    private fun logScreenViewEv() {
        val duration = (SystemClock.elapsedRealtime() - screenOpenTimestamp) / 1000
        AnalyticsEvents.logScreenViewEv(
            _ScreenViewEv(
                screenName = screenName,
                screenState = _ScreenViewEv.State.STOP,
                popupName = getPopupName(),
                duration = duration.toInt()
            )
        )
    }

    private var mPopupViewEv: PopupViewEv? = null

    override fun onShow(dialog: DialogInterface) {
        super.onShow(dialog)
        if (getPopupName().isEmpty()) return
        screenOpenTimestamp = SystemClock.elapsedRealtime()
        val popupViewEv = PopupViewEv(screenName, getPopupName())
        mPopupViewEv = popupViewEv
        AnalyticsUserProperties.logEventScreenOpen(screenName, getPopupName())   // user property
        (mActivity as? BaseActivity)?.onDialogShowing(popupViewEv, this)         // đẩy vào stack
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismiss()
    }

    open fun onDismiss(logEvent: Boolean = true) {
        if (getPopupName().isEmpty()) return
        if (logEvent) logScreenViewEv()                                          // fire event riêng
        mPopupViewEv?.let {
            (mActivity as? BaseActivity)?.onDialogDismiss(it)                    // gỡ khỏi stack
            mPopupViewEv = null
        }
    }
}
```

> `BaseDialogEv` làm **cả hai**: vừa tự fire `screen_view_ev` (qua `logScreenViewEv()`),
> vừa đăng ký vào stack của activity (`onDialogShowing`/`onDialogDismiss`). App đích cần có
> sẵn lớp `BaseDialog` quản lý `mDialog`/`mActivity` và gọi `onShow`/`onDismiss` — nếu chưa
> có, tự wire `dialog.setOnShowListener { onShow(it) }` / `setOnDismissListener { onDismiss(it) }`.

**Nếu dialog tạo bằng builder** (vd MaterialDialog): cho builder giữ một `DialogScreenViewEv`,
rồi trong listener:

```kotlin
// onShow:  mScreenViewEv?.onShow();  activity.onDialogShowing(popupViewEv, null)
// onDismiss: activity.onDialogDismiss(popupViewEv);  mScreenViewEv?.onClosed()
```

Nguồn đối chiếu: `BaseDialogEv` (`app/.../ui/base/BaseDialogEv.kt`),
`BaseDialog` (`app/.../ui/dialogs/BaseDialog.kt`),
`BaseMaterialDialogBuilder` (`app/.../ui/base/dialog/BaseMaterialDialogBuilder.kt`).

### Dialog standalone (ngoài Activity)

Dialog chạy từ Service/overlay (vd lock screen) không có activity để đăng ký stack — chỉ dùng
`DialogScreenViewEv`:

```kotlin
class MyOverlayDialog(context: Context) {
    private val dialogScreenViewEv = DialogScreenViewEv.newInstance(ScreenName.LOCK_SCREEN, "")
    private var dialog: Dialog? = null

    fun show() {
        dialog = Dialog(context).apply {
            setOnShowListener { dialogScreenViewEv.onShow() }
            setOnDismissListener { dialogScreenViewEv.onClosed() }
            show()
        }
    }
}
```

Không gọi `onDialogShowing`/`onDialogDismiss` (không có activity).

---

## Wire vào màn hình thực tế

**Activity/Fragment con** — chỉ cần extend base + override `getScreenName()`:

```kotlin
class HourlyDataFragment : BaseFragmentEv() {
    override fun getScreenName(): String = ScreenName.HOURLY_SCREEN
}
```

**Dialog/popup** — cách chuẩn là **extend `BaseDialogEv`** (xem section "Dialog tracking")
và override `getPopupName()`; việc fire event + đăng ký stack xảy ra tự động:

```kotlin
class RateDialog(activity: FragmentActivity) : BaseDialogEv(activity, ScreenName.HOME_SCREEN) {
    override fun getPopupName() = PopupName.RATE_DIALOG
}
```

Nếu dialog **không** đi qua base (vd dùng builder/`Dialog` thuần), tự báo cho activity:

```kotlin
val popup = PopupViewEv(ScreenName.HOME_SCREEN, PopupName.SELECT_LOCATION)
activity.onDialogShowing(popup, null)   // khi show
// …
activity.onDialogDismiss(popup)         // khi dismiss
```

**Click button:**

```kotlin
logClickBtnEv("addLocation")                       // → home_addLocation
logClickBtnEv("ok", PopupName.SELECT_LOCATION)     // → home_selectLocation_ok
```

---

## Naming convention (BẮT BUỘC giữ nhất quán)

| Trường | Quy ước | OK | KHÔNG OK |
|---|---|---|---|
| `screenName` | camelCase | `home`, `manageLocation` | `Home`, `manage_location` |
| `popupName` | camelCase hoặc `""` | `selectLocation`, `""` | `select_location` |
| `buttonName` | camelCase, không prefix/underscore | `addLocation`, `back` | `btn_add_location`, `add_location` |

Mọi giá trị đều đi qua `convertSnakeCaseToCamelCase()` trong `:firebase-events` trước khi ghép
tên event, nên đặt `snake_case` cũng tự chuyển — nhưng **thống nhất camelCase** để dễ đọc dashboard.

---

## Lifecycle: khi nào fire & state nào?

| Tình huống | `screen_view_ev`? | `screen_state` |
|---|---|---|
| Chuyển sang màn hình khác / back | ✅ | `stop` |
| Nhấn Home / Recent (app xuống bg) | ✅ | `home_recent` (sau delay 500ms) |
| Mở dialog/Fragment đè lên (app vẫn foreground) | ✅ (event của screen nền) | `overlap` |
| Dialog đóng (`BaseDialogEv`/`DialogScreenViewEv`) | ✅ (event riêng của dialog) | `stop` |
| Xoay màn hình (config change) | ⚠️ tùy | thường không vào `onStop` thật sự |
| `getScreenName()` rỗng | ❌ | bị skip (guard `screenName.isEmpty()`) |
| `openTimestamp == 0L` (chưa từng `onShow`) | ❌ | skip (chống fire trùng) |
| Welcome screen đang bật (nếu giữ guard) | ❌ | skip |

---

## Common pitfalls

- **Quên override `getScreenName()`** → trả `""` → toàn bộ event của màn hình đó bị skip.
- **Fragment tab nhưng để chạy nhánh `onResume` mặc định** → đếm sai/double. Tab phải gọi
  `initializingTab()` + `setFragmentVisible()` từ adapter, đừng để rơi vào nhánh "fragment thường".
- **Quên gọi `onDialogShowing`/`onDialogDismiss`** → popup không bao giờ xuất hiện trong
  `screen_name`/`click_btn_ev_name`.
- **Copy nguyên guard `mustShowWelcomeScreen()`** vào app không có welcome → biên dịch lỗi
  hoặc skip nhầm. Bỏ guard nếu không áp dụng.
- **`isAppInBackground()` trả sai** → state `home_recent` vs `overlap` đảo lộn. Test kỹ 2 case:
  nhấn Home (phải ra `home_recent`) và mở dialog (phải ra `overlap`).

---

## Prompt mẫu để paste vào Claude Code (ở app đích)

```
Đọc app-event/docs/SCREEN_VIEW_GUIDE.md rồi triển khai screen tracking cho app này:

1. Xác nhận đã có :firebase-events + AnalyticsModule.init. Nếu chưa, dừng và báo tôi.
2. Thêm dialog stack hooks (PopupViewEv, onDialogShowing/onDialogDismiss/
   getShowingPopup/getShowingPopupName) vào BaseActivity và BaseFragment của app.
3. Tạo ScreenViewEventHelper theo B2, map isAppInBackground() và getShowingPopup()
   sang đúng API của app này.
4. Tạo BaseActivityEv và BaseFragmentEv theo B3/B4. Bỏ guard mustShowWelcomeScreen()
   nếu app không có welcome screen.
5. Tạo DialogScreenViewEv (B6) + BaseDialogEv (B7) để dialog tự phát screen_view_ev.
   Cho các dialog hiện có extend BaseDialogEv + override getPopupName(); dialog ngoài
   activity (service/overlay) dùng DialogScreenViewEv standalone.
6. Quét toàn bộ Activity/Fragment hiện có, sinh object ScreenName/PopupName (camelCase)
   và cho từng màn hình extend base + override getScreenName().
7. Build thử variant debug, liệt kê những chỗ tôi cần tự điền (time của click_btn_ev,
   cách check background, lớp BaseDialog nếu app chưa có) nếu không suy ra được.
KHÔNG sửa code trong :firebase-events.
```

---

## Reference

- Integration layer (copy từ đây):
  [`ScreenViewEventHelper`](../../app/src/main/java/com/tohsoft/weather/event/ScreenViewEventHelper.kt) ·
  [`BaseActivityEv`](../../app/src/main/java/com/tohsoft/weather/ui/base/BaseActivityEv.kt) ·
  [`BaseFragmentEv`](../../app/src/main/java/com/tohsoft/weather/ui/base/BaseFragmentEv.kt) ·
  [`ScreenName`](../../app/src/main/java/com/tohsoft/weather/event/ScreenName.kt) ·
  popup hooks trong [`BaseActivity`](../../app/src/main/java/com/tohsoft/weather/ui/base/BaseActivity.kt).
- Dialog tracking:
  [`DialogScreenViewEv`](../../app/src/main/java/com/tohsoft/weather/event/DialogScreenViewEv.kt) ·
  [`BaseDialogEv`](../../app/src/main/java/com/tohsoft/weather/ui/base/BaseDialogEv.kt) ·
  [`BaseDialog`](../../app/src/main/java/com/tohsoft/weather/ui/dialogs/BaseDialog.kt) ·
  [`BaseMaterialDialogBuilder`](../../app/src/main/java/com/tohsoft/weather/ui/base/dialog/BaseMaterialDialogBuilder.kt).
- Firebase log layer (dùng lại, không sửa):
  [`AnalyticsEvents`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt) ·
  [`AnalyticsUserProperties`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/AnalyticsUserProperties.kt) ·
  [`_ScreenViewEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/_ScreenViewEv.kt) ·
  [`_ClickBtnEv`](../../firebase-events/src/main/java/com/tohsoft/firebase_events/models/_ClickBtnEv.kt).
- Remote-config toggles: `screen_view_ev_enable`, `click_btn_ev_enable` trong `EventConfigs`.
