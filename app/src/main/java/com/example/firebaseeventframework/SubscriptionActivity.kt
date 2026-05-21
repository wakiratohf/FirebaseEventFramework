package com.example.firebaseeventframework

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.subscription.SubscriptionScreen
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme

/** Màn Subscription (mock/demo) — tự log `screen_view_ev` qua [BaseTrackedActivity]. */
class SubscriptionActivity : BaseTrackedActivity() {

    override fun screenName(): String = ScreenName.SUBSCRIPTION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseEventFrameworkTheme {
                SubscriptionScreen(onClose = { finish() })
            }
        }
    }
}
