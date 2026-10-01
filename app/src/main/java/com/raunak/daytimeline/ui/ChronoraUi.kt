package com.raunak.daytimeline.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs

/**
 * Chronora's design system. Every tab has its own accent colour (Today indigo, Plan violet, Focus coral,
 * Campus teal, You amber) applied with [SectionTheme], on a calm neutral base, so screens are easy to tell apart.
 */
object Palette {
    val indigo = Color(0xFF4F5BD5)
    val violet = Color(0xFF7C5CE6)
    val coral = Color(0xFFEF6A45)
    val teal = Color(0xFF0E9F9A)
    val amber = Color(0xFFDB8F12)
    val rose = Color(0xFFE5486B)
    val sky = Color(0xFF2F8FE0)
    val green = Color(0xFF22A06B)
    val ink = Color(0xFF151827)
    val paper = Color(0xFFF5F6FA)
    val card = Color(0xFFFFFFFF)
    val night = Color(0xFF0D0F15)
    val nightCard = Color(0xFF181B24)
    // Kept for older call sites.
    val sage = indigo
    val sageLight = Color(0xFF9AA3FF)
}

/** Colours with a meaning: success / warning / danger and the "hero" card used for headline numbers. */
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

val LightStatus = StatusColors(Color(0xFF1E9E5A), Color(0xFFD98A00), Color(0xFFE0434C), Color(0xFF1F2340), Color.White, Color.White.copy(alpha = .74f), Color(0xFFFFC857))
val DarkStatus = StatusColors(Color(0xFF5FD39A), Color(0xFFFFC04D), Color(0xFFFF7A82), Color(0xFF262B52), Color.White, Color.White.copy(alpha = .74f), Color(0xFFFFC857))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }
private val LocalDark = staticCompositionLocalOf { false }

object Chronora {
    val colors: StatusColors @Composable @ReadOnlyComposable get() = LocalStatusColors.current
    val muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val dark: Boolean @Composable @ReadOnlyComposable get() = LocalDark.current
}

object Spacing {
    val screen = 16.dp
    val gap = 12.dp
    val inner = 16.dp
    val small = 8.dp
}

val ChronoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val Base = Typography()
val ChronoraType = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent, onPrimary = Color.White,
    primaryContainer = lerp(accent, Color.White, .84f), onPrimaryContainer = lerp(accent, Palette.ink, .55f),
    secondary = lerp(accent, Color(0xFF5B6075), .6f), secondaryContainer = lerp(accent, Color.White, .9f), onSecondaryContainer = Palette.ink,
    tertiary = Palette.coral,
    background = Palette.paper, onBackground = Palette.ink,
    surface = Palette.card, onSurface = Palette.ink,
    surfaceVariant = Color(0xFFECEEF4), onSurfaceVariant = Color(0xFF636A7E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFD), surfaceContainer = Color(0xFFF2F3F8),
    surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFA2A8BA), outlineVariant = Color(0xFFE3E5EE),
    error = Color(0xFFE0434C), errorContainer = Color(0xFFFFE3E5), onErrorContainer = Color(0xFF6B1218)
)

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = lerp(accent, Color.White, .25f), onPrimary = Color(0xFF0D0F15),
    primaryContainer = lerp(accent, Palette.night, .62f), onPrimaryContainer = lerp(accent, Color.White, .75f),
    secondary = lerp(accent, Color(0xFFB8BDD0), .6f), secondaryContainer = Color(0xFF262A36), onSecondaryContainer = Color(0xFFE4E6EF),
    tertiary = Color(0xFFFF9A7A),
    background = Palette.night, onBackground = Color(0xFFE6E8F0),
    surface = Palette.nightCard, onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF242836), onSurfaceVariant = Color(0xFFA6ABBD),
    surfaceContainerLowest = Color(0xFF0A0C11), surfaceContainerLow = Color(0xFF13161E), surfaceContainer = Palette.nightCard,
    surfaceContainerHigh = Color(0xFF1E2230), surfaceContainerHighest = Color(0xFF242836),
    outline = Color(0xFF6E7488), outlineVariant = Color(0xFF2C3040),
    error = Color(0xFFFF7A82), errorContainer = Color(0xFF5A1C22), onErrorContainer = Color(0xFFFFE3E5)
)

// Older code reads these directly.
val ChronoraLight = lightScheme(Palette.indigo)
val ChronoraDark = darkScheme(Palette.indigo)

/** Theme without any stored preference — for screens that run before unlock or must always be dark. */
@Composable
fun ChronoraThemeBase(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus, LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) ChronoraDark else ChronoraLight, shapes = ChronoraShapes, typography = ChronoraType, content = content)
    }
}

