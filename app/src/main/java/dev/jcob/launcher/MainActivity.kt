package dev.jcob.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jcob.launcher.data.Pin
import dev.jcob.launcher.ui.components.PinDialog
import dev.jcob.launcher.ui.home.HomeScreen
import dev.jcob.launcher.ui.home.toast
import dev.jcob.launcher.ui.settings.Section
import dev.jcob.launcher.ui.settings.SettingsScreen
import dev.jcob.launcher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    /** null = home screen. */
    private var settings by mutableStateOf<Section?>(null)
    /** Section waiting on the PIN prompt. */
    private var pinFor by mutableStateOf<Section?>(null)
    private var pinError by mutableStateOf<String?>(null)
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp
        setContent {
            LauncherTheme {
                val current = settings
                if (current == null) {
                    // Back on the home screen has nowhere to go.
                    BackHandler {}
                    HomeScreen(
                        onLaunch = ::launch,
                        onOpenSettings = { openSettings(Section.Customise) },
                        onOpenWifi = { openSettings(Section.Wifi) },
                    )
                } else {
                    BackHandler { settings = null }
                    SettingsScreen(initial = current)
                }
                pinFor?.let { target ->
                    PinDialog(
                        title = "Enter PIN",
                        error = pinError,
                        onDismiss = { pinFor = null; pinError = null },
                        onDone = { pin ->
                            val stored = app.config.config.value.pinHash
                            if (stored == null || Pin.matches(pin, stored)) {
                                unlocked = true
                                pinFor = null
                                pinError = null
                                settings = target
                            } else {
                                pinError = "Wrong PIN"
                            }
                        },
                    )
                }
            }
        }
    }

    private fun openSettings(section: Section) {
        val needsPin = (application as LauncherApp).config.config.value.pinHash != null
        if (needsPin && !unlocked) pinFor = section else settings = section
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while we're showing Settings returns to the home screen.
        settings = null
        pinFor = null
    }

    override fun onStop() {
        super.onStop()
        // Re-lock once the user leaves the launcher.
        unlocked = false
    }

    private fun launch(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("Couldn't open app") }
    }
}
