package com.burhannexus.voicestudio.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Burhan Nexus AI Voice Studio palette
val BnBackground = Color(0xFFF7EFE8)
val BnPrimary = Color(0xFFFC6454)
val BnSecondary = Color(0xFFBAF5D9)
val BnAccent = Color(0xFFF3E16B)
val BnText = Color(0xFF282828)

private val LightColors = lightColorScheme(
    primary = BnPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = BnText,
    secondary = Color(0xFF2F7D5C),
    onSecondary = Color.White,
    secondaryContainer = BnSecondary,
    onSecondaryContainer = BnText,
    tertiary = Color(0xFF7A6C00),
    onTertiary = Color.White,
    tertiaryContainer = BnAccent,
    onTertiaryContainer = BnText,
    background = BnBackground,
    onBackground = BnText,
    surface = BnBackground,
    onSurface = BnText,
    surfaceVariant = Color(0xFFEDE0D5),
    onSurfaceVariant = Color(0xFF5B524C),
    outline = Color(0xFFB8A99D),
    outlineVariant = Color(0xFFDCCFC4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFAF6),
    surfaceContainer = Color(0xFFFFF7F1),
    surfaceContainerHigh = Color(0xFFFBF1E9),
    surfaceContainerHighest = Color(0xFFFFFBF8),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = BnPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7A2A20),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = BnSecondary,
    onSecondary = BnText,
    secondaryContainer = Color(0xFF1F4D3A),
    onSecondaryContainer = BnSecondary,
    tertiary = BnAccent,
    onTertiary = BnText,
    tertiaryContainer = Color(0xFF5A5000),
    onTertiaryContainer = BnAccent,
    background = Color(0xFF1C1917),
    onBackground = BnBackground,
    surface = Color(0xFF1C1917),
    onSurface = BnBackground,
    surfaceVariant = Color(0xFF3A342F),
    onSurfaceVariant = Color(0xFFD5C7BB),
    outline = Color(0xFF8E8177),
    outlineVariant = Color(0xFF4A423C),
    surfaceContainerLowest = Color(0xFF151210),
    surfaceContainerLow = Color(0xFF24201D),
    surfaceContainer = Color(0xFF292421),
    surfaceContainerHigh = Color(0xFF332D29),
    surfaceContainerHighest = Color(0xFF2C2724),
)

@Composable
fun VoiceStudioTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background, content = content)
    }
}

@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun BnCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Badge(text: String, color: Color, textColor: Color = BnText) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LabelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

fun fmtDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun fmtEta(sec: Long): String = when {
    sec < 0 -> "andaza lag raha hai..."
    sec < 60 -> "$sec sec"
    else -> "${sec / 60} min ${sec % 60} sec"
}

fun fmtBytes(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1024L * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    b >= 1024L -> "%d KB".format(b / 1024)
    else -> "$b B"
}
