package com.tohsoft.firebase_events.utils

fun String.convertSnakeCaseToCamelCase(): String {
    var input = this
    if (this.isEmpty()) {
        return ""
    } else if (this.contains("popup")) {
        input = this.replace("popup", "")
    }
    val words: Array<String> = input.split("_".toRegex()).dropWhile { it.isEmpty() }.toTypedArray()
    val result = StringBuilder()

    for (i in words.indices) {
        val word = words[i]
        if (word.isEmpty()) {
            continue  // Bỏ qua các chuỗi rỗng do dấu gạch dưới liên tiếp
        }
        result.append(word[0].uppercaseChar()) // Viết hoa chữ cái đầu
            .append(word.substring(1)) // Thêm phần còn lại của từ
    }
    return result.toString()
}