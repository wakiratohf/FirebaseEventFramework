package com.tohsoft.firebase_events.models

enum class AllowPermission(val identify: String, val title: String) {
    ALARM("A", "AlarmPermission"),
    BATTERY_OPTIMIZATION("B", "BatteryPermission"),
    FULL_SCREEN_NOTIFICATION("F", "FullScreenNotificationPermission"),
    LOCATION("L", "LocationPermission"),
    POST_NOTIFICATION("N", "NotificationPermission"),
    OVERLAY_PERMISSION("O", "OverlayPermission"),
    LOCATION_BACKGROUND("G", "BackgroundLocationPermission");

    companion object{
        fun fromTitle(title: String): AllowPermission? {
            return entries.find { it.title == title }
        }
    }
}