package com.tohsoft.firebase_events.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ButtonNameConventionDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ButtonNameConventionDetector()

    override fun getIssues(): List<Issue> = listOf(
        ButtonNameConventionDetector.ISSUE_UNDERSCORE,
        ButtonNameConventionDetector.ISSUE_BTN_PREFIX,
        ButtonNameConventionDetector.ISSUE_NOT_CAMEL_CASE,
        ButtonNameConventionDetector.ISSUE_EMPTY,
    )

    private val clickBtnEvInterface: TestFile = kotlin(
        """
        package com.example.event

        interface ClickBtnEv {
            val screenName: String
            val buttonName: String
            val popupName: String
        }
        """,
    ).indented()

    fun testValidCamelCase_passes() {
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    enum class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv {
                        SORT_CONFIRM("home", "sortConfirm", ""),
                        REFRESH("home", "refresh", ""),
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun testUnderscore_reported() {
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    enum class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv {
                        BAD("home", "btn_sort_confirm", ""),
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/event/HomeBtn.kt:8: Error: buttonName "btn_sort_confirm" của BAD chứa _. Dùng camelCase, ví dụ "sortConfirm". [ClickBtnEvUnderscore]
                    BAD("home", "btn_sort_confirm", ""),
                                ~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testBtnPrefix_reported() {
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    enum class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv {
                        BAD("home", "btnSortConfirm", ""),
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/event/HomeBtn.kt:8: Error: buttonName "btnSortConfirm" của BAD bắt đầu bằng prefix btn. Bỏ prefix, ví dụ "sortConfirm" thay vì "btnSortConfirm". [ClickBtnEvBtnPrefix]
                    BAD("home", "btnSortConfirm", ""),
                                ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testPascalCase_reported() {
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    enum class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv {
                        BAD("home", "SortConfirm", ""),
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/event/HomeBtn.kt:8: Error: buttonName "SortConfirm" của BAD không bắt đầu bằng chữ thường. Dùng camelCase, ví dụ "sortConfirm". [ClickBtnEvNotCamelCase]
                    BAD("home", "SortConfirm", ""),
                                ~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testEmpty_reported() {
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    enum class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv {
                        BAD("home", "", ""),
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/event/HomeBtn.kt:8: Error: buttonName của BAD không được rỗng. [ClickBtnEvEmpty]
                    BAD("home", "", ""),
                                ~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testNonEnum_ignored() {
        // Class implementing ClickBtnEv that is NOT an enum should be ignored.
        lint()
            .files(
                clickBtnEvInterface,
                kotlin(
                    """
                    package com.example.event

                    class HomeBtn(
                        override val screenName: String,
                        override val buttonName: String,
                        override val popupName: String,
                    ) : ClickBtnEv
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
