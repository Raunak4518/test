package com.raunak.daytimeline.wellbeing

import com.raunak.daytimeline.ui.*

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.pro.FocusGuardService
import com.raunak.daytimeline.pro.FocusGuardStore
import com.raunak.daytimeline.pro.UsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

internal data class Perms(val usage: Boolean, val accessibility: Boolean, val notifications: Boolean, val dnd: Boolean, val grayscale: Boolean) {
    val all get() = usage && accessibility && notifications && dnd
    companion object {
        fun read(c: android.content.Context) = Perms(UsageAccess.granted(c), FocusGuardService.isEnabled(c), WellbeingNotificationListener.enabled(c), WellbeingModes.dndAccess(c), WellbeingModes.grayscaleAccess(c))
    }
}

@Composable
internal fun PermissionsCard(p: Perms) {
    val context = LocalContext.current
    SetupBanner(listOf(
        SetupStep("Usage access, for screen time", p.usage) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) },
        SetupStep("Accessibility, for blocking", p.accessibility) { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
    ))
}

/** Single-series bar chart: rounded data ends on the baseline, 2 dp gaps, tap a bar to read its value. */
@Composable
internal fun BarChart(values: List<Int>, labels: List<String>, valueLabel: (Int, Int) -> String, height: androidx.compose.ui.unit.Dp, goal: Int? = null, highlight: Int = -1) {
    val bar = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(values) { mutableIntStateOf(-1) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(goal ?: 0).coerceAtLeast(1)
    Column {
        if (selected in values.indices) Text(valueLabel(selected, values[selected]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(values) {
                detectTapGestures { o -> selected = (o.x / (size.width / values.size.toFloat())).toInt().coerceIn(0, values.lastIndex) }
            }
        ) {
            val slot = size.width / values.size
            val gap = 2.dp.toPx()
            val w = (slot - gap).coerceAtLeast(1f)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            goal?.let { g ->
                val y = size.height - size.height * g / max
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (size.height * v / max).coerceAtLeast(2.dp.toPx())
                val color = if (highlight < 0 || i == highlight || i == selected) bar else muted
                roundedTopBar(color, Offset(i * slot + gap / 2, size.height - h), Size(w, h), 4.dp.toPx().coerceAtMost(w / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
    }
}

private fun DrawScope.roundedTopBar(color: androidx.compose.ui.graphics.Color, topLeft: Offset, size: Size, r: Float) {
    val path = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r), bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero))
    }
    drawPath(path, color)
}


@Composable
private fun MinutesStepper(label: String, value: Int, step: Int = 15, min: Int = 0, max: Int = 24 * 60, zeroLabel: String = "Off", onChange: (Int) -> Unit) =
    Stepper(label, if (value == 0) zeroLabel else hm(value), { onChange((value - step).coerceAtLeast(min)) }, { onChange((value + step).coerceAtMost(max)) })

@Composable
internal fun AppDetailDialog(
    pkg: String, label: String, week: Map<LocalDate, DayUsage>,
    c: WellbeingConfig, g: com.raunak.daytimeline.pro.FocusGuardConfig,
    onSave: (WellbeingConfig) -> Unit, onSaveGuard: (com.raunak.daytimeline.pro.FocusGuardConfig) -> Unit, onClose: () -> Unit
) {
    val today = LocalDate.now()
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val mins = days.map { d -> week[d]?.apps?.firstOrNull { it.pkg == pkg }?.minutes ?: 0 }
    val opens = days.map { d -> week[d]?.apps?.firstOrNull { it.pkg == pkg }?.opens ?: 0 }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(label) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text("7-day total ${hm(mins.sum())} · avg ${hm(mins.sum() / 7)} · ${opens.sum()} opens", style = MaterialTheme.typography.bodySmall)
                    BarChart(mins, days.map { it.dayOfWeek.name.take(1) }, { i, v -> "${days[i]} · ${hm(v)} · ${opens[i]} opens" }, 90.dp)
                }
                item {
                    MinutesStepper("Weekday timer", g.dailyLimits[pkg] ?: 0) { v -> onSaveGuard(g.copy(dailyLimits = if (v == 0) g.dailyLimits - pkg else g.dailyLimits + (pkg to v))) }
                    MinutesStepper("Weekend timer", c.weekendLimits[pkg] ?: 0, zeroLabel = "Same") { v -> onSave(c.copy(weekendLimits = if (v == 0) c.weekendLimits - pkg else c.weekendLimits + (pkg to v))) }
                    val cur = c.openLimits[pkg] ?: 0
                    Stepper("Max opens per day", if (cur == 0) "Off" else "$cur",
                        { val v = (cur - 5).coerceAtLeast(0); onSave(c.copy(openLimits = if (v == 0) c.openLimits - pkg else c.openLimits + (pkg to v))) },
                        { onSave(c.copy(openLimits = c.openLimits + (pkg to cur + 5))) })
                    val s = c.sessionLimits[pkg]
                    MinutesStepper("Session length", s?.maxMinutes ?: 0, step = 5) { v -> onSave(c.copy(sessionLimits = if (v == 0) c.sessionLimits - pkg else c.sessionLimits + (pkg to SessionLimit(v, s?.cooldownMinutes ?: 30)))) }
                    if (s != null) MinutesStepper("Break after a session", s.cooldownMinutes, step = 5, min = 5) { v -> onSave(c.copy(sessionLimits = c.sessionLimits + (pkg to s.copy(cooldownMinutes = v)))) }
                    SwitchRow("Mindful pause before opening", pkg in g.mindfulPackages) { on -> onSaveGuard(g.copy(mindfulPackages = if (on) g.mindfulPackages + pkg else g.mindfulPackages - pkg)) }
                    SwitchRow("Block during focus", pkg in g.blockedPackages) { on -> onSaveGuard(g.copy(blockedPackages = if (on) g.blockedPackages + pkg else g.blockedPackages - pkg)) }
                    SwitchRow("Quiet notifications (digest)", pkg in c.quietApps) { on -> onSave(c.copy(quietApps = if (on) c.quietApps + pkg else c.quietApps - pkg)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}
