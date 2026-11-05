package dev.jcob.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.data.LaunchableApp
import dev.jcob.launcher.data.LauncherConfig
import dev.jcob.launcher.service.PowerTimers
import dev.jcob.launcher.shell.ShellClient
import dev.jcob.launcher.ui.components.ConfirmDialog
import dev.jcob.launcher.ui.components.MenuOption
import dev.jcob.launcher.ui.components.OptionsDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

@Composable
fun AppOptionsDialog(app: LaunchableApp, config: LauncherConfig, onMove: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val launcher = context.applicationContext as LauncherApp
    val scope = rememberCoroutineScope()
    var pickCategory by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }

    when {
        pickCategory -> OptionsDialog(
            title = "Category for ${app.label}",
            options = listOf(MenuOption("None") {
                launcher.config.update { it.copy(appCategory = it.appCategory - app.packageName) }
                onDismiss()
            }) + config.categories.map { cat ->
                MenuOption(cat.name) {
                    launcher.config.update { it.copy(appCategory = it.appCategory + (app.packageName to cat.id)) }
                    onDismiss()
                }
            },
            subtitle = if (config.categories.isEmpty()) "Create categories in Settings → Categories." else null,
            onDismiss = onDismiss,
        )
        confirmUninstall -> ConfirmDialog(
            title = "Uninstall ${app.label}?",
            message = "This removes the app and its data.",
            confirmLabel = "Uninstall",
            onConfirm = {
                onDismiss()
                scope.launch { uninstall(context, launcher, app.packageName) }
            },
            onDismiss = onDismiss,
        )
        else -> OptionsDialog(
            title = app.label,
            options = buildList {
                add(MenuOption("Move", Icons.Outlined.SwapHoriz) { onDismiss(); onMove() })
                add(MenuOption("Hide from home", Icons.Outlined.VisibilityOff) {
                    launcher.config.update { it.copy(hidden = it.hidden + app.packageName) }
                    onDismiss()
                })
                add(MenuOption("Category", Icons.Outlined.Folder) { pickCategory = true })
                add(MenuOption("App info", Icons.Outlined.Info) {
                    onDismiss()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                })
                if (!app.isSystem) add(MenuOption("Uninstall", Icons.Outlined.Delete) { confirmUninstall = true })
            },
            onDismiss = onDismiss,
        )
    }
}

/** Uses the shell when available (no extra dialog), otherwise the system uninstall prompt. */
suspend fun uninstall(context: Context, launcher: LauncherApp, packageName: String) {
    if (launcher.shell.isPaired) {
        val error = launcher.packages.uninstall(packageName)
        if (error == null) { context.toast("Uninstalled"); return }
    }
    context.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private val SleepOptions = listOf(0, 15, 30, 60, 90, 120)

@Composable
fun QuickMenu(onDismiss: () -> Unit, onOpenWifi: () -> Unit) {
    val context = LocalContext.current
    val launcher = context.applicationContext as LauncherApp
    val scope = rememberCoroutineScope()
    val shellState by launcher.shell.state.collectAsStateWithLifecycle()
    var sleepAt by remember { mutableStateOf(PowerTimers.sleepTimerAt(context)) }
    var confirmRestart by remember { mutableStateOf(false) }

    fun runTv(label: String, action: suspend () -> Boolean) {
        scope.launch { if (!action()) context.toast("$label failed. Check Settings → System → Shell access.") }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart this device?",
            message = "It takes about a minute to come back.",
            confirmLabel = "Restart",
            onConfirm = { onDismiss(); runTv("Restart") { launcher.tv.restart() } },
            onDismiss = { confirmRestart = false },
        )
        return
    }

    val sleepLabel = sleepAt?.let { "Sleep timer: off at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) }
        ?: "Sleep timer: off"
    OptionsDialog(
        title = "TV & power",
        subtitle = when (shellState) {
            is ShellClient.State.Unpaired -> "Shell access isn't set up, so power controls won't work yet."
            is ShellClient.State.Failed -> "Shell access is failing; power controls may not work."
            else -> null
        },
        options = listOf(
            MenuOption("Turn TV off", Icons.Outlined.PowerSettingsNew) { onDismiss(); runTv("TV off") { launcher.tv.tvOff() } },
            MenuOption("Turn TV on / switch to this input", Icons.Outlined.Tv) { runTv("TV on") { launcher.tv.tvOn() } },
            MenuOption("TV input menu", Icons.Outlined.Input) { onDismiss(); runTv("Input menu") { launcher.tv.inputMenu() } },
            MenuOption(sleepLabel, Icons.Outlined.Bedtime) {
                // Cycle Off → 15 → 30 → 60 → 90 → 120 → Off, measured from now.
                val currentMinutes = sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() }
                val next = SleepOptions.firstOrNull { it > (currentMinutes ?: 0) + 1 } ?: 0
                if (next == 0) PowerTimers.clearSleepTimer(context) else PowerTimers.setSleepTimer(context, next)
                sleepAt = PowerTimers.sleepTimerAt(context)
            },
            MenuOption("Wi-Fi", Icons.Outlined.Wifi) { onDismiss(); onOpenWifi() },
            MenuOption("Restart device", Icons.Outlined.RestartAlt) { confirmRestart = true },
        ),
        onDismiss = onDismiss,
    )
}
