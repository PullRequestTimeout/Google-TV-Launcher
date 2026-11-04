package dev.jcob.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jcob.launcher.data.Category
import dev.jcob.launcher.data.HomeMode
import dev.jcob.launcher.data.Pin
import dev.jcob.launcher.data.WeatherApi
import dev.jcob.launcher.service.PowerTimers
import dev.jcob.launcher.shell.ShellClient
import dev.jcob.launcher.ui.components.CheckRow
import dev.jcob.launcher.ui.components.ConfirmDialog
import dev.jcob.launcher.ui.components.CycleRow
import dev.jcob.launcher.ui.components.MenuOption
import dev.jcob.launcher.ui.components.OptionsDialog
import dev.jcob.launcher.ui.components.PinDialog
import dev.jcob.launcher.ui.components.SectionHeader
import dev.jcob.launcher.ui.components.SettingRow
import dev.jcob.launcher.ui.components.TextEntryDialog
import dev.jcob.launcher.ui.components.ToggleRow
import dev.jcob.launcher.ui.home.toast
import kotlinx.coroutines.launch
import java.util.UUID

fun LazyListScope.customiseSection(ctx: SectionContext) = block("customise") { CustomiseSection(ctx) }
fun LazyListScope.categoriesSection(ctx: SectionContext) = block("categories") { CategoriesSection(ctx) }
fun LazyListScope.tvSection(ctx: SectionContext) = block("tv") { TvSection(ctx) }
fun LazyListScope.systemSection(ctx: SectionContext) = block("system") { SystemSection(ctx) }

