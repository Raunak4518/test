package com.raunak.daytimeline.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.IconBadge

/** A destination on the You page: [route] is handed back to the shell, which opens it. */
data class MoreItem(val label: String, val icon: ImageVector, val color: Long, val route: String)

/**
 * Each feature lives in exactly one place. Alarms and Search are in the top bar, the calendar is in Plan,
 * the timer, Focus mode, sounds and garden are in Focus, and settings sit behind the gear.
 */
val MoreSections: List<Pair<String, List<MoreItem>>> = listOf(
    "Digital wellbeing" to listOf(
        MoreItem("Screen time", Icons.Default.PhoneAndroid, 0xFF2F8FE0, "wellbeing:0"),
        MoreItem("App blocker", Icons.Default.Block, 0xFFE0434C, "wellbeing:2"),
        MoreItem("Limits", Icons.Default.HourglassBottom, 0xFFDB8F12, "wellbeing:3"),
        MoreItem("Bedtime", Icons.Default.Bedtime, 0xFF4F5BD5, "wellbeing:4"),
        MoreItem("Web filter", Icons.Default.Shield, 0xFF7C5CE6, "wellbeing:5")
    ),
    "Habits & goals" to listOf(
        MoreItem("Trackers", Icons.Default.DonutLarge, 0xFF0E9F9A, "trackers"),
        MoreItem("Habits", Icons.Default.Loop, 0xFF22A06B, "prod:0"),
        MoreItem("Goals", Icons.Default.Flag, 0xFFEF6A45, "prod:1"),
        MoreItem("Routines", Icons.Default.Checklist, 0xFF7C5CE6, "prod:2")
    ),
    "Write" to listOf(
        MoreItem("Journal", Icons.Default.AutoStories, 0xFF2F8FE0, "prod:4"),
        MoreItem("Notes", Icons.Default.StickyNote2, 0xFFDB8F12, "prod:3"),
        MoreItem("Private journal", Icons.Default.Lock, 0xFF4F5BD5, "tool:JOURNAL")
    ),
    "Track & review" to listOf(
        MoreItem("Time log", Icons.Default.Timer, 0xFF0E9F9A, "prod:5"),
        MoreItem("Week review", Icons.Default.DateRange, 0xFF4F5BD5, "tool:WEEK"),
        MoreItem("Insights", Icons.Default.Insights, 0xFFE5486B, "power")
    ),
    "Smart tools" to listOf(
        MoreItem("Voice add", Icons.Default.Mic, 0xFFEF6A45, "tool:QUICK_ADD"),
        MoreItem("Energy plan", Icons.Default.Bolt, 0xFFDB8F12, "tool:ENERGY"),
        MoreItem("Places", Icons.Default.Place, 0xFFE0434C, "tool:PLACES")
    )
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val locked = com.raunak.daytimeline.campus.rememberLockedUntil() != null
    val lockedRoutes = setOf("wellbeing:2", "wellbeing:3", "wellbeing:4", "wellbeing:5")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { com.raunak.daytimeline.wellbeing.TodayUsageStrip { onOpen("wellbeing:0") } }
        MoreSections.map { (t, list) -> t to if (locked) list.filterNot { it.route in lockedRoutes } else list }.forEach { (title, items) ->
            item(key = title) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    items.chunked(if (items.size == 4) 2 else 3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { Tile(it, Modifier.weight(1f)) { onOpen(it.route) } }
                        }
                    }
                }
            }
        }
        item { com.raunak.daytimeline.MadeByRaunak(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) }
    }
}

/** A coloured card tile: soft gradient in the feature's colour, icon on top, label below. */
@Composable
private fun Tile(item: MoreItem, modifier: Modifier, onClick: () -> Unit) {
    val c = Color(item.color)
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c.copy(alpha = .20f), c.copy(alpha = .07f))))
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(item.icon, null, tint = c, modifier = Modifier.size(28.dp))
        Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Settings behind the gear: appearance and backup, updates, and the advanced legacy tools. */
@Composable
fun SettingsList(onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SettingsRow(Icons.Default.Palette, 0xFF7C5CE6, "Theme, backup & restore") { onOpen("settings") } }
        item { SettingsRow(Icons.Default.Alarm, 0xFFDB8F12, "Alarms") { onOpen("alarms") } }
        item { SettingsRow(Icons.Default.SystemUpdate, 0xFF2F8FE0, "Check for updates") { onOpen("update") } }
        item { SettingsRow(Icons.Default.Dashboard, 0xFF636A7E, "Advanced tools") { onOpen("command") } }
        item { com.raunak.daytimeline.MadeByRaunak(Modifier.fillMaxWidth().padding(top = 16.dp)) }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, color: Long, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, Color(color), 40.dp)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Chronora.muted)
    }
}
