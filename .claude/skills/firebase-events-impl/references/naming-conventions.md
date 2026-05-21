# Naming Conventions

> **Authoritative source**: `firebase-events/docs/NAMING_CONVENTION.md` trong upstream repo. Skill GENERATE code follow tài liệu này strictly. Khi upstream đổi convention → skill cập nhật để match.

## TL;DR — Bảng convention

| Field | Convention | ĐÚNG | SAI |
|---|---|---|---|
| **Hằng Kotlin (LHS)** | `SCREAMING_SNAKE_CASE` | `HOME_BACK`, `MANAGE_LOCATION_ADD_LOCATION` | `homeBack`, `Home_Back`, `BTN_HOME_BACK` |
| **screenName value** | `camelCase`, lowercase first | `"home"`, `"manageLocation"`, `"weatherRadar"` | `"Home"`, `"manage_location"`, `"MANAGE_LOCATION"` |
| **popupName value** | `camelCase` hoặc `""` | `"selectLocation"`, `"rateDialog"`, `""` | `"select_location"`, `"SelectLocation"` |
| **buttonName value** | `camelCase`, KHÔNG `_`, KHÔNG `btn_` prefix | `"back"`, `"addLocation"`, `"sortConfirm"` | `"btn_sort_confirm"`, `"add_location"`, `"AddLocation"` |

## Cấu trúc code skill sinh ra

Theo NAMING_CONVENTION.md: mỗi screen có 1 enum riêng implement `ClickBtnEv`. **KHÔNG có `object ButtonName`** chứa button values — values inline trong enum entries.

### Files được sinh

```
event/
├── ClickBtnEv.kt              ← marker interface (LINT hook)
├── ScreenName.kt              ← const object
├── PopupName.kt               ← const object (chỉ sinh khi spec có popups)
├── HomeBtnEv.kt               ← 1 enum cho screen `home`
├── ManageLocationBtnEv.kt     ← 1 enum cho screen `manageLocation`
├── SettingsBtnEv.kt           ← 1 enum cho screen `settings`
├── AnalyticsEventsUtils.kt    ← wrapper API
└── AnalyticsBootstrap.kt      ← 1-line init
```

### Ví dụ output

```kotlin
// ClickBtnEv.kt — marker interface
interface ClickBtnEv {
    val screenName: String
    val buttonName: String
    val popupName: String
}

// ScreenName.kt
object ScreenName {
    const val HOME = "home"
    const val MANAGE_LOCATION = "manageLocation"
    const val SETTINGS = "settings"
    const val NONE = ""
}

// PopupName.kt
object PopupName {
    const val RATE_DIALOG = "rateDialog"
    const val NONE = ""
}

// HomeBtnEv.kt
enum class HomeBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    HOME_BACK(ScreenName.HOME, "back", ""),
    HOME_MENU(ScreenName.HOME, "menu", ""),
    HOME_ADD_LOCATION(ScreenName.HOME, "addLocation", ""),
    HOME_RATE_DIALOG_OK(ScreenName.HOME, "ok", PopupName.RATE_DIALOG),
    ;
}
```

## Quy tắc đặt tên hằng enum entry

- Format: `{SCREEN}_{ACTION}` hoặc `{SCREEN}_{POPUP}_{ACTION}` cho button trong popup.
- KHÔNG dùng prefix `BTN_` (vd KHÔNG đặt `BTN_HOME_BACK`).
- Stateful button (toggle/checkbox/accordion/radio): suffix variant `_ON/_OFF`, `_CHECKED/_UNCHECKED`, etc.

### Mapping spec → enum entry

Spec:
```yaml
buttons:
  - {screen: home, popup: rateDialog, id: btn_ok, name: ok}
  - {screen: settings, id: btn_dark_mode, name: darkMode, type: toggle}
```

Generated:
```kotlin
HOME_RATE_DIALOG_OK(ScreenName.HOME, "ok", PopupName.RATE_DIALOG),

SETTINGS_DARK_MODE_ON(ScreenName.SETTINGS, "darkModeOn", ""),
SETTINGS_DARK_MODE_OFF(ScreenName.SETTINGS, "darkModeOff", ""),
```

Conversion rules:
- `screen.id` → `_upper_snake` cho const prefix
- `screen.id` → `_camel` cho value (camelCase)
- `button.name` → `_upper_snake` cho const action part
- `button.name` giữ nguyên làm value (validator đã enforce camelCase)
- `popup.id` → `_upper_snake` cho const popup part

## Anti-patterns

| ❌ Sai | Hệ quả | ✅ Sửa thành |
|---|---|---|
| `buttonName = "btn_sort_confirm"` | Event `Xxx_BtnSortConfirm`, dư `Btn` | `"sortConfirm"` |
| `buttonName = "add_location"` | Trộn convention | `"addLocation"` |
| `buttonName = "AddLocation"` | Transform tạo `Xxx_AddLocation` lẫn lộn | `"addLocation"` |
| `buttonName = "On"`, `"Off"` | Lệch convention | `"on"`, `"off"` |
| `screenName = "home screen"` | Firebase reject space | `"home"` hoặc `"homeScreen"` |
| Hằng `BTN_HOME_BACK = "back"` | Prefix `BTN_` thừa | Enum entry `HOME_BACK` trong `HomeBtnEv` |
| `object ButtonName { const val ... }` | Lint không catch flat object | Per-screen enum implement `ClickBtnEv` |

## Enforcement compile-time

Module `:firebase-events-lint` ship `ButtonNameConventionDetector`:
- Match enum implement interface tên `ClickBtnEv` (bất kỳ package)
- Inspect constructor arg index 1 (`buttonName`)
- 4 Lint Issues: `ClickBtnEvUnderscore` / `ClickBtnEvBtnPrefix` / `ClickBtnEvNotCamelCase` / `ClickBtnEvEmpty`
- Vi phạm = `:app:lintDebug` fail build

## `event_name_style` override

Default convention output PascalCase (`Settings_Back`). Override:
```yaml
project:
  event_name_style: camel_case   # mặc định pascal_case
```
→ Skill generate `AnalyticsEventsUtils` bypass `_ClickBtnEv` model, build event name local + gọi `AnalyticsEvents.logEvent(name, bundle)`.

## Migration từ skill cũ (v1.12 → v1.13)

Skill v1.12 sinh `ButtonName.kt` + `ClickBtnEvCatalog` global. v1.13 không sinh nữa, thay bằng per-screen enum.

Sau `/analytics-generate`:
- Sinh per-screen files (`HomeBtnEv.kt`, `SettingsBtnEv.kt`, ...)
- `ButtonName.kt` cũ NGUYÊN trên disk (xoá tay)
- `ClickBtnEv.kt` cũ overwrite → chỉ còn marker interface

**Call site cần migrate:**

```kotlin
// Cũ
AnalyticsEventsUtils.logClickBtn(ClickBtnEvCatalog.HOME_BTN_BACK)
TrackedButton(buttonName = ButtonName.BTN_CONNECT, ...)

// Mới
AnalyticsEventsUtils.logClickBtn(HomeBtnEv.HOME_BACK)
TrackedButton(event = HomeBtnEv.HOME_CONNECT, ...)
```
