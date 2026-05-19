# Event naming convention — `firebase-events`

> **Mục đích**: chốt convention duy nhất cho `screenName`, `popupName`,
> `buttonName` để mọi dự án copy module `firebase-events` đều log event với
> format giống nhau, dễ phân tích trên Firebase / BigQuery.
>
> **Phạm vi**: tài liệu này áp dụng cho mọi dự án tích hợp module
> `firebase-events`. Khi copy-paste module này sang dự án mới, kèm theo file
> này (và [`CONTEXT.md`](CONTEXT.md)) làm spec.
>
> Reference function: [`String.convertSnakeCaseToCamelCase()`](../src/main/java/com/tohsoft/firebase_events/utils/Strings.kt).

---

## 1. Cú pháp transform cốt lõi

Mọi `screenName`, `popupName`, `buttonName` được truyền vào `_ScreenViewEv`
hoặc `_ClickBtnEv` đều đi qua hàm `convertSnakeCaseToCamelCase()` trước khi ghép thành tên event Firebase:

```kotlin
"".convertSnakeCaseToCamelCase()                   // → ""
"home".convertSnakeCaseToCamelCase()               // → "Home"
"manageLocation".convertSnakeCaseToCamelCase()     // → "ManageLocation"  (camelCase input)
"manage_location".convertSnakeCaseToCamelCase()    // → "ManageLocation"  (snake_case input)
"popup_rate".convertSnakeCaseToCamelCase()         // → "Rate"            (strip "popup")
"rate_popup".convertSnakeCaseToCamelCase()         // → "Rate"            (strip "popup")
"btn_sort_confirm".convertSnakeCaseToCamelCase()   // → "BtnSortConfirm"  ⚠️ dư chữ "Btn"
```

**Hệ quả**: cả camelCase và snake_case đều cho ra cùng output PascalCase.
Project phải chọn **một** convention và dùng nhất quán — nếu trộn cả 2, code
catalog sẽ rối, khó grep.

---

## 2. Convention CHÍNH THỨC (chốt cho mọi project)

| Field | Convention | ĐÚNG | SAI |
|---|---|---|---|
| **Hằng Kotlin** (LHS) | `SCREAMING_SNAKE_CASE` | `HOME_BACK`, `MANAGE_LOCATION_ADD_LOCATION` | `homeBack`, `Home_Back` |
| **screenName** (value) | `camelCase`, không khoảng trắng | `"home"`, `"manageLocation"`, `"weatherRadar"` | `"Home"`, `"manage_location"`, `"MANAGE_LOCATION"`, `"home screen"` |
| **popupName** (value) | `camelCase` hoặc `""` (rỗng = không có popup) | `"selectLocation"`, `"rateDialog"`, `""` | `"select_location"`, `"SelectLocation"` |
| **buttonName** (value) | `camelCase`, **KHÔNG prefix `btn_`**, **KHÔNG có `_`** | `"back"`, `"addLocation"`, `"sortConfirm"` | `"btn_sort_confirm"`, `"add_location"`, `"AddLocation"`, `"BTN_SORT"` |

### Tại sao KHÔNG dùng prefix `btn_`?

Tên event Firebase được ghép thành `{Screen}[_{Popup}]_{Button}` (PascalCase).
Nếu `buttonName = "btn_sort_confirm"` thì event ra `Xxx_BtnSortConfirm` — dư
chữ `Btn` (vì event đã là `click_btn_ev` rồi). Convention thống nhất giúp:

- Grep nhanh hơn (toàn bộ button event có dạng `{Screen}_{Action}`).
- Dễ phân tích trên BigQuery (substring không lẫn `Btn`).
- Đồng bộ với 200+ entries hiện có trong project Weather (`WF3`).

### Tại sao chọn camelCase mà KHÔNG phải snake_case cho value?

Cả hai input đều cho ra PascalCase output giống nhau. Chọn camelCase vì:
- Khớp với cách Android/Kotlin viết tên màn hình (`HomeFragment`, `ManageLocationActivity`).
- Ngắn hơn snake_case (`"manageLocation"` vs `"manage_location"`).
- Dễ đối chiếu code-to-event-name khi debug Firebase DebugView.

---

## 3. Quy tắc đặt hằng `ClickBtnEv` (interface gợi ý)

Module này không ship sẵn `interface ClickBtnEv` (vì button names là
domain-specific). Khi triển khai trong project, dùng mẫu sau:

