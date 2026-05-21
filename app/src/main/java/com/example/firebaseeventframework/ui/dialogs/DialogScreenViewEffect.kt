package com.example.firebaseeventframework.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.tohsoft.app_event.DialogScreenViewEv

@Composable
fun TrackDialogScreenView(screenName: String, popupName: String) {
    val tracker = remember(screenName, popupName) {
        DialogScreenViewEv(screenName, popupName)
    }
    DisposableEffect(tracker) {
        tracker.onShow()
        onDispose { tracker.onClosed() }
    }
}
