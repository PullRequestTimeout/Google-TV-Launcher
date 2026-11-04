package dev.jcob.launcher.data

import kotlinx.serialization.Serializable

@Serializable
enum class HomeMode {
    /** Stock launcher is home; ours is just an app. */
    OFF,
    /** Accessibility service redirects Home presses to us. Stock launcher untouched. */
    SOFT,
    /** Stock launcher packages disabled so we are the only home. */
    HARD,
}

@Serializable
data class Category(val id: String, val name: String)

/** Everything the user can customise. Also the backup/export format. */
@Serializable
data class LauncherConfig(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val categories: List<Category> = emptyList(),
    /** package name -> category id */
    val appCategory: Map<String, String> = emptyMap(),

    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = false,
    val showStatus: Boolean = true,
    val statusSsid: Boolean = true,
    val statusSignal: Boolean = true,
    val statusIp: Boolean = true,
    val showCategories: Boolean = true,
    val use24h: Boolean = false,

    /** App card width in dp; height follows at the 16:9 banner ratio. */
    val appCardWidth: Int = 160,
    val appSpacing: Int = 20,
    /** Focused card scale, in percent. */
    val appFocusZoom: Int = 110,
    val appCornerRadius: Int = 12,
    val showAppLabel: Boolean = true,
    val translucentCards: Boolean = true,
    /** Gap between the row and the bottom of the screen, in dp. */
    val appRowBottom: Int = 40,

    val weatherCity: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val fahrenheit: Boolean = false,

    val wallpaperMinutes: Int = 5,
    val screensaverSeconds: Int = 20,

    /** Salted SHA-256 of the settings PIN, or null for no PIN. */
    val pinHash: String? = null,

    /** KeyEvent keycode name (e.g. "KEYCODE_BUTTON_1") -> package to launch. */
    val keyRemaps: Map<String, String> = emptyMap(),
    /** Same as [keyRemaps] but for a long press. */
    val longPressRemaps: Map<String, String> = emptyMap(),

    val homeMode: HomeMode = HomeMode.OFF,
    /** Put the TV to sleep after this many hours without remote input. 0 = off. */
    val autoOffHours: Int = 0,
)
