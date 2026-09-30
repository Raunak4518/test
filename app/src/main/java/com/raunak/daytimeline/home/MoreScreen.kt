package com.raunak.daytimeline.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.Chronora

/** A destination on the More page: [route] is handed back to the shell, which opens it. */
data class MoreItem(val label: String, val icon: ImageVector, val color: Long, val route: String)

val MoreSections: List<Pair<String, List<MoreItem>>> = listOf(
    "Digital wellbeing" to listOf(
        MoreItem("Focus mode", Icons.Default.SelfImprovement, 0xFF55786A, "focusmode"),
        MoreItem("Screen time", Icons.Default.PhoneAndroid, 0xFF3D85C6, "wellbeing:0"),
        MoreItem("App blocker", Icons.Default.Block, 0xFFC62828, "wellbeing:2"),
        MoreItem("Limits", Icons.Default.HourglassBottom, 0xFFE0A33B, "wellbeing:3"),
        MoreItem("Web filter", Icons.Default.Shield, 0xFF8E6BBF, "wellbeing:4")
    ),
    "Life" to listOf(
        MoreItem("Habits", Icons.Default.Loop, 0xFF43A047, "prod:0"),
        MoreItem("Goals", Icons.Default.Flag, 0xFFE07A5F, "prod:1"),
        MoreItem("Routines", Icons.Default.Checklist, 0xFF8E6BBF, "prod:2"),
        MoreItem("Notes", Icons.Default.StickyNote2, 0xFFE0A33B, "prod:3"),
        MoreItem("Journal", Icons.Default.AutoStories, 0xFF3D85C6, "prod:4"),
        MoreItem("Time log", Icons.Default.Timer, 0xFF4DB6AC, "prod:5")
    ),
    "Tools" to listOf(
        MoreItem("Alarms", Icons.Default.Alarm, 0xFFE0A33B, "alarms"),
        MoreItem("Calendar", Icons.Default.CalendarMonth, 0xFF3D85C6, "calendar"),
        MoreItem("Search", Icons.Default.Search, 0xFF55786A, "tool:SEARCH"),
        MoreItem("Quick add", Icons.Default.Mic, 0xFFE07A5F, "tool:QUICK_ADD"),
        MoreItem("Sounds", Icons.Default.GraphicEq, 0xFF8E6BBF, "tool:SOUNDS"),
        MoreItem("Garden", Icons.Default.Park, 0xFF43A047, "tool:GARDEN"),
        MoreItem("Energy plan", Icons.Default.Bolt, 0xFFE0A33B, "tool:ENERGY"),
        MoreItem("Week review", Icons.Default.DateRange, 0xFF3D85C6, "tool:WEEK"),
        MoreItem("Places", Icons.Default.Place, 0xFFC62828, "tool:PLACES"),
        MoreItem("Private journal", Icons.Default.Lock, 0xFF5B6BB0, "tool:JOURNAL")
    ),
    "App" to listOf(
        MoreItem("Insights", Icons.Default.Insights, 0xFF55786A, "power"),
        MoreItem("Command center", Icons.Default.Dashboard, 0xFF6B7F76, "command"),
        MoreItem("Settings & backup", Icons.Default.Settings, 0xFF6B7F76, "settings"),
        MoreItem("Update app", Icons.Default.SystemUpdate, 0xFF3D85C6, "update")
    )
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { com.raunak.daytimeline.wellbeing.TodayUsageStrip { onOpen("wellbeing:0") } }
        MoreSections.forEach { (title, items) ->
            item(key = title) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Chronora.muted, modifier = Modifier.padding(top = 8.dp)) }
            items.chunked(4).forEachIndexed { row, chunk ->
                item(key = "$title$row") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { Tile(it, Modifier.weight(1f)) { onOpen(it.route) } }
                        repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { com.raunak.daytimeline.MadeByRaunak(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) }
    }
}

@Composable
private fun Tile(item: MoreItem, modifier: Modifier, onClick: () -> Unit) {
    val c = Color(item.color)
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = c, modifier = Modifier.size(26.dp))
        }
        Text(item.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
    }
}