```kotlin
interface ClickBtnEv {
    val screenName: String
    val buttonName: String
    val popupName: String
}

// Catalog screen/popup names (giữ ở app, KHÔNG nằm trong :firebase-events).
object ScreenName {
    const val HOME = "home"
    const val MANAGE_LOCATION = "manageLocation"
    // ...
}

object PopupName {
    const val RATE_DIALOG = "rateDialog"
    const val SELECT_LOCATION = "selectLocation"
    // ...
}

// Mỗi screen có 1 enum riêng — gom theo screen, KHÔNG gom theo button type.
enum class HomeScreenBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    HOME_BACK(ScreenName.HOME, "back", ""),
    HOME_MENU(ScreenName.HOME, "menu", ""),
    HOME_ADD_LOCATION(ScreenName.HOME, "addLocation", ""),
    HOME_RATE_DIALOG_OK(ScreenName.HOME, "ok", PopupName.RATE_DIALOG),
}
```

### Quy tắc đặt tên hằng

- Format: `{SCREEN}_{ACTION}` hoặc `{SCREEN}_{POPUP}_{ACTION}` nếu là button trong popup.
- VD: `HOME_BACK`, `HOME_ADD_LOCATION`, `HOME_RATE_DIALOG_OK`.
- KHÔNG dùng prefix `BTN_` (ví dụ KHÔNG đặt `BTN_HOME_BACK`).

---

## 4. Công thức ghép tên event Firebase

| Event Firebase | Param key | Param value format | Built by |
|---|---|---|---|
| `click_btn_ev` | `click_btn_ev_name` | `{Screen}[_{Popup}]_{Button}` (PascalCase, ghép `_`) | [`_ClickBtnEv.getBtnEventNameValue()`](../src/main/java/com/tohsoft/firebase_events/models/_ClickBtnEv.kt) |
| `click_btn_ev` | `click_btn_ev_time` | int — giây từ lúc mở app | — |
| `screen_view_ev` | `screen_name` | `{Screen}[_{Popup}]` (PascalCase) | [`_ScreenViewEv.getScreenNameValue()`](../src/main/java/com/tohsoft/firebase_events/models/_ScreenViewEv.kt) |
| `screen_view_ev` | `screen_state` | `overlap` / `stop` / `home_recent` | — |
| `screen_view_ev` | `duration` | int — giây ở lại màn hình | — |
| `open_app_from_ev` | `open_app_from_ev_where_time` | `OAF_ev_{where}_{hour24}` | [`AnalyticsEvents.logOpenAppFromEv`](../src/main/java/com/tohsoft/firebase_events/AnalyticsEvents.kt) |
| user property `screen_open` | — | `sr_{screenName}[_{popupName}]` (camelCase, **KHÔNG** transform) | [`AnalyticsUserProperties.getScreenOpenName`](../src/main/java/com/tohsoft/firebase_events/AnalyticsUserProperties.kt) |

**Lưu ý**: user property `screen_open` **không** transform sang PascalCase — giữ
nguyên camelCase. Chỉ event names mới transform.

---

## 5. Ví dụ end-to-end

```kotlin
// Catalog (giữ ở app)
const val SCREEN_MANAGE_LOCATION = "manageLocation"

enum class ManageLocationBtnEv(
    override val screenName: String,
    override val buttonName: String,
    override val popupName: String,
) : ClickBtnEv {
    BACK(SCREEN_MANAGE_LOCATION, "back", ""),
    ADD_LOCATION(SCREEN_MANAGE_LOCATION, "addLocation", ""),
    DELETE_ITEM_OK(SCREEN_MANAGE_LOCATION, "ok", "deleteAddress"),
}

// Call site
fun onAddLocationClick() {
    AnalyticsEvents.logClickBtnEv(
        _ClickBtnEv(
            screenName = ManageLocationBtnEv.ADD_LOCATION.screenName,
            buttonName = ManageLocationBtnEv.ADD_LOCATION.buttonName,
            popupName = ManageLocationBtnEv.ADD_LOCATION.popupName,
            time = secondsSinceAppOpen(),
        )
    )
}

// Event Firebase nhận được:
// event_name = "click_btn_ev"
// click_btn_ev_name = "ManageLocation_AddLocation"
// click_btn_ev_time = 42

// Với DELETE_ITEM_OK (có popup):
// click_btn_ev_name = "ManageLocation_DeleteAddress_Ok"
```

---

## 6. Anti-patterns — KHÔNG làm

| ❌ Sai | Hệ quả | ✅ Sửa thành |
|---|---|---|
| `buttonName = "btn_sort_confirm"` | Event ra `Xxx_BtnSortConfirm`, dư chữ `Btn` | `"sortConfirm"` |
| `buttonName = "add_location"` (snake_case) | Trộn convention với 200+ camelCase entries | `"addLocation"` |
| `buttonName = "AddLocation"` (PascalCase) | Transform tạo `Xxx_AddLocation` (chữ `A` thường lẫn hoa khó debug) | `"addLocation"` |
| `buttonName = "On"`, `"Off"` (viết hoa chữ đầu) | Lệch convention; nên đồng bộ với enum constant viết hoa | `"on"`, `"off"` |
| `screenName = "home screen"` (có space) | Firebase reject ký tự space ở event name | `"home"` hoặc `"homeScreen"` |
| `popupName = "select_location"` | Lệch với `popupName = ""` của các entry không có popup | `"selectLocation"` |
| Hằng `BTN_HOME_BACK = "back"` | Prefix `BTN_` thừa ở tên hằng | `HOME_BACK = "back"` |

