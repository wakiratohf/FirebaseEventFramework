package com.tohsoft.firebase_events.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StringsTest {

    @Test
    fun `empty input returns empty`() {
        assertEquals("", "".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `single word capitalises first letter`() {
        assertEquals("Home", "home".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `snake_case becomes CamelCase`() {
        assertEquals("HomeScreen", "home_screen".convertSnakeCaseToCamelCase())
        assertEquals("MyHomeScreen", "my_home_screen".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `consecutive underscores are skipped`() {
        assertEquals("HomeScreen", "home__screen".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `leading underscore is dropped`() {
        assertEquals("HomeScreen", "_home_screen".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `the word popup is stripped before conversion`() {
        // The implementation strips the substring "popup" before splitting.
        assertEquals("Rate", "popup_rate".convertSnakeCaseToCamelCase())
        assertEquals("Rate", "rate_popup".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `mixed case input preserves non-first letters`() {
        assertEquals("HomeABC", "home_aBC".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `camelCase buttonName passes through to PascalCase`() {
        // Convention chuẩn: buttonName camelCase → PascalCase, không có dấu '_'
        assertEquals("AddLocation", "addLocation".convertSnakeCaseToCamelCase())
        assertEquals("SortConfirm", "sortConfirm".convertSnakeCaseToCamelCase())
        assertEquals("ChangeOrder", "changeOrder".convertSnakeCaseToCamelCase())
    }

    @Test
    fun `snake_case buttonName with btn prefix produces unwanted Btn token`() {
        // Tài liệu hoá hệ quả khi vi phạm convention:
        // buttonName "btn_xxx_yyy" sẽ tạo PascalCase "BtnXxxYyy" — dư chữ "Btn".
        // Convention bắt buộc buttonName phải camelCase không prefix.
        assertEquals("BtnSortConfirm", "btn_sort_confirm".convertSnakeCaseToCamelCase())
        assertEquals("BtnAddLocation", "btn_add_location".convertSnakeCaseToCamelCase())
    }
}
