package dev.jcob.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val Scheme = darkColorScheme(
    primary = Color(0xFFF2EFE9),
    onPrimary = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFF2EFE9),
    background = Color.Black,
    onBackground = Color(0xFFF2EFE9),
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
