package com.tohsoft.ads.utils

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers

object CoroutineHandler {
    val coroutineExceptionHandler = CoroutineExceptionHandler() { _, throwable ->
        AdDebugLog.loge(throwable)
    }

    val IOScope = Dispatchers.IO + coroutineExceptionHandler
    val MainScope = Dispatchers.Main + coroutineExceptionHandler
    val DefaultScope = Dispatchers.Default + coroutineExceptionHandler
}