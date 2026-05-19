package com.example.firebaseeventframework.event

import android.os.Bundle
import com.tohsoft.firebase_events.models.AnalyticsEvent

data class PullToRefreshEv(
    val screenName: String,
    val itemsLoaded: Int,
    val durationMs: Long
) : AnalyticsEvent {
    override val eventName: String = "pull_to_refresh"
    override fun toBundle(): Bundle = Bundle().apply {
        putString("screen_name", screenName)
        putInt("items_loaded", itemsLoaded)
        putLong("duration_ms", durationMs)
    }
}
