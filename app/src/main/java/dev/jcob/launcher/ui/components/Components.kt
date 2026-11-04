package dev.jcob.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

val Ink = Color(0xFFF2EFE9)
val InkDim = Color(0xFFF2EFE9).copy(alpha = 0.65f)
val Panel = Color(0xFF1C1B1F)
val PanelRaised = Color(0xFF2B2A33)

/** A full-width focusable row: the building block of every settings list. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Ink,
            focusedContainerColor = Ink,
            focusedContentColor = Panel,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = InkDim.copy(alpha = 0.4f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        modifier = Modifier.semantics(mergeDescendants = true) { role = Role.Switch; toggleableState = ToggleableState(checked) },
        onClick = { onToggle(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp)) },
    )
}

/** An indented checkbox row for sub-options of the setting above it. */
@Composable
fun CheckRow(title: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        enabled = enabled,
        modifier = Modifier
            .padding(start = 40.dp)
            .semantics(mergeDescendants = true) { role = Role.Checkbox; toggleableState = ToggleableState(checked) },
        onClick = { onToggle(!checked) },
        trailing = {
            // Follow the row's content colour so the box stays visible when the row inverts on focus.
            val content = LocalContentColor.current
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = content,
                    uncheckedColor = content,
                    checkmarkColor = if (content == Panel) Ink else Panel,
                    disabledCheckedColor = content,
                    disabledUncheckedColor = content,
                    disabledIndeterminateColor = content,
                ),
                modifier = Modifier.padding(start = 16.dp),
            )
        },
    )
}

/** Clicking cycles to the next option; simplest possible picker for a D-pad. */
@Composable
fun <T> CycleRow(title: String, options: List<Pair<T, String>>, selected: T, subtitle: String? = null, onSelect: (T) -> Unit) {
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    SettingRow(
        title = title,
        subtitle = subtitle,
        value = options[index].second,
        onClick = { onSelect(options[(index + 1) % options.size].first) },
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = InkDim,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun TvDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // A dialog opened by a long press appears while OK is still held, and releasing it would
    // click the first option. Ignore OK releases until a fresh OK press starts inside the dialog
    // (held-key repeats don't count).
    var okPressedHere by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.type) {
                        KeyEventType.KeyDown -> { if (event.nativeKeyEvent.repeatCount == 0) okPressedHere = true; !okPressedHere }
                        KeyEventType.KeyUp -> !okPressedHere
                        else -> false
                    }
                }
                .widthIn(min = 420.dp, max = 640.dp)
                .background(Panel, RoundedCornerShape(20.dp))
                .border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) { content() }
    }
}

data class MenuOption(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

@Composable
fun OptionsDialog(title: String, options: List<MenuOption>, onDismiss: () -> Unit, subtitle: String? = null) {
    val first = remember { FocusRequester() }
    TvDialog(onDismiss) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
            if (subtitle != null) Text(subtitle, color = InkDim, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(options.size) { i ->
                    val option = options[i]
                    SettingRow(
                        title = option.label,
                        icon = option.icon,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = option.onClick,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OptionsDialog(
        title = title,
        subtitle = message,
        options = listOf(MenuOption("Cancel", onClick = onDismiss), MenuOption(confirmLabel, onClick = onConfirm)),
        onDismiss = onDismiss,
    )
}

/** Text entry. Focusing the field brings up the TV's on-screen keyboard. */
@Composable
fun TextEntryDialog(
    title: String,
    initial: String = "",
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    TvDialog(onDismiss) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.padding(bottom = 16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(PanelRaised, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                if (text.isEmpty()) Text(hint, color = InkDim)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 20.sp),
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow("Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                SettingRow("OK", modifier = Modifier.weight(1f), onClick = { onDone(text) })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

/** Number pad for remotes without number keys. */
@Composable
fun PinDialog(title: String, onDone: (String) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var pin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    TvDialog(onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(
                if (pin.isEmpty()) "• • • •" else "●".repeat(pin.length).toList().joinToString(" "),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (error != null) Text(error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(bottom = 8.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
            keys.chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    chunk.forEachIndexed { col, key ->
                        Surface(
                            onClick = {
                                when (key) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length >= 4) onDone(pin)
                                    else -> if (pin.length < 8) pin += key
                                }
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = PanelRaised, contentColor = Ink,
                                focusedContainerColor = Ink, focusedContentColor = Panel,
                            ),
                            modifier = Modifier
                                .size(width = 88.dp, height = 56.dp)
                                .then(if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                                Text(key, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