@Composable
private fun CustomiseSection(ctx: SectionContext) {
    val c = ctx.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editCity by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Home screen")
        ToggleRow("Clock", c.showClock) { v -> ctx.update { it.copy(showClock = v) } }
        ToggleRow("24-hour clock", c.use24h, enabled = c.showClock) { v -> ctx.update { it.copy(use24h = v) } }
        ToggleRow("Date", c.showDate) { v -> ctx.update { it.copy(showDate = v) } }
        ToggleRow("Network status", c.showStatus, subtitle = "Wi-Fi name, signal and IP address in the top bar") { v ->
            ctx.update { it.copy(showStatus = v) }
        }
        CheckRow("Network name", c.statusSsid, enabled = c.showStatus) { v -> ctx.update { it.copy(statusSsid = v) } }
        CheckRow("Signal", c.statusSignal, enabled = c.showStatus) { v -> ctx.update { it.copy(statusSignal = v) } }
        CheckRow("IP address", c.statusIp, enabled = c.showStatus) { v -> ctx.update { it.copy(statusIp = v) } }
        ToggleRow("Category chips", c.showCategories, subtitle = "Only shown once you've created a category") { v ->
            ctx.update { it.copy(showCategories = v) }
        }

        SectionHeader("App row")
        CycleRow(
            "App size",
            listOf(96 to "Extra small", 128 to "Small", 160 to "Medium", 192 to "Large"),
            c.appCardWidth,
        ) { v -> ctx.update { it.copy(appCardWidth = v) } }
        CycleRow("Card style", listOf(true to "Translucent", false to "Solid"), c.translucentCards) { v ->
            ctx.update { it.copy(translucentCards = v) }
        }
        CycleRow(
            "Spacing",
            listOf(10 to "Tight", 20 to "Normal", 32 to "Wide"),
            c.appSpacing,
        ) { v -> ctx.update { it.copy(appSpacing = v) } }
        CycleRow(
            "Focus zoom",
            listOf(100 to "Off", 105 to "Subtle", 110 to "Normal", 120 to "Large"),
            c.appFocusZoom,
        ) { v -> ctx.update { it.copy(appFocusZoom = v) } }
        CycleRow(
            "Corners",
            listOf(0 to "Square", 6 to "Slightly rounded", 12 to "Rounded", 24 to "Very rounded"),
            c.appCornerRadius,
        ) { v -> ctx.update { it.copy(appCornerRadius = v) } }
        CycleRow(
            "Row position",
            listOf(24 to "Low", 40 to "Normal", 96 to "High"),
            c.appRowBottom,
        ) { v -> ctx.update { it.copy(appRowBottom = v) } }
        ToggleRow("Show app name above row", c.showAppLabel, subtitle = "Without it, apps with a logo-only banner show no name") { v ->
            ctx.update { it.copy(showAppLabel = v) }
        }

        SectionHeader("Weather")
        ToggleRow("Show weather", c.showWeather, subtitle = if (c.weatherLat == null) "Set a city first" else null) { v ->
            ctx.update { it.copy(showWeather = v) }
        }
        SettingRow("City", value = c.weatherCity.ifEmpty { "Not set" }, onClick = { editCity = true })
        CycleRow("Units", listOf(false to "°C", true to "°F"), c.fahrenheit) { v -> ctx.update { it.copy(fahrenheit = v) } }

        SectionHeader("Photos")
        CycleRow(
            "Change wallpaper every",
            listOf(1 to "1 min", 5 to "5 min", 15 to "15 min", 60 to "1 hour", 24 * 60 to "1 day"),
            c.wallpaperMinutes,
        ) { v -> ctx.update { it.copy(wallpaperMinutes = v) } }
        CycleRow(
            "Screensaver photo duration",
            listOf(10 to "10 s", 20 to "20 s", 40 to "40 s", 90 to "90 s"),
            c.screensaverSeconds,
        ) { v -> ctx.update { it.copy(screensaverSeconds = v) } }
    }

    if (editCity) {
        TextEntryDialog(
            title = "Weather city",
            initial = c.weatherCity,
            hint = "e.g. Vancouver",
            onDismiss = { editCity = false },
            onDone = { query ->
                editCity = false
                scope.launch {
                    val place = WeatherApi.findCity(query.trim())
                    if (place == null) {
                        context.toast("Couldn't find \"$query\"")
                    } else {
                        ctx.update { it.copy(weatherCity = place.name, weatherLat = place.lat, weatherLon = place.lon, showWeather = true) }
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoriesSection(ctx: SectionContext) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var renaming by remember { mutableStateOf<Category?>(null) }

    Column {
        SettingRow("Add category", icon = Icons.Outlined.Add, onClick = { adding = true })
        if (ctx.config.categories.isNotEmpty()) SectionHeader("Your categories")
        ctx.config.categories.forEach { cat ->
            val count = ctx.config.appCategory.values.count { it == cat.id }
            SettingRow(cat.name, value = "$count apps", onClick = { editing = cat })
        }
        SectionHeader("Tip")
        SettingRow("Long-press an app on the home screen to put it in a category.", enabled = false, onClick = {})
    }

    if (adding) {
        TextEntryDialog("New category", hint = "e.g. Streaming", onDismiss = { adding = false }, onDone = { name ->
            adding = false
            if (name.isNotBlank()) {
                ctx.update { it.copy(categories = it.categories + Category(UUID.randomUUID().toString().take(8), name.trim())) }
            }
        })
    }
    renaming?.let { cat ->
        TextEntryDialog("Rename category", initial = cat.name, onDismiss = { renaming = null }, onDone = { name ->
            renaming = null
            if (name.isNotBlank()) {
                ctx.update { c -> c.copy(categories = c.categories.map { if (it.id == cat.id) it.copy(name = name.trim()) else it }) }
            }
        })
    }
    editing?.let { cat ->
        val index = ctx.config.categories.indexOf(cat)
        OptionsDialog(
            title = cat.name,
            options = listOf(
                MenuOption("Rename") { editing = null; renaming = cat },
                MenuOption("Move earlier") {
                    editing = null
                    if (index > 0) ctx.update { c -> c.copy(categories = c.categories.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                },
                MenuOption("Move later") {
                    editing = null
                    if (index < ctx.config.categories.lastIndex) {
                        ctx.update { c -> c.copy(categories = c.categories.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                    }
                },
                MenuOption("Delete") {
                    editing = null
                    ctx.update { c ->
                        c.copy(
                            categories = c.categories.filterNot { it.id == cat.id },
                            appCategory = c.appCategory.filterValues { it != cat.id },
                        )
                    }
                },
            ),
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TvSection(ctx: SectionContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tv = ctx.app.tv
    var sleepAt by remember { mutableStateOf(PowerTimers.sleepTimerAt(context)) }
    var confirmRestart by remember { mutableStateOf(false) }

    fun run(label: String, action: suspend () -> Boolean) {
        scope.launch { if (!action()) context.toast("$label failed. Check shell access under Home & system.") }
    }

    Column {
        SectionHeader("HDMI-CEC")
        SettingRow("Turn TV off", icon = Icons.Outlined.PowerSettingsNew, subtitle = "Sleeps the Chromecast; the TV follows over HDMI-CEC",
            onClick = { run("TV off") { tv.tvOff() } })
        SettingRow("Turn TV on / switch to this input", icon = Icons.Outlined.Tv, onClick = { run("TV on") { tv.tvOn() } })
        SettingRow("TV input menu", icon = Icons.Outlined.Input, subtitle = "Asks the TV to show its input picker. Not every TV supports this.",
            onClick = { run("Input menu") { tv.inputMenu() } })
        SettingRow("Reset HDMI-CEC", icon = Icons.Outlined.Sync,
            subtitle = "Try this if the TV stops responding to on/off. If it still doesn't, turn Anynet+ (Samsung) or your TV's CEC setting off and on.",
            onClick = { scope.launch { context.toast(if (tv.resetCec()) "HDMI-CEC restarted" else "Couldn't restart HDMI-CEC") } })

        SectionHeader("Timers")
        val minutesLeft = sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() + 1 }
        CycleRow(
            "Sleep timer",
            listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 90 to "90 min", 120 to "2 hours"),
            selected = if (minutesLeft == null) 0 else listOf(15, 30, 60, 90, 120).firstOrNull { it >= minutesLeft } ?: 120,
            subtitle = minutesLeft?.let { "Turns off in about $it min" },
        ) { minutes ->
            if (minutes == 0) PowerTimers.clearSleepTimer(context) else PowerTimers.setSleepTimer(context, minutes)
            sleepAt = PowerTimers.sleepTimerAt(context)
        }
        CycleRow(
            "Auto-off when idle",
            listOf(0 to "Off", 1 to "1 hour", 2 to "2 hours", 3 to "3 hours", 4 to "4 hours", 6 to "6 hours"),
            ctx.config.autoOffHours,
            subtitle = "Turns the TV off after this long with no remote presses. You get a 60-second warning.",
        ) { hours -> ctx.update { it.copy(autoOffHours = hours) } }

        SectionHeader("Chromecast")
        SettingRow("Restart Chromecast", icon = Icons.Outlined.RestartAlt, onClick = { confirmRestart = true })
    }

    if (confirmRestart) {
        ConfirmDialog("Restart the Chromecast?", "It takes about a minute to come back.", "Restart",
            onConfirm = { confirmRestart = false; run("Restart") { tv.restart() } },
            onDismiss = { confirmRestart = false })
    }
}

@Composable
private fun SystemSection(ctx: SectionContext) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = ctx.app
    val shellState by app.shell.state.collectAsStateWithLifecycle()
    var confirmHard by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var screensaver by remember { mutableStateOf(app.homeMode.isPhotoScreensaver()) }

    fun applyMode(mode: HomeMode) {
        busy = true
        scope.launch {
            val error = app.homeMode.apply(mode)
            busy = false
            context.toast(error ?: "Home mode: ${modeLabel(mode)}")
        }
    }

    Column {
        SectionHeader("Home button")
        CycleRow(
            "Home mode",
            listOf(HomeMode.OFF to modeLabel(HomeMode.OFF), HomeMode.SOFT to modeLabel(HomeMode.SOFT), HomeMode.HARD to modeLabel(HomeMode.HARD)),
            ctx.config.homeMode,
            subtitle = when (ctx.config.homeMode) {
                HomeMode.OFF -> "Google TV's launcher is home. This launcher is just an app."
                HomeMode.SOFT -> "Home opens this launcher. Google TV's launcher is still installed and may flash at boot."
                HomeMode.HARD -> "Google TV's launcher is disabled, so this is the only home screen."
            },
        ) { mode ->
            if (busy) return@CycleRow
            if (mode == HomeMode.HARD) confirmHard = true else applyMode(mode)
        }
        SettingRow(
            "Revert to Google TV launcher",
            icon = Icons.Outlined.Undo,
            subtitle = "Re-enables the stock launcher, stops intercepting Home and opens it",
            onClick = { confirmRevert = true },
        )

        SectionHeader("Screensaver")
        ToggleRow("Photo slideshow screensaver", screensaver, subtitle = "Off restores Google TV's ambient mode") { v ->
            if (app.homeMode.setPhotoScreensaver(v)) screensaver = v else context.toast("Needs WRITE_SECURE_SETTINGS; run scripts/deploy.ps1")
        }

        SectionHeader("Shell access")
        SettingRow(
            "Status",
            icon = Icons.Outlined.Sync,
            value = when (val s = shellState) {
                ShellClient.State.Unpaired -> "Not paired"
                ShellClient.State.Disconnected -> "Paired, idle"
                ShellClient.State.Connecting -> "Connecting…"
                is ShellClient.State.Connected -> "Connected"
                is ShellClient.State.Failed -> "Error"
            },
            subtitle = when (val s = shellState) {
                ShellClient.State.Unpaired -> "Run scripts\\pair-launcher.ps1 on the PC to set this up."
                is ShellClient.State.Failed -> s.message
                else -> "Used for TV power, Wi-Fi, installs and Home mode. Press to test."
            },
            onClick = {
                scope.launch {
                    val ok = app.shell.output("echo ok")?.trim() == "ok"
                    context.toast(if (ok) "Shell OK" else "Shell not reachable. Is Wireless debugging on?")
                }
            },
        )

        SectionHeader("Backup")
        SettingRow("Export settings", icon = Icons.Outlined.Backup, subtitle = "Pull to the PC with scripts\\config.ps1 pull", onClick = {
            val file = app.config.export()
            context.toast("Saved to ${file.absolutePath}")
        })
        SettingRow("Import settings", icon = Icons.Outlined.Restore, subtitle = "From the exported file (push one with scripts\\config.ps1 push)", onClick = {
            context.toast(if (app.config.import()) "Settings imported" else "No valid backup found at ${app.config.exportFile().absolutePath}")
        })

        SectionHeader("Security")
        if (ctx.config.pinHash == null) {
            SettingRow("Set settings PIN", icon = Icons.Outlined.Lock, subtitle = "Required to open Settings", onClick = { settingPin = true })
        } else {
            SettingRow("Change PIN", icon = Icons.Outlined.Lock, onClick = { settingPin = true })
            SettingRow("Remove PIN", onClick = { ctx.update { it.copy(pinHash = null) }; context.toast("PIN removed") })
        }
    }

    if (confirmHard) {
        ConfirmDialog(
            title = "Disable Google TV's launcher?",
            message = "This launcher becomes the only home screen. You can undo it here (Revert) or from the PC with scripts\\revert.ps1.",
            confirmLabel = "Disable it",
            onConfirm = { confirmHard = false; applyMode(HomeMode.HARD) },
            onDismiss = { confirmHard = false },
        )
    }
    if (confirmRevert) {
        ConfirmDialog(
            title = "Go back to Google TV's launcher?",
            message = "Your layout is kept, so you can switch back any time by opening this app.",
            confirmLabel = "Revert",
            onConfirm = {
                confirmRevert = false
                scope.launch { app.homeMode.revertToStock()?.let { context.toast(it) } }
            },
            onDismiss = { confirmRevert = false },
        )
    }
    if (settingPin) {
        PinDialog("New PIN (4–8 digits)", onDismiss = { settingPin = false }, onDone = { pin ->
            settingPin = false
            ctx.update { it.copy(pinHash = Pin.hash(pin)) }
            context.toast("PIN set")
        })
    }
}

private fun modeLabel(mode: HomeMode) = when (mode) {
    HomeMode.OFF -> "Google TV"
    HomeMode.SOFT -> "This launcher"
    HomeMode.HARD -> "This launcher only"
}
