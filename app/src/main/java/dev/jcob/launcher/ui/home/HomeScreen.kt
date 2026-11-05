package dev.jcob.launcher.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.data.CardStyle
import dev.jcob.launcher.data.LaunchableApp
import dev.jcob.launcher.data.LauncherConfig
import dev.jcob.launcher.data.Photos
import dev.jcob.launcher.data.Weather
import dev.jcob.launcher.data.WeatherApi
import dev.jcob.launcher.data.WeatherKind
import dev.jcob.launcher.data.WifiStatus
import dev.jcob.launcher.data.moved
import dev.jcob.launcher.data.orderedApps
import dev.jcob.launcher.data.readWifiStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onLaunch: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWifi: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as LauncherApp
    val allApps by app.apps.apps.collectAsStateWithLifecycle()
    val config by app.config.config.collectAsStateWithLifecycle()
    val systemSettings = remember {
        app.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
            ?: "com.android.tv.settings"
    }

    var category by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var optionsFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var quickMenu by remember { mutableStateOf(false) }

    val visible = remember(allApps, config, category) {
        orderedApps(allApps, config).filter { category == null || config.appCategory[it.packageName] == category }
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(intervalMillis = config.wallpaperMinutes.coerceAtLeast(1) * 60_000L)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.25f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    )
                )
        )
        TopBar(
            config = config,
            onPower = { quickMenu = true },
            onWifi = onOpenWifi,
            onSettings = { onLaunch(Intent(Settings.ACTION_SETTINGS)) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 48.dp),
        )
        if (config.showClock || config.showDate || config.showWeather) {
            ClockAndWeather(config, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = config.appRowBottom.dp)) {
            if (config.showCategories && config.categories.isNotEmpty()) {
                CategoryChips(config, selected = category, onSelect = { category = it })
            }
            AppRow(
                apps = visible,
                config = config,
                moving = moving,
                // The system Settings tile opens this launcher's settings; the top bar gear opens the system's.
                onLaunch = { if (it.packageName == systemSettings) onOpenSettings() else onLaunch(it.launchIntent) },
                onOptions = { optionsFor = it },
                onMove = { pkg, delta -> app.config.update { it.moved(allApps, pkg, delta) } },
                onMoveDone = { moving = null },
            )
        }
    }

    optionsFor?.let { target ->
        AppOptionsDialog(
            app = target,
            config = config,
            onMove = { moving = target.packageName },
            onDismiss = { optionsFor = null },
        )
    }
    if (quickMenu) QuickMenu(onDismiss = { quickMenu = false }, onOpenWifi = onOpenWifi)
}

