package dev.jcob.launcher.ui.settings

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jcob.launcher.data.LaunchableApp
import dev.jcob.launcher.data.SpeedTest
import dev.jcob.launcher.data.WifiStatus
import dev.jcob.launcher.data.downloadApk
import dev.jcob.launcher.data.moved
import dev.jcob.launcher.data.orderedApps
import dev.jcob.launcher.data.readWifiStatus
import dev.jcob.launcher.service.HomeAccessibilityService
import dev.jcob.launcher.shell.SavedNetwork
import dev.jcob.launcher.shell.ScanResult
import dev.jcob.launcher.ui.components.ConfirmDialog
import dev.jcob.launcher.ui.components.MenuOption
import dev.jcob.launcher.ui.components.OptionsDialog
import dev.jcob.launcher.ui.components.SectionHeader
import dev.jcob.launcher.ui.components.SettingRow
import dev.jcob.launcher.ui.components.TextEntryDialog
import dev.jcob.launcher.ui.components.ToggleRow
import dev.jcob.launcher.ui.components.TvDialog
import dev.jcob.launcher.ui.home.toast
import dev.jcob.launcher.ui.home.uninstall
import kotlinx.coroutines.launch
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jcob.launcher.ui.components.Ink
import dev.jcob.launcher.ui.components.InkDim

fun LazyListScope.appsSection(ctx: SectionContext) = block("apps") { AppsSection(ctx) }
fun LazyListScope.wifiSection(ctx: SectionContext) = block("wifi") { WifiSection(ctx) }
fun LazyListScope.buttonsSection(ctx: SectionContext) = block("buttons") { ButtonsSection(ctx) }

