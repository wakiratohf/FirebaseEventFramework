package com.tohsoft.app_event

/**
 * Contract for the per-app enum that lists every place the user can open
 * the app from (launcher icon, notification, widget, deep link, lock screen,
 * etc.).
 *
 * Each product defines its own enum and lets it implement this interface, then
 * passes values to [OpenAppFromIntent]. Example:
 *
 * ```
 * enum class MusicOpenSource(override val where: String) : OpenAppSource {
 *     APP_ICON("app_icon"),
 *     NOTI_PLAYER("noti_player"),
 *     WIDGET("widget"),
 * }
 * ```
 */
interface OpenAppSource {
    /**
     * Stable identifier used as the `{where}` segment in
     * `OAF_ev_{where}_{hour}` (the Firebase param value formatted by
     * `:firebase-events`). Keep it snake_case lowercase and short.
     */
    val where: String
}
