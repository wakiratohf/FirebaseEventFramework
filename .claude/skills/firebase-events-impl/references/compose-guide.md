# Jetpack Compose Tracking Guide

Skill sinh file `TrackedComposables.kt` chứa 4 primitive + 1 helper để cover mọi tracking scenario trong Compose.

## Primitives được sinh ra

| Primitive | Mục đích | Khi nào dùng |
|---|---|---|
| `TrackedScreen(screenName, content)` | Fire `screen_view_ev` START/STOP qua `DisposableEffect`, provide `LocalScreenName` | Wrap mỗi `*Screen()` top-level |
| `TrackedPopup(popupName, content)` | Fire `screen_view_ev` với popup_name + provide `LocalPopupName` | Wrap content của Dialog/BottomSheet |
| `TrackedButton(buttonName, onClick, ...)` | Drop-in cho `Button` — auto fire `click_btn_ev` | Mọi click action chính |
| `TrackedIconButton(buttonName, onClick, ...)` | Drop-in cho `IconButton` | Back arrow, close X, share icon |
| `Modifier.trackedClickable(buttonName, onClick)` | Wrap cho `Modifier.clickable` | Row/Card click pattern |
| `logComposeClick(buttonName)` | Imperative log từ trong existing lambda | Khi không thể wrap (incremental adoption) |

## CompositionLocal

```kotlin
val LocalScreenName = staticCompositionLocalOf { "" }
val LocalPopupName  = compositionLocalOf { PopupName.NONE }
```

- `LocalScreenName` — `staticCompositionLocalOf` vì đổi screen recreate toàn bộ subtree, không cần fine-grained invalidation.
- `LocalPopupName` — `compositionLocalOf` vì popup có thể mở/đóng over screen mà không tái tạo screen.

Nested composable đọc qua `LocalScreenName.current` mà không cần truyền `screenName` xuống N tầng.

## Pattern chuẩn

### Top-level screen

```kotlin
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    TrackedScreen(ScreenName.HOME) {
        HomeContent(state = viewModel.state.collectAsState().value,
                    onConnect = viewModel::connect)
    }
}
```

`TrackedScreen` fire `START` ngay khi vào composition, fire `STOP` với duration khi `onDispose`.

### Button trong screen

```kotlin
TrackedButton(
    buttonName = ButtonName.BTN_CONNECT,
    onClick = onConnect,
) {
    Text(stringResource(R.string.connect))
}
```

Tự đọc `LocalScreenName` → fire `click_btn_ev` với `screen_name="Home"`, `button_name="ConnectButton"`. Không cần truyền screen.

### Stateful button (toggle)

Spec:
```yaml
- screen: settings
  id: btn_dark_mode
  name: DarkModeToggle
  type: toggle
```

Sinh 2 const `BTN_DARK_MODE_ON` / `BTN_DARK_MODE_OFF`. Call site:

```kotlin
Switch(
    checked = isDark,
    onCheckedChange = { newValue ->
        // Pass the NEW state (post-click)
        logComposeClick(
            if (newValue) ButtonName.BTN_DARK_MODE_ON
            else ButtonName.BTN_DARK_MODE_OFF
        )
        viewModel.setDarkMode(newValue)
    }
)
```

### Radio button group

Spec:
```yaml
- screen: settings
  id: radio_timeout
  name: TimeoutSelector
  type: radio
  options:
    - { value: "5min",  label: "5Min" }
    - { value: "15min", label: "15Min" }
```

Call site:
```kotlin
TimeoutOptions.forEach { option ->
    Row(
        modifier = Modifier.trackedClickable(
            buttonName = when (option) {
                Timeout.FIVE_MIN -> ButtonName.RADIO_TIMEOUT_5_MIN
                Timeout.FIFTEEN_MIN -> ButtonName.RADIO_TIMEOUT_15_MIN
                else -> ButtonName.RADIO_TIMEOUT_30_MIN
            },
            onClick = { onSelect(option) },
        )
    ) {
        RadioButton(selected = selected == option, onClick = null)
        Text(option.label)
    }
}
```

### Dialog / BottomSheet

```kotlin
if (showRateDialog) {
    AlertDialog(onDismissRequest = { showRateDialog = false }) {
        TrackedPopup(PopupName.RATE_DIALOG) {
            RateDialogContent(
                onRate5Stars = {
                    // logComposeClick auto picks up popup name from LocalPopupName
                    logComposeClick(ButtonName.BTN_RATE_5_STAR)
                    onRate(5)
                }
            )
        }
    }
}
```

Click trong popup → `click_btn_ev` có cả `screen_name="Home"` và `popup_name="rate_dialog"`.

### Bottom navigation

