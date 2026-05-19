package com.tohsoft.firebase_events.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.evaluateString

/**
 * Bắt vi phạm convention cho mọi enum implement interface tên `ClickBtnEv`
 * (bất kỳ package nào — host app tự khai báo interface marker với constructor
 * `(screenName, buttonName, popupName)` hoặc tương đương: arg thứ 2 là `buttonName`).
 *
 * Quy ước [buttonName] (constructor argument thứ 2):
 *  - Không rỗng.
 *  - Không chứa '_'.
 *  - Không bắt đầu bằng prefix "btn".
 *  - Bắt đầu bằng chữ thường (camelCase).
 *
 * Lý do: hàm `convertSnakeCaseToCamelCase()` trong `:firebase-events` sẽ split
 * '_' và capitalize từng từ. Nếu `buttonName = "btn_sort_confirm"` thì event name
 * Firebase sẽ ra `"{Screen}_BtnSortConfirm"` — dư chữ "Btn" và lệch convention.
 */
@Suppress("UnstableApiUsage")
class ButtonNameConventionDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CLICK_BTN_EV_SIMPLE_NAME = "ClickBtnEv"

        private val IMPLEMENTATION = Implementation(
            ButtonNameConventionDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        val ISSUE_UNDERSCORE: Issue = Issue.create(
            id = "ClickBtnEvUnderscore",
            briefDescription = "buttonName của ClickBtnEv không được chứa '_'",
            explanation = """
                `buttonName` phải là camelCase, không có dấu gạch dưới.
                Hàm `convertSnakeCaseToCamelCase()` sẽ split `_` và viết hoa từng từ
                → tạo PascalCase thừa. Ví dụ `"btn_sort_confirm"` sẽ ra
                `"BtnSortConfirm"` — lẫn chữ `Btn` không cần thiết và lệch convention
                của project.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        val ISSUE_BTN_PREFIX: Issue = Issue.create(
            id = "ClickBtnEvBtnPrefix",
            briefDescription = "buttonName của ClickBtnEv không nên bắt đầu bằng 'btn'",
            explanation = """
                Tên event Firebase đã có hậu tố từ enum constant + screen name,
                prefix `btn` thừa và làm sai lệch convention. Dùng tên ngắn camelCase
                không prefix, ví dụ `"sortConfirm"` thay vì `"btnSortConfirm"`.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        val ISSUE_NOT_CAMEL_CASE: Issue = Issue.create(
            id = "ClickBtnEvNotCamelCase",
            briefDescription = "buttonName phải bắt đầu bằng chữ thường (camelCase)",
            explanation = """
                `buttonName` phải bắt đầu bằng chữ thường hoặc số (camelCase).
                Tránh viết hoa chữ đầu (PascalCase) vì sẽ tạo event name lệch
                convention.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        val ISSUE_EMPTY: Issue = Issue.create(
            id = "ClickBtnEvEmpty",
            briefDescription = "buttonName của ClickBtnEv không được rỗng",
            explanation = """
                `buttonName` rỗng sẽ tạo event Firebase không có phần button —
                không thể phân biệt nhiều nút trên cùng một màn hình.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private fun extractStringLiteral(expr: UExpression?): String? {
            if (expr == null) return null
            return when (expr) {
                is UPolyadicExpression -> expr.evaluateString()
                else -> {
                    val src = expr.sourcePsi
                    if (src is PsiLiteralExpression && src.value is String) {
                        src.value as String
                    } else {
                        expr.evaluateString()
                    }
                }
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (!implementsClickBtnEv(node)) return
                if (!node.isEnum) return
                for (field in node.fields) {
                    val enumConstantPsi = field as? PsiEnumConstant ?: continue
                    val uEnumConstant = enumConstantPsi.toUEnumConstant() ?: continue
                    validateEnumConstant(context, uEnumConstant)
                }
            }
        }

    private fun PsiEnumConstant.toUEnumConstant(): UEnumConstant? {
        val ue = UastFacade.convertElementWithParent(this, UEnumConstant::class.java)
        return ue as? UEnumConstant
    }

    private fun implementsClickBtnEv(uClass: UClass): Boolean {
        val psi = uClass.javaPsi
        return psi.interfaces.any { it.name == CLICK_BTN_EV_SIMPLE_NAME } ||
            psi.supers.any { it.name == CLICK_BTN_EV_SIMPLE_NAME }
    }

    private fun validateEnumConstant(context: JavaContext, enumConstant: UEnumConstant) {
        val args = enumConstant.valueArguments
        // Convention enum ClickBtnEv: (screenName, buttonName, popupName)
        // → buttonName ở index 1.
        if (args.size < 2) return
        val buttonNameExpr = args[1]
        val buttonName = extractStringLiteral(buttonNameExpr) ?: return

        val location = context.getLocation(buttonNameExpr)
        val constantName = enumConstant.name ?: "?"

        if (buttonName.isBlank()) {
            context.report(
                ISSUE_EMPTY,
                buttonNameExpr,
                location,
                "buttonName của `$constantName` không được rỗng.",
            )
            return
        }

        if (buttonName.contains('_')) {
            context.report(
                ISSUE_UNDERSCORE,
                buttonNameExpr,
                location,
                "buttonName `\"$buttonName\"` của `$constantName` chứa `_`. " +
                    "Dùng camelCase, ví dụ `\"sortConfirm\"`.",
            )
            return
        }

        if (buttonName.length >= 4) {
            val prefix = buttonName.substring(0, 3)
            val next = buttonName[3]
            if (prefix.equals("btn", ignoreCase = true) && (next.isUpperCase() || next == '_')) {
                context.report(
                    ISSUE_BTN_PREFIX,
                    buttonNameExpr,
                    location,
                    "buttonName `\"$buttonName\"` của `$constantName` bắt đầu bằng prefix `btn`. " +
                        "Bỏ prefix, ví dụ `\"sortConfirm\"` thay vì `\"btnSortConfirm\"`.",
                )
                return
            }
        }

        val first = buttonName.first()
        if (!first.isLowerCase() && !first.isDigit()) {
            context.report(
                ISSUE_NOT_CAMEL_CASE,
                buttonNameExpr,
                location,
                "buttonName `\"$buttonName\"` của `$constantName` không bắt đầu bằng chữ thường. " +
                    "Dùng camelCase, ví dụ `\"sortConfirm\"`.",
            )
        }
    }
}