@Composable
private fun AppIcon(app: LaunchableApp) {
    Image(app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
}

@Composable
private fun AppsSection(ctx: SectionContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by ctx.app.apps.apps.collectAsStateWithLifecycle()
    val apps = orderedApps(all, ctx.config, includeHidden = true)
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var confirmUninstall by remember { mutableStateOf<LaunchableApp?>(null) }
    var enterUrl by remember { mutableStateOf(false) }
    var usbApks by remember { mutableStateOf<List<String>?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }

    Column {
        SectionHeader("Install")
        SettingRow("Install APK from a URL", icon = Icons.Outlined.Download, onClick = { enterUrl = true })
        SettingRow("Install APK from USB / Downloads", icon = Icons.Outlined.Usb, onClick = {
            scope.launch {
                progress = "Looking for APK files…"
                usbApks = ctx.app.packages.findApks()
                progress = null
                if (usbApks.isNullOrEmpty()) { usbApks = null; context.toast("No .apk files found on USB drives or in Downloads") }
            }
        })
        SettingRow("Get apps from Google Play", icon = Icons.Outlined.Add, onClick = {
            val intent = context.packageManager.getLeanbackLaunchIntentForPackage("com.android.vending")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.vending")
            if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else context.toast("Play Store not found")
        })

        SectionHeader("On the home screen (${apps.count { it.packageName !in ctx.config.hidden }} of ${apps.size})")
        apps.forEach { app ->
            val hidden = app.packageName in ctx.config.hidden
            SettingRow(
                title = app.label,
                subtitle = listOfNotNull(
                    if (hidden) "Hidden" else null,
                    ctx.config.appCategory[app.packageName]?.let { id -> ctx.config.categories.firstOrNull { it.id == id }?.name },
                ).joinToString(" · ").ifEmpty { null },
                trailing = { AppIcon(app) },
                onClick = { selected = app },
            )
        }
    }

    selected?.let { app ->
        val hidden = app.packageName in ctx.config.hidden
        OptionsDialog(
            title = app.label,
            options = buildList {
                add(MenuOption(if (hidden) "Show on home" else "Hide from home") {
                    ctx.update { c -> c.copy(hidden = if (hidden) c.hidden - app.packageName else c.hidden + app.packageName) }
                    selected = null
                })
                add(MenuOption("Move earlier") { ctx.update { it.moved(all, app.packageName, -1) }; selected = null })
                add(MenuOption("Move later") { ctx.update { it.moved(all, app.packageName, 1) }; selected = null })
                add(MenuOption("Move to start") { ctx.update { it.moved(all, app.packageName, -apps.size) }; selected = null })
                ctx.config.categories.forEach { cat ->
                    add(MenuOption("Category: ${cat.name}") {
                        ctx.update { it.copy(appCategory = it.appCategory + (app.packageName to cat.id)) }
                        selected = null
                    })
                }
                if (ctx.config.appCategory.containsKey(app.packageName)) {
                    add(MenuOption("Remove from category") {
                        ctx.update { it.copy(appCategory = it.appCategory - app.packageName) }
                        selected = null
                    })
                }
                if (!app.isSystem) add(MenuOption("Uninstall") { confirmUninstall = app; selected = null })
            },
            onDismiss = { selected = null },
        )
    }
    confirmUninstall?.let { app ->
        ConfirmDialog("Uninstall ${app.label}?", "This removes the app and its data.", "Uninstall",
            onConfirm = { confirmUninstall = null; scope.launch { uninstall(context, ctx.app, app.packageName) } },
            onDismiss = { confirmUninstall = null })
    }
    if (enterUrl) {
        TextEntryDialog("APK download URL", hint = "https://…/app.apk", keyboardType = KeyboardType.Uri,
            onDismiss = { enterUrl = false },
            onDone = { url ->
                enterUrl = false
                scope.launch {
                    progress = "Downloading…"
                    val result = runCatching {
                        val file = downloadApk(url.trim(), context.cacheDir.resolve("apk")) { p -> progress = "Downloading… ${(p * 100).toInt()}%" }
                        progress = "Installing…"
                        ctx.app.shell.install(file).getOrThrow()
                        file.delete()
                    }
                    progress = null
                    context.toast(result.fold({ "Installed" }, { "Install failed: ${it.message}" }))
                }
            })
    }
    usbApks?.let { files ->
        OptionsDialog(
            title = "Install which APK?",
            options = files.map { path ->
                MenuOption(path.substringAfterLast('/'), Icons.Outlined.Usb) {
                    usbApks = null
                    scope.launch {
                        progress = "Installing ${path.substringAfterLast('/')}…"
                        val error = ctx.app.packages.installPath(path)
                        progress = null
                        context.toast(error?.let { "Install failed: $it" } ?: "Installed")
                    }
                }
            },
            onDismiss = { usbApks = null },
        )
    }
    progress?.let { BusyDialog(it) }
}

@Composable
private fun BusyDialog(message: String) {
    TvDialog(onDismiss = {}) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun WifiSection(ctx: SectionContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wifi = ctx.app.wifi
    var status by remember { mutableStateOf<WifiStatus?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var saved by remember { mutableStateOf<List<SavedNetwork>>(emptyList()) }
    var selected by remember { mutableStateOf<ScanResult?>(null) }
    var confirmForget by remember { mutableStateOf<SavedNetwork?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf<String?>(null) }

    fun openSystemWifi() {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun refresh() {
        status = readWifiStatus(context)
        scanning = true
        saved = wifi.savedNetworks().distinctBy { it.id }
        results = wifi.scan()
        scanning = false
        status = readWifiStatus(context).let { s ->
            if (s.connected && s.ssid == null) s.copy(ssid = wifi.connectedSsid()) else s
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column {
        val s = status
        SectionHeader("Connection")
        ToggleRow(
            "Wi-Fi",
            checked = s?.enabled == true,
            subtitle = when {
                s == null -> null
                s.connected -> listOfNotNull(s.ssid, s.ip, s.linkMbps?.let { "$it Mbps link" }).joinToString(" · ")
                s.enabled -> "Not connected"
                else -> "Off"
            },
        ) { on ->
            if (!on) confirmOff = true
            else scope.launch {
                // With Wi-Fi off our shell (Wireless debugging) is down too, so fall back to Settings.
                if (!wifi.setEnabled(true)) openSystemWifi() else { kotlinx.coroutines.delay(3_000); refresh() }
            }
        }
        SettingRow("Speed test", icon = Icons.Outlined.Speed, value = speed, subtitle = "Downloads 25 MB from Cloudflare", onClick = {
            scope.launch {
                speed = "Testing…"
                speed = runCatching {
                    val ping = SpeedTest.latencyMs()
                    val mbps = SpeedTest.downloadMbps { p -> speed = "${(p * 100).toInt()}%" }
                    "%.0f Mbps · %d ms".format(mbps, ping)
                }.getOrElse { "Failed: ${it.message}" }
            }
        })
        SettingRow("System Wi-Fi settings", icon = Icons.Outlined.Settings, subtitle = "Connect to a new network, proxy and IP settings", onClick = ::openSystemWifi)

        SectionHeader(if (scanning) "Scanning…" else "Nearby networks")
        SettingRow("Scan again", icon = Icons.Outlined.Refresh, enabled = !scanning, onClick = { scope.launch { refresh() } })
        results.forEach { r ->
            val isSaved = saved.any { it.ssid == r.ssid }
            val isCurrent = s?.ssid == r.ssid
            SettingRow(
                title = r.ssid,
                subtitle = listOfNotNull(
                    if (isCurrent) "Connected" else if (isSaved) "Saved" else null,
                    r.band,
                    if (r.isSecured) "Secured" else "Open",
                ).joinToString(" · "),
                value = "▂▄▆█".take(r.bars.coerceAtLeast(1)),
                onClick = { selected = r },
            )
        }
        if (!scanning && results.isEmpty()) {
            Text("No networks found (scanning needs shell access).", color = InkDim, modifier = androidx.compose.ui.Modifier)
        }
    }

    selected?.let { r ->
        val savedEntry = saved.firstOrNull { it.ssid == r.ssid }
        OptionsDialog(
            title = r.ssid,
            subtitle = "${r.band} · ${r.signalDbm} dBm · ${r.security}",
            options = buildList {
                if (status?.ssid != r.ssid) {
                    add(MenuOption("Connect…") {
                        selected = null
                        // The shell on this firmware can't join networks, so hand off to Settings.
                        context.toast("Pick \"${r.ssid}\" in the system list")
                        openSystemWifi()
                    })
                }
                if (savedEntry != null) {
                    add(MenuOption("Forget network…") { selected = null; confirmForget = savedEntry })
                }
            },
            onDismiss = { selected = null },
        )
    }
    confirmForget?.let { network ->
        val isCurrent = status?.ssid == network.ssid
        ConfirmDialog(
            title = "Forget ${network.ssid}?",
            message = if (isCurrent) {
                "This is the network the Chromecast is using. It'll disconnect and you'll need the password to rejoin."
            } else {
                "You'll need the password to join it again."
            },
            confirmLabel = "Forget",
            onConfirm = {
                confirmForget = null
                scope.launch {
                    if (wifi.forget(network.id)) { context.toast("Forgot ${network.ssid}"); refresh() } else context.toast("Couldn't forget network")
                }
            },
            onDismiss = { confirmForget = null },
        )
    }
    if (confirmOff) {
        ConfirmDialog(
            title = "Turn Wi-Fi off?",
            message = "Streaming stops and shell access (TV power, installs) is unavailable until Wi-Fi is back on. Turn it back on here; it'll open system settings.",
            confirmLabel = "Turn off",
            onConfirm = { confirmOff = false; scope.launch { wifi.setEnabled(false); status = readWifiStatus(context) } },
            onDismiss = { confirmOff = false },
        )
    }
}

/** Keys that would break navigation if remapped. */
private val Reserved = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
)

@Composable
private fun ButtonsSection(ctx: SectionContext) {
    val context = LocalContext.current
    val all by ctx.app.apps.apps.collectAsStateWithLifecycle()
    var capturing by remember { mutableStateOf(false) }
    var capturedKey by remember { mutableStateOf<String?>(null) }
    var longPress by remember { mutableStateOf<Boolean?>(null) }
    var removing by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val serviceOn = ctx.app.homeMode.isAccessibilityEnabled()

    fun labelFor(target: String) =
        if (target == HomeAccessibilityService.TargetHome) "This launcher" else all.firstOrNull { it.packageName == target }?.label ?: target

    Column {
        if (!serviceOn) {
            SettingRow("Enable the button service", subtitle = "Remaps need it. Also used for Home mode.", onClick = {
                if (!ctx.app.homeMode.setAccessibilityEnabled(true)) context.toast("Needs WRITE_SECURE_SETTINGS; run scripts/deploy.ps1")
            })
        }
        SettingRow("Add a shortcut", icon = Icons.Outlined.Add, subtitle = "Press a remote button, then pick an app", enabled = serviceOn, onClick = {
            capturing = true
            HomeAccessibilityService.capture = { code ->
                capturing = false
                if (code in Reserved) context.toast("That button is needed for navigation and can't be remapped")
                else capturedKey = KeyEvent.keyCodeToString(code)
            }
        })
        val mappings = ctx.config.keyRemaps.map { (k, v) -> Triple(k, false, v) } + ctx.config.longPressRemaps.map { (k, v) -> Triple(k, true, v) }
        if (mappings.isNotEmpty()) SectionHeader("Shortcuts")
        mappings.sortedBy { it.first }.forEach { (key, long, target) ->
            SettingRow(
                title = "${prettyKey(key)}${if (long) " (hold)" else ""}",
                value = labelFor(target),
                subtitle = "Press to remove",
                onClick = { removing = key to long },
            )
        }
    }

    if (capturing) {
        TvDialog(onDismiss = { capturing = false; HomeAccessibilityService.capture = null }) {
            Column {
                Text("Press the remote button to remap…", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text("For example the YouTube, Netflix or input button.", color = InkDim)
            }
        }
    }
    capturedKey?.let { key ->
        if (longPress == null) {
            OptionsDialog(
                title = prettyKey(key),
                options = listOf(
                    MenuOption("When pressed") { longPress = false },
                    MenuOption("When held (normal press keeps working)") { longPress = true },
                ),
                onDismiss = { capturedKey = null },
            )
        } else {
            val isLong = longPress == true
            OptionsDialog(
                title = "Open which app?",
                options = listOf(MenuOption("This launcher (home)") {
                    ctx.update { if (isLong) it.copy(longPressRemaps = it.longPressRemaps + (key to HomeAccessibilityService.TargetHome)) else it.copy(keyRemaps = it.keyRemaps + (key to HomeAccessibilityService.TargetHome)) }
                    capturedKey = null; longPress = null
                }) + all.map { app ->
                    MenuOption(app.label) {
                        ctx.update { if (isLong) it.copy(longPressRemaps = it.longPressRemaps + (key to app.packageName)) else it.copy(keyRemaps = it.keyRemaps + (key to app.packageName)) }
                        capturedKey = null; longPress = null
                    }
                },
                onDismiss = { capturedKey = null; longPress = null },
            )
        }
    }
    removing?.let { (key, long) ->
        ConfirmDialog("Remove shortcut?", "${prettyKey(key)}${if (long) " (hold)" else ""} goes back to its normal behaviour.", "Remove",
            onConfirm = {
                ctx.update { if (long) it.copy(longPressRemaps = it.longPressRemaps - key) else it.copy(keyRemaps = it.keyRemaps - key) }
                removing = null
            },
            onDismiss = { removing = null })
    }
}

private fun prettyKey(name: String) =
    name.removePrefix("KEYCODE_").split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