```kotlin
NavigationBar {
    NavigationBarItem(
        selected = currentRoute == "home",
        onClick = {
            logComposeClick(ButtonName.BTN_NAV_HOME)
            navController.navigate("home")
        },
        icon = { Icon(Icons.Default.Home, null) },
        label = { Text("Home") },
    )
}
```

### LazyColumn item click

```kotlin
LazyColumn {
    items(servers, key = { it.id }) { server ->
        Card(
            modifier = Modifier.trackedClickable(
                buttonName = ButtonName.BTN_SERVER_ITEM,
                onClick = { onServerClick(server) },
            )
        ) {
            ServerRow(server)
        }
    }
}
```

LazyColumn lifecycle không gây vấn đề — `Modifier.trackedClickable` đọc local mỗi lần lambda chạy.

## Anti-patterns

### ❌ Hard-code string

```kotlin
TrackedButton(buttonName = "ConnectButton", ...) // ❌
```

→ Lint không catch được, có thể typo. Luôn dùng `ButtonName.BTN_CONNECT`.

### ❌ Không wrap top-level

```kotlin
@Composable
fun HomeScreen() {
    Column {
        TrackedButton(...) { ... }   // ❌ — LocalScreenName == ""
    }
}
```

→ `screen_view_ev` không fire, click event có `screen_name=""`. PHẢI có `TrackedScreen` wrap ngoài cùng.

### ❌ Dialog global render ngoài NavHost (audit P16)

```kotlin
@Composable
fun VpnNavGraph(...) {
    if (showDialog) {
        AppAlertDialog(positiveText = "OK", onPositive = { ... })  // ❌ screen_name=""
    }
    NavHost(...) { /* screens wrap TrackedScreen */ }
}
```

Dialog khai báo ở nav-graph level (sibling của `NavHost`, overlay mọi màn) **không có `TrackedScreen` ancestor** → `LocalScreenName.current` rỗng → `click_btn_ev` ra event name kiểu `_Ok` thay vì `HomeOk`. `AppAlertDialog`/`QuitDialog` đọc `LocalScreenName.current` nên trong screen thì OK, chỉ vỡ ở nav-graph level.

**Fix:** suy screen từ route hiện tại rồi cấp qua `CompositionLocalProvider` — KHÔNG dùng `TrackedScreen` (nó fire thêm `screen_view_ev` cho dialog):

```kotlin
val currentScreenName = routeToScreenName(
    navController.currentBackStackEntryAsState().value?.destination?.route
)
if (showDialog) {
    CompositionLocalProvider(LocalScreenName provides currentScreenName) {  // ✅
        AppAlertDialog(positiveText = "OK", onPositive = { ... })
    }
}
```

`routeToScreenName` map route → `ScreenName` (Routes values thường khớp 1:1 ScreenName values, chỉ route parameterized như `premium/{x}` cần tách).

### ❌ Dùng cả 2 (double track)

```kotlin
TrackedButton(
    buttonName = ButtonName.BTN_CONNECT,
    onClick = {
        logComposeClick(ButtonName.BTN_CONNECT) // ❌ double-fire
        onConnect()
    }
) { ... }
```

→ Fire 2 lần. Chọn 1: hoặc dùng `TrackedButton` (auto), hoặc `Button` thường + `logComposeClick` inline.

### ❌ Đặt `TrackedScreen` trong `if`

```kotlin
if (isLoggedIn) {
    TrackedScreen(ScreenName.HOME) { ... }
}
// nếu isLoggedIn toggle → fire START liên tục mỗi lần true
```

→ Toggle state liên tục gây fire spam. Tách logic: `TrackedScreen` ở top-level, content rẽ nhánh bên trong.

## Tracking hierarchy nested NavHost

Nếu app dùng nested NavHost (parent route + child route), wrap mỗi destination độc lập:

```kotlin
NavHost(navController, startDestination = "home") {
    composable("home") {
        TrackedScreen(ScreenName.HOME) { HomeContent() }
    }
    composable("server/{id}") { backStackEntry ->
        TrackedScreen(ScreenName.SERVER_DETAIL) { ServerDetail() }
    }
}
```

Mỗi navigate → composable cũ dispose → `screen_view_ev STOP`; composable mới enter → `START`. Duration tự tính.

## Tham khảo audit pattern liên quan

- **P11** Compose Button onClick — xem `audit-patterns.md`
- **P12** `Modifier.clickable` — xem `audit-patterns.md`
- **P13** hoisted button — xem `audit-patterns.md`
- **P14** Back navigation — xem `audit-patterns.md`

Compose mode KHÔNG trigger P3 (setOnClickListener), P2 (Fragment lifecycle).
