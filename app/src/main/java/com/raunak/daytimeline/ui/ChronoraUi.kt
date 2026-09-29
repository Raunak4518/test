package com.raunak.daytimeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Chronora's design system: one palette (light + dark), semantic status colours, spacing and the
 * shared building blocks every screen uses, so the whole app looks and behaves the same.
 */
object Palette {
    val sage = Color(0xFF55786A)
    val sageLight = Color(0xFFA8C7B7)
    val ink = Color(0xFF17221E)
    val paper = Color(0xFFF4F1E9)
    val card = Color(0xFFFFFDF8)
    val night = Color(0xFF101815)
    val nightCard = Color(0xFF1B2723)
}

/** Colours with a meaning: success / warning / danger and the dark "hero" card used for headline numbers. */
@Immutable
data class StatusColors(
    val good: Color,
    val warn: Color,
    val bad: Color,
    val hero: Color,
    val onHero: Color,
    val heroMuted: Color,
    val heroAccent: Color
)

val LightStatus = StatusColors(Color(0xFF2E7D32), Color(0xFFB26A00), Color(0xFFC62828), Palette.ink, Color.White, Color(0xFFB9CCC2), Palette.sageLight)
val DarkStatus = StatusColors(Color(0xFF81C995), Color(0xFFFFB74D), Color(0xFFF28B82), Color(0xFF2E4A3D), Color.White, Color(0xFFB9CCC2), Palette.sageLight)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object Chronora {
    val colors: StatusColors @Composable @ReadOnlyComposable get() = LocalStatusColors.current
    val muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

object Spacing {
    val screen = 16.dp
    val gap = 12.dp
    val inner = 14.dp
    val small = 6.dp
}

val ChronoraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val ChronoraLight = lightColorScheme(
    primary = Palette.sage, onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E5DC), onPrimaryContainer = Palette.ink,
    secondary = Color(0xFF6B7F76), secondaryContainer = Color(0xFFE3EAE6), onSecondaryContainer = Palette.ink,
    tertiary = Color(0xFF8A6A3E),
    background = Palette.paper, onBackground = Palette.ink,
    surface = Palette.card, onSurface = Palette.ink,
    surfaceVariant = Color(0xFFE6E8E2), onSurfaceVariant = Color(0xFF5E6B65),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Palette.card, surfaceContainer = Color(0xFFF8F6F0),
    surfaceContainerHigh = Color(0xFFFAF8F2), surfaceContainerHighest = Palette.card,
    outline = Color(0xFF8C9892), outlineVariant = Color(0xFFD5DAD4),
    error = Color(0xFFC62828), errorContainer = Color(0xFFFDE2E0), onErrorContainer = Color(0xFF5F1412)
)

val ChronoraDark = darkColorScheme(
    primary = Palette.sageLight, onPrimary = Palette.ink,
    primaryContainer = Color(0xFF34503F), onPrimaryContainer = Color(0xFFD4E5DC),
    secondary = Color(0xFFB4C4BC), secondaryContainer = Color(0xFF2C3833), onSecondaryContainer = Color(0xFFDCE5E0),
    tertiary = Color(0xFFE0C08F),
    background = Palette.night, onBackground = Color(0xFFE3E8E5),
    surface = Palette.nightCard, onSurface = Color(0xFFE3E8E5),
    surfaceVariant = Color(0xFF2A3631), onSurfaceVariant = Color(0xFFAFBDB6),
    surfaceContainerLowest = Color(0xFF0C120F), surfaceContainerLow = Color(0xFF151F1B), surfaceContainer = Palette.nightCard,
    surfaceContainerHigh = Color(0xFF22302A), surfaceContainerHighest = Color(0xFF2A3833),
    outline = Color(0xFF7D8B85), outlineVariant = Color(0xFF34423C),
    error = Color(0xFFF28B82), errorContainer = Color(0xFF5C1E1A), onErrorContainer = Color(0xFFFDE2E0)
)

/** Theme without any stored preference — for screens that run before unlock or must always be dark. */
@Composable
fun ChronoraThemeBase(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) ChronoraDark else ChronoraLight, shapes = ChronoraShapes, content = content)
    }
}

// ------------------------------------------------------------------ building blocks

/** Standard scrolling screen body: 16 dp padding, 12 dp between cards. */
@Composable
fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) =
    LazyColumn(modifier, contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.gap), content = content)

/** A titled card: the one container used for every section. */
@Composable
fun SectionCard(title: String, subtitle: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.inner), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
                action?.invoke()
            }
            content()
        }
    }
}

/** The dark headline card (daily score, streaks, today's summary). */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Chronora.colors.hero, contentColor = Chronora.colors.onHero), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(Spacing.small), content = content)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        action?.invoke()
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
    }
}

/** A stat inside its own small card, for rows of numbers. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Stat(label, value)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Chronora.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) { Text("−") }
        Text(value, Modifier.widthIn(min = 64.dp).padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) { Text("+") }
    }
}

/** Editable list of short values: remove with ×, add with the field below. */
@Composable
fun ListEditor(title: String, values: List<String>, numeric: Boolean = false, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        values.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onChange(values.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Close, "Remove $v") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, label = { Text("Add") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
            )
            IconButton(onClick = {
                val v = input.trim()
                if (v.isNotEmpty() && (!numeric || v.toIntOrNull() != null)) { onChange(values + v); input = "" }
            }) { Icon(Icons.Default.Add, "Add") }
        }
    }
}

/** Status line with an icon-like prefix so meaning never depends on colour alone. */
@Composable
fun StatusText(text: String, ok: Boolean?, modifier: Modifier = Modifier) {
    val (prefix, color) = when (ok) {
        true -> "✓ " to Chronora.colors.good
        false -> "✗ " to Chronora.colors.bad
        null -> "! " to Chronora.colors.warn
    }
    Text(prefix + text, modifier, color = color, style = MaterialTheme.typography.bodySmall)
}

/** The one top bar for full-screen pages: back arrow, title, optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronoraTopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** A full-screen page opened on top of another (editors, suites): same top bar and background everywhere. */
@Composable
fun FullScreenPage(title: String, onClose: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                ChronoraTopBar(title, onClose, subtitle, actions)
                content()
            }
        }
    }
}
