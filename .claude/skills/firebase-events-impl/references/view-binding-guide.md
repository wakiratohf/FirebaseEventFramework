# View Binding / findViewById Tracking Guide

Skill sinh `BaseTrackedActivity.kt` + `BaseTrackedFragment.kt` cho project dùng XML layouts. Mọi Activity/Fragment chỉ cần extends + override `getScreenName()` là tự fire `screen_view_ev`.

## Files được sinh ra

| File | Vai trò |
|---|---|
| `BaseTrackedActivity.kt` | Base cho mọi Activity có screen riêng — auto `logScreenStart/Stop` ở onResume/onPause |
| `BaseTrackedFragment.kt` | Base cho mọi Fragment có screen riêng — handle cả ViewPager tab visibility |
| `ScreenName.kt` | `object ScreenName { const val HOME = "home" ... }` |
| `ButtonName.kt` | `object ButtonName { const val BTN_CONNECT = "ConnectButton" ... }` |
| `PopupName.kt` | `object PopupName { const val RATE_DIALOG = "rate_dialog" ... }` |
| `AnalyticsEventsUtils.kt` | Facade gọi runtime module |

## Pattern chuẩn — Activity

```kotlin
class HomeActivity : BaseTrackedActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun getScreenName(): String = ScreenName.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            logClickBtn(ClickBtnEvCatalog.BTN_CONNECT)
            viewModel.connect()
        }

        binding.btnPremium.setOnClickListener {
            logClickBtn(ClickBtnEvCatalog.BTN_PREMIUM)
            launchIapFlow()
        }
    }
}
```

Base class lo:
- `onResume` → `AnalyticsEventsUtils.logScreenStart(getScreenName())`
- `onPause` → `logScreenStop(getScreenName(), durationSec)` với duration tính từ thời điểm onResume

## Pattern chuẩn — Fragment đơn

```kotlin
class SettingsFragment : BaseTrackedFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun getScreenName(): String = ScreenName.SETTINGS

    override fun onCreateView(...): View {
        _binding = FragmentSettingsBinding.inflate(...)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            logClickBtn(if (isChecked) ButtonName.BTN_DARK_MODE_ON
                        else ButtonName.BTN_DARK_MODE_OFF)
            viewModel.setDarkMode(isChecked)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
```

## Pattern — Fragment trong ViewPager / ViewPager2

ViewPager keep nhiều fragment `RESUMED` cùng lúc → `onResume` không phải signal visibility tin cậy. Phải dùng `useUserVisibleHint = true` và host callback:

```kotlin
class ServerListFragment : BaseTrackedFragment() {
    override fun getScreenName(): String = ScreenName.SERVER_LIST
    override val useUserVisibleHint: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // ... binding setup
    }
}
```

Host activity gọi:

```kotlin
binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
    private var previousIndex = -1

    override fun onPageSelected(position: Int) {
        if (previousIndex >= 0) {
            (adapter.getFragment(previousIndex) as? BaseTrackedFragment)
                ?.setFragmentVisible(false)
        }
        (adapter.getFragment(position) as? BaseTrackedFragment)
            ?.setFragmentVisible(true)
        previousIndex = position
    }
})
```

Tab đầu tiên → gọi `setFragmentVisible(true)` 1 lần trong `onCreate` của host sau khi register.

## Pattern — Dialog / BottomSheet

```kotlin
class RateUsDialog : DialogFragment() {

    override fun onResume() {
        super.onResume()
        AnalyticsEventsUtils.logScreenStart(
            screenName = (activity as? BaseTrackedActivity)?.let { ... } ?: ScreenName.HOME,
            popupName = PopupName.RATE_DIALOG,
        )
    }

    override fun onPause() {
        AnalyticsEventsUtils.logScreenStop(
            screenName = ...,
            durationSec = ...,
            popupName = PopupName.RATE_DIALOG,
        )
        super.onPause()
    }

    // Click on 5-star
    binding.btnFiveStars.setOnClickListener {
        AnalyticsEventsUtils.logClickBtn(
            screenName = host screen,
            buttonName = ButtonName.BTN_RATE_5_STAR,
            popupName = PopupName.RATE_DIALOG,
        )
    }
}
```

Pattern này verbose hơn vì DialogFragment không có base class chung — skill chưa sinh `BaseTrackedDialogFragment` ở v1. Workaround: tạo dialog wrapper bằng tay, đặt ngoài thư mục `event/` để không bị overwrite.

## RecyclerView item click

```kotlin
class ServerAdapter : RecyclerView.Adapter<...>() {
    var onItemClick: (Server) -> Unit = {}

    override fun onBindViewHolder(holder: VH, position: Int) {
        val server = items[position]
        holder.itemView.setOnClickListener {
            // Activity passes lambda that logs + handles
            onItemClick(server)
        }
    }
}

// In Activity:
adapter.onItemClick = { server ->
    logClickBtn(ButtonName.BTN_SERVER_ITEM)
    navigateToServerDetail(server)
}
```

Đặt `logClickBtn` ở Activity, không trong Adapter — Adapter không nên phụ thuộc analytics module.

## Pattern — Click value (FAQ item id, package name)

```kotlin
binding.faqItems.children.forEachIndexed { idx, item ->
    item.setOnClickListener {
        logClickBtn(
            event = ClickBtnEvCatalog.BTN_FAQ_EXPANDED,
            value = "faq_$idx"
        )
        expandFaq(idx)
    }
}
```

`value` đi vào `click_btn_ev_value` riêng, KHÔNG vào event name → tránh explode cardinality (Firebase chặn event quá nhiều variant tên).

## Anti-patterns

### ❌ Quên override `getScreenName()`

```kotlin
class HomeActivity : BaseTrackedActivity() {
    // không override → abstract method → compile error
}
```

Đúng vậy — base class abstract `getScreenName()` chính là để compiler enforce.

### ❌ Hard-code string

```kotlin
override fun getScreenName() = "home"  // ❌
override fun getScreenName() = "Home"  // ❌
```

→ Phải dùng `ScreenName.HOME`. Refactor sau dễ hơn (đổi 1 chỗ).

### ❌ Gọi `logScreenStart` 2 lần

```kotlin
override fun onResume() {
    super.onResume()  // base đã log
    AnalyticsEventsUtils.logScreenStart(ScreenName.HOME)  // ❌ double
}
```

→ `super.onResume()` đã fire rồi. KHÔNG fire thêm.

### ❌ Dùng `ButtonName.X` nhưng quên CamelCase trong spec

```yaml
- screen: home
  id: btn_connect
  name: connect_button  # ❌ phải CamelCase: "ConnectButton"
```

→ Lint sẽ catch sau khi generate (`ClickBtnEvUnderscore` rule).

### ❌ Activity không có entry trong spec

```kotlin
class DebugActivity : BaseTrackedActivity() {  // P1+P7 cùng lúc
    override fun getScreenName() = "debug"  // string literal — no ScreenName.DEBUG
}
```

→ Audit P7 LOW. Đúng — debug screen không cần track. Thêm `// @no-analytics` ở class declaration để skip.

## Tham khảo audit pattern liên quan

- **P1** Activity không extends `BaseTrackedActivity`
- **P2** Fragment lifecycle thiếu log
- **P3** `setOnClickListener` thiếu `logClickBtn`
- **P4** Dialog/Sheet không track popup
- **P6** File generated bị sửa tay
- **P7** Activity/Fragment có code, không có spec