/** Re-tints everything inside with one accent, so each tab has its own colour. */
@Composable
fun SectionTheme(accent: Color, content: @Composable () -> Unit) {
    val dark = LocalDark.current
    val base = LocalStatusColors.current
    val hero = if (dark) lerp(accent, Color.Black, .5f) else lerp(accent, Color.Black, .28f)
    CompositionLocalProvider(LocalStatusColors provides base.copy(hero = hero)) {
        MaterialTheme(colorScheme = if (dark) darkScheme(accent) else lightScheme(accent), shapes = ChronoraShapes, typography = ChronoraType, content = content)
    }
}

// ------------------------------------------------------------------ feedback

/** A short confirmation shown at the bottom of the app, with an optional Undo. */
data class FeedbackMessage(val text: String, val undo: (() -> Unit)? = null)

/** Anyone can post; the app shell shows them as snackbars. */
object Feedback {
    val messages = MutableSharedFlow<FeedbackMessage>(extraBufferCapacity = 8)
    fun show(text: String, undo: (() -> Unit)? = null) { messages.tryEmit(FeedbackMessage(text, undo)) }
}

// ------------------------------------------------------------------ building blocks

/** Standard scrolling screen body. */
@Composable
fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) =
    LazyColumn(modifier, contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.gap), content = content)

/** Small rounded icon tile in the accent colour (or [color]). */
@Composable
fun IconBadge(icon: ImageVector, color: Color = MaterialTheme.colorScheme.primary, size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * .56f))
    }
}

/** A titled card: the one container used for every section. */
@Composable
fun SectionCard(title: String, subtitle: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (Chronora.dark) 0.dp else 1.dp)) {
        Column(Modifier.padding(Spacing.inner), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { IconBadge(icon); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null && subtitle.length <= 48) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
                }
                action?.invoke()
            }
            content()
        }
    }
}

/** The bold headline card: a gradient in the section's accent. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hero = Chronora.colors.hero
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(listOf(hero, lerp(hero, accent, .75f), lerp(accent, Color.White, .08f))))) {
        CompositionLocalProvider(LocalContentColor provides Chronora.colors.onHero) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Spacing.small), content = content)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
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
    Card(modifier, shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
        Column(Modifier.padding(12.dp)) {
            Stat(label, value)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("✦", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (body.length <= 70) Text(body, color = Chronora.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else Chronora.muted)
        Switch(checked, onChange, enabled = enabled)
    }
}

/**
 * A setting with a value: shows the value as a chip. Tap it to open a picker with a big readout and a ruler
 * you drag left or right; each tick is one step. No plus/minus buttons anywhere.
 */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Icon(Icons.Default.UnfoldMore, null, Modifier.size(16.dp).padding(start = 2.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    if (open) ValueSheet(label, value, onMinus, onPlus) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueSheet(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Chronora.muted, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            RulerDial(onMinus, onPlus, Modifier.fillMaxWidth())
            Button(onClick = close, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

/** Drag the ruler: left increases, right decreases, with a haptic tick per step. */
@Composable
fun RulerDial(onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var carry by remember { mutableFloatStateOf(0f) }
    val tick = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.height(76.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
        .pointerInput(Unit) {
            detectHorizontalDragGestures { change, amount ->
                change.consume()
                offset += amount
                carry += amount
                while (carry <= -stepPx) { carry += stepPx; onPlus(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                while (carry >= stepPx) { carry -= stepPx; onMinus(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.width / 2
            // Ticks sit at mid + offset + n·step; every fifth is taller.
            val first = kotlin.math.floor((-mid - offset) / stepPx).toInt()
            val last = kotlin.math.ceil((size.width - mid - offset) / stepPx).toInt()
            for (n in first..last) {
                val x = mid + offset + n * stepPx
                val major = Math.floorMod(n, 5) == 0
                val fade = (1f - abs(x - mid) / mid).coerceIn(0f, 1f)
                val h = if (major) size.height * .48f else size.height * .26f
                drawLine(tick.copy(alpha = .2f + .65f * fade), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
            }
            drawLine(accent, Offset(mid, size.height * .12f), Offset(mid, size.height * .88f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Editable list of short values: remove with ×, add with the field below. */
@Composable
fun ListEditor(title: String, values: List<String>, numeric: Boolean = false, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { i, v ->
                InputChip(selected = false, onClick = { onChange(values.toMutableList().also { it.removeAt(i) }) }, label = { Text(v) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remove $v", Modifier.size(16.dp)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, placeholder = { Text("Add") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
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
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null && subtitle.length <= 40) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** A full-screen page opened on top of another (editors, suites): same top bar and background everywhere. */
@Composable
fun FullScreenPage(title: String, onClose: () -> Unit, subtitle: String? = null, accent: Color? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val body: @Composable () -> Unit = {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    ChronoraTopBar(title, onClose, subtitle, actions)
                    content()
                }
            }
        }
        if (accent != null) SectionTheme(accent, body) else body()
    }
}

/** Big pill toggle group used instead of tiny filter chips for primary choices. */
@Composable
fun PillTabs(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, tween(250), label = "pill")
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(i) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}
