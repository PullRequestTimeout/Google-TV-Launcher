package dev.jcob.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.data.LauncherConfig
import dev.jcob.launcher.ui.components.Ink
import dev.jcob.launcher.ui.components.InkDim
import dev.jcob.launcher.ui.components.Panel
import dev.jcob.launcher.ui.components.SettingRow

enum class Section(val title: String, val icon: ImageVector) {
    Customise("Customise", Icons.Outlined.Palette),
    Apps("Apps", Icons.Outlined.Apps),
    Categories("Categories", Icons.Outlined.Folder),
    Wifi("Wi-Fi", Icons.Outlined.Wifi),
    Tv("TV & power", Icons.Outlined.Tv),
    Buttons("Remote buttons", Icons.Outlined.SettingsRemote),
    System("Home & system", Icons.Outlined.Home),
}

/** Shared bits every section needs. */
class SectionContext(val app: LauncherApp, val config: LauncherConfig) {
    fun update(transform: (LauncherConfig) -> LauncherConfig) = app.config.update(transform)
}

@Composable
fun SettingsScreen(initial: Section = Section.Customise) {
    val app = LocalContext.current.applicationContext as LauncherApp
    val config by app.config.config.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(initial) }
    val navRequesters = remember { Section.entries.associateWith { FocusRequester() } }
    val content = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = SectionContext(app, config)

    Row(Modifier.fillMaxSize().background(Panel)) {
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(Ink.copy(alpha = 0.04f))
                .padding(top = 40.dp, start = 24.dp, end = 16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(start = 20.dp, bottom = 24.dp))
            Section.entries.forEach { s ->
                SettingRow(
                    title = s.title,
                    icon = s.icon,
                    // Moving focus over a section opens it, and ▶ jumps to its first row
                    // (default focus search would pick whichever row is level with the menu item).
                    modifier = Modifier
                        .focusRequester(navRequesters.getValue(s))
                        .onFocusChanged {
                            if (it.isFocused && section != s) {
                                section = s
                                scope.launch { listState.scrollToItem(0) }
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                scope.launch {
                                    listState.scrollToItem(0)
                                    runCatching { content.requestFocus() }
                                }
                                true
                            } else false
                        },
                    value = if (s == section) "›" else null,
                    onClick = { section = s },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 32.dp, end = 48.dp, top = 40.dp, bottom = 48.dp),
                modifier = Modifier
                    // Requesting focus on this non-focusable group focuses its first focusable row.
                    .focusRequester(content)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            runCatching { navRequesters.getValue(section).requestFocus() }
                            true
                        } else false
                    },
            ) {
                item(key = "title-${section.name}") {
                    Text(section.title, style = MaterialTheme.typography.headlineSmall, color = InkDim, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                }
                when (section) {
                    Section.Customise -> customiseSection(ctx)
                    Section.Apps -> appsSection(ctx)
                    Section.Categories -> categoriesSection(ctx)
                    Section.Wifi -> wifiSection(ctx)
                    Section.Tv -> tvSection(ctx)
                    Section.Buttons -> buttonsSection(ctx)
                    Section.System -> systemSection(ctx)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { navRequesters.getValue(initial).requestFocus() } }
}

/** Lets section builders (LazyListScope extensions) host a single composable block. */
fun LazyListScope.block(key: String, content: @Composable () -> Unit) = item(key = key) { content() }