@Composable
private fun Wallpaper(intervalMillis: Long) {
    val context = LocalContext.current
    val photos = remember { Photos.list(context).shuffled() }
    if (photos.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF2B2A33), Color(0xFF101014))))
        )
        return
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos, intervalMillis) {
        while (true) {
            delay(intervalMillis)
            index = (index + 1) % photos.size
        }
    }
    Crossfade(targetState = photos[index], animationSpec = tween(1500), label = "wallpaper") { url ->
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TopBar(
    config: LauncherConfig,
    onPower: () -> Unit,
    onWifi: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher = context.applicationContext as LauncherApp
    var status by remember { mutableStateOf<WifiStatus?>(null) }
    LaunchedEffect(config.showStatus) {
        var shellSsid: String? = null
        while (config.showStatus) {
            val read = runCatching { readWifiStatus(context) }.getOrNull()
            if (read?.connected == true && read.ssid == null && shellSsid == null && launcher.shell.isPaired) {
                shellSsid = launcher.wifi.connectedSsid()
            }
            status = read?.let { if (it.ssid == null && it.connected) it.copy(ssid = shellSsid) else it }
            delay(15_000)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Icons.Outlined.PowerSettingsNew, "TV and power", onPower)
        RoundIconButton(if (status?.connected == false) Icons.Outlined.WifiOff else Icons.Outlined.Wifi, "Wi-Fi", onWifi)
        RoundIconButton(Icons.Outlined.Settings, "Settings", onSettings)
        val s = status
        if (config.showStatus && s != null) {
            val ip = s.ip?.takeIf { config.statusIp }
            val text = when {
                s.ethernet -> listOfNotNull("Ethernet", ip).joinToString("  ·  ")
                !s.enabled -> "Wi-Fi off"
                !s.connected -> "Not connected"
                else -> listOfNotNull(
                    s.ssid?.takeIf { config.statusSsid },
                    "▂▄▆█".take(s.bars.coerceAtLeast(1)).takeIf { config.statusSignal },
                    ip,
                ).joinToString("  ·  ")
            }
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.25f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ClockAndWeather(config: LauncherConfig, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(config.showWeather, config.weatherLat, config.weatherLon, config.fahrenheit) {
        val lat = config.weatherLat
        val lon = config.weatherLon
        weather = null
        while (config.showWeather && lat != null && lon != null) {
            weather = WeatherApi.current(lat, lon, config.fahrenheit) ?: weather
            delay(30 * 60_000L)
        }
    }
    val timePattern = if (config.use24h) "HH:mm" else "h:mm"
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (config.showClock) {
            Text(
                remember(now, timePattern) { SimpleDateFormat(timePattern, Locale.getDefault()).format(now) },
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
            )
        }
        val lineColor = Color.White.copy(alpha = 0.85f)
        if (config.showDate) {
            Text(
                remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) },
                style = MaterialTheme.typography.titleMedium,
                color = lineColor,
            )
        }
        weather?.takeIf { config.showWeather }?.let { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                weatherIcon(w)?.let { Icon(it, contentDescription = null, tint = lineColor, modifier = Modifier.padding(end = 8.dp).size(22.dp)) }
                Text("${w.temperature}°  ${w.description}".trim(), style = MaterialTheme.typography.titleMedium, color = lineColor)
            }
        }
    }
}

private fun weatherIcon(w: Weather): ImageVector? = when (w.kind) {
    WeatherKind.CLEAR -> if (w.isDay) Icons.Outlined.WbSunny else Icons.Outlined.ModeNight
    WeatherKind.PARTLY_CLOUDY -> if (w.isDay) Icons.Outlined.FilterDrama else Icons.Outlined.NightsStay
    WeatherKind.OVERCAST -> Icons.Outlined.Cloud
    WeatherKind.FOG -> Icons.Outlined.Dehaze
    WeatherKind.DRIZZLE -> Icons.Outlined.WaterDrop
    WeatherKind.RAIN -> Icons.Outlined.Umbrella
    WeatherKind.SNOW -> Icons.Outlined.AcUnit
    WeatherKind.THUNDERSTORM -> Icons.Outlined.Thunderstorm
    WeatherKind.UNKNOWN -> null
}