---

## 7. Enforce convention (khuyến nghị)

### 7.1. Unit test

Trong project tích hợp, viết test gom mọi enum implement `ClickBtnEv` rồi
assert convention. Tham khảo `app/src/test/java/com/tohsoft/weather/event/ClickBtnEvConventionTest.kt`
trong project Weather:

```kotlin
class ClickBtnEvConventionTest {
    private val all: List<ClickBtnEv> = buildList {
        addAll(HomeScreenBtnEv.entries)
        addAll(ManageLocationBtnEv.entries)
        // ... liệt kê hết enum của project
    }

    @Test fun `buttonName must not contain underscore`() {
        all.forEach {
            assertFalse(it.buttonName.contains('_'))
        }
    }

    @Test fun `buttonName must not start with btn prefix`() {
        all.forEach {
            val bn = it.buttonName
            val violates = bn.length >= 4
                && bn.substring(0, 3).equals("btn", true)
                && (bn[3].isUpperCase() || bn[3] == '_')
            assertFalse(violates)
        }
    }

    @Test fun `buttonName must start with lowercase`() {
        all.forEach {
            val first = it.buttonName.firstOrNull() ?: return@forEach
            assertTrue(first.isLowerCase() || first.isDigit())
        }
    }
}
```

### 7.2. Custom Lint rule (highlight ngay trong IDE)

Module `:firebase-events-lint` **ship cùng repo này** (sibling của
`firebase-events/`). Khi copy module SDK sang project mới, **bắt buộc
copy luôn** `firebase-events-lint/`:

- `ButtonNameConventionDetector` — UAST detector match enum implement
  interface tên **`ClickBtnEv`** (bất kỳ package nào).
- 4 Lint Issues: `ClickBtnEvUnderscore`, `ClickBtnEvBtnPrefix`,
  `ClickBtnEvNotCamelCase`, `ClickBtnEvEmpty`.

Wire vào `:app`:

```kotlin
// settings.gradle.kts
include(":firebase-events")
include(":firebase-events-lint")

// app/build.gradle.kts
dependencies {
    implementation(project(":firebase-events"))
    lintChecks(project(":firebase-events-lint"))
}

android {
    lint {
        error += setOf("ClickBtnEvUnderscore", "ClickBtnEvBtnPrefix",
                       "ClickBtnEvNotCamelCase", "ClickBtnEvEmpty")
    }
}
```

Catalog phải có 1 interface `ClickBtnEv` để rule fire — Lint chỉ
catch enum implement interface với simple name khớp `ClickBtnEv`:

```kotlin
// app/src/main/java/.../event/ClickBtnEv.kt
interface ClickBtnEv {
    val screenName: String
    val buttonName: String
    val popupName: String
}
```

Trigger: `./gradlew :app:lintDebug` — Lint sẽ báo ERROR ngay tại dòng khai báo
hằng nếu vi phạm. Trong IDE (Android Studio), Lint chạy realtime → vi
phạm gạch đỏ ngay khi gõ value.

### 7.3. Trong runtime debug

`EventNameValidator.validate()` đã có sẵn — chỉ chạy khi `AnalyticsModule.isTestMode = true` — sẽ log warning nếu tên event hoặc param key vi phạm
giới hạn Firebase (≤ 40 ký tự, ký tự cho phép, …). Đây là tầng bảo vệ cuối,
không thay thế được test/Lint.

---

## 8. Tham chiếu nhanh

- Reference function: [`Strings.convertSnakeCaseToCamelCase`](../src/main/java/com/tohsoft/firebase_events/utils/Strings.kt)
- Event format: [`_ClickBtnEv`](../src/main/java/com/tohsoft/firebase_events/models/_ClickBtnEv.kt), [`_ScreenViewEv`](../src/main/java/com/tohsoft/firebase_events/models/_ScreenViewEv.kt)
- Validator: [`EventNameValidator`](../src/main/java/com/tohsoft/firebase_events/utils/EventNameValidator.kt)
- Lint module (ship cùng repo): `../../firebase-events-lint/`
- Detector source: [`ButtonNameConventionDetector`](../../firebase-events-lint/src/main/java/com/tohsoft/firebase_events/lint/ButtonNameConventionDetector.kt)
- Issue registry: [`ClickBtnEvIssueRegistry`](../../firebase-events-lint/src/main/java/com/tohsoft/firebase_events/lint/ClickBtnEvIssueRegistry.kt)
- Detector test: [`ButtonNameConventionDetectorTest`](../../firebase-events-lint/src/test/java/com/tohsoft/firebase_events/lint/ButtonNameConventionDetectorTest.kt)
