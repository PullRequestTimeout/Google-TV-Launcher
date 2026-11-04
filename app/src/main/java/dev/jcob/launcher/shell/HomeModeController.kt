package dev.jcob.launcher.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import dev.jcob.launcher.data.ConfigStore
import dev.jcob.launcher.data.HomeMode
import dev.jcob.launcher.service.HomeAccessibilityService
import dev.jcob.launcher.service.PhotoDreamService

/** Google TV packages that claim HOME ahead of us. Disabled in [HomeMode.HARD]. */
val StockHomePackages = listOf(
    "com.google.android.apps.tv.launcherx",
    "com.google.android.tungsten.setupwraith",
)

/**
 * Switches between stock home, soft takeover (accessibility service catches Home) and hard
 * takeover (stock launcher disabled). Every path back to stock lives here.
 */
class HomeModeController(
    private val context: Context,
    private val shell: ShellClient,
    private val config: ConfigStore,
) {
    private val accessibilityComponent =
        ComponentName(context, HomeAccessibilityService::class.java).flattenToString()

    /** Returns null on success, or a message explaining what went wrong. */
    suspend fun apply(mode: HomeMode): String? {
        when (mode) {
            HomeMode.OFF -> {
                if (!enableStockLaunchers()) return "Couldn't re-enable the stock launcher (shell unavailable)."
            }
            HomeMode.SOFT -> {
                if (!enableStockLaunchers()) return "Couldn't re-enable the stock launcher (shell unavailable)."
                if (!setAccessibilityEnabled(true)) return "Couldn't enable the Home button service."
            }
            HomeMode.HARD -> {
                if (!setAccessibilityEnabled(true)) return "Couldn't enable the Home button service."
                for (pkg in StockHomePackages) {
                    val result = shell.run("pm disable-user --user 0 $pkg").getOrNull()
                    if (result?.exitCode != 0) {
                        enableStockLaunchers()
                        return "Couldn't disable $pkg: ${result?.allOutput ?: "shell unavailable"}"
                    }
                }
            }
        }
        config.update { it.copy(homeMode = mode) }
        return null
    }

    /** Back to the stock Google TV home screen, then show it. */
    suspend fun revertToStock(): String? {
        apply(HomeMode.OFF)?.let { return it }
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(StockHomePackages.first())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return null
    }

    private suspend fun enableStockLaunchers(): Boolean {
        val disabled = shell.output("pm list packages -d")?.lines()?.map { it.removePrefix("package:").trim() }
            ?: return false
        return StockHomePackages.filter { it in disabled }.all { pkg ->
            shell.run("pm enable $pkg").getOrNull()?.exitCode == 0
        }
    }

    /**
     * The accessibility service stays on in every mode (it also powers button remaps and idle
     * tracking); it only redirects Home when the mode is SOFT.
     */
    fun setAccessibilityEnabled(enabled: Boolean): Boolean = runCatching {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.filter { it.isNotBlank() } ?: emptyList()
        val updated = if (enabled) (current + accessibilityComponent).distinct() else current - accessibilityComponent
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated.joinToString(":"))
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (updated.isEmpty()) 0 else 1)
    }.onFailure { Log.e("HomeMode", "accessibility toggle failed", it) }.isSuccess

    fun isAccessibilityEnabled(): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.contains(accessibilityComponent) == true

    /** Makes our photo slideshow the screensaver, or restores Google TV's ambient mode. */
    fun setPhotoScreensaver(enabled: Boolean): Boolean = runCatching {
        val value = if (enabled) {
            ComponentName(context, PhotoDreamService::class.java).flattenToString()
        } else {
            StockScreensaver
        }
        Settings.Secure.putString(context.contentResolver, "screensaver_components", value)
        Settings.Secure.putInt(context.contentResolver, "screensaver_enabled", 1)
    }.isSuccess

    fun isPhotoScreensaver(): Boolean =
        Settings.Secure.getString(context.contentResolver, "screensaver_components")
            ?.contains(PhotoDreamService::class.java.name) == true

    private companion object {
        const val StockScreensaver = "com.google.android.apps.tv.dreamx/.service.Backdrop"
    }
}