@Composable
private fun CategoryChips(config: LauncherConfig, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 48.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item { Chip("All", selected == null) { onSelect(null) } }
        items(config.categories, key = { it.id }) { cat -> Chip(cat.name, selected == cat.id) { onSelect(cat.id) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.25f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

@Composable
private fun AppRow(
    apps: List<LaunchableApp>,
    config: LauncherConfig,
    moving: String?,
    onLaunch: (LaunchableApp) -> Unit,
    onOptions: (LaunchableApp) -> Unit,
    onMove: (String, Int) -> Unit,
    onMoveDone: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedLabel by remember { mutableStateOf("") }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !initialFocusDone) {
            listState.scrollToItem(0)
            // The first frame may not have attached the requester yet.
            repeat(5) {
                if (runCatching { focusRequesters[apps.first().packageName]?.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    return@LaunchedEffect
                }
                delay(50)
            }
        }
    }
    // Keep focus on the card being moved as it changes position.
    LaunchedEffect(moving, apps) {
        moving?.let { pkg -> runCatching { focusRequesters[pkg]?.requestFocus() } }
    }

    // When the row fits on screen it's centred, so line the label up with the first card.
    val density = LocalDensity.current
    val labelStart by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            if (first == null || first.index != 0 || listState.canScrollForward || listState.canScrollBackward) 48.dp
            else with(density) { (first.offset - info.viewportStartOffset).toDp() }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (moving != null || config.showAppLabel) {
            Text(
                if (moving != null) "Moving: use ◀ ▶, press OK when done" else focusedLabel,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = labelStart, bottom = 12.dp),
            )
        }
        if (apps.isEmpty()) {
            Text(
                "No apps here. Long-press an app or open Settings → Apps to add some.",
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 48.dp),
            )
            return@Column
        }
        val zoom = config.appFocusZoom / 100f
        // Leave room above and below for the focused card's zoom so the row doesn't clip it.
        val zoomRoom = (config.appCardWidth * 9f / 16 * (zoom - 1) / 2).dp + 4.dp
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(config.appSpacing.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = zoomRoom.coerceAtLeast(12.dp)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(apps, key = { it.packageName }) { app ->
                val requester = focusRequesters.getOrPut(app.packageName) { FocusRequester() }
                val isMoving = moving == app.packageName
                var focused by remember { mutableStateOf(false) }
                AppCard(
                    app = app,
                    width = config.appCardWidth.dp,
                    zoom = zoom,
                    shape = RoundedCornerShape(config.appCornerRadius.dp),
                    style = config.cardStyle,
                    focused = focused,
                    highlighted = isMoving,
                    onClick = { if (moving != null) onMoveDone() else onLaunch(app) },
                    onLongClick = { if (moving == null) onOptions(app) },
                    modifier = Modifier
                        // Draw the zoomed card over its neighbours when spacing is tight.
                        .zIndex(if (focused) 1f else 0f)
                        .focusRequester(requester)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) focusedLabel = app.label
                            else if (focusedLabel == app.label) focusedLabel = ""
                        }
                        .onPreviewKeyEvent { event ->
                            if (!isMoving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> { onMove(app.packageName, -1); true }
                                Key.DirectionRight -> { onMove(app.packageName, 1); true }
                                Key.DirectionUp, Key.DirectionDown -> true
                                Key.Back -> { onMoveDone(); true }
                                else -> false
                            }
                        },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AppCard(
    app: LaunchableApp,
    width: Dp,
    zoom: Float,
    shape: Shape,
    style: CardStyle,
    focused: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = width * 9 / 16
    // Translucent and logo-only cards let the wallpaper show through, so dim them until focused.
    val contentAlpha = when {
        focused || style == CardStyle.SOLID -> 1f
        style == CardStyle.LOGO -> 0.85f
        else -> 0.7f
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = zoom),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = when (style) {
            CardStyle.SOLID -> ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF2B2A33),
                focusedContainerColor = Color(0xFF3A3944),
            )
            CardStyle.TRANSLUCENT -> ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.3f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f),
            )
            CardStyle.LOGO -> ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
            )
        },
        border = when (style) {
            CardStyle.SOLID -> ClickableSurfaceDefaults.border()
            CardStyle.TRANSLUCENT -> ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), shape = shape),
            )
            CardStyle.LOGO -> ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), shape = shape),
            )
        },
        modifier = modifier.size(width, height),
    ) {
        when {
            style == CardStyle.LOGO -> Image(
                app.logo,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                alpha = contentAlpha,
                modifier = Modifier.fillMaxSize().padding(horizontal = width * 0.15f, vertical = height * 0.15f),
            )
            app.banner != null -> Image(
                app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                alpha = contentAlpha,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(app.icon, contentDescription = null, alpha = contentAlpha, modifier = Modifier.size(width / 4))
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
                )
            }
        }
        // Inside the Surface so the border scales with the focused card instead of hiding behind it.
        if (highlighted) Box(Modifier.fillMaxSize().border(3.dp, Color.White, shape))
    }
}
