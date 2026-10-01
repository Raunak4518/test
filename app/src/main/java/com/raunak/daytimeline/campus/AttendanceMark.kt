package com.raunak.daytimeline.campus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.Feedback
import java.time.format.DateTimeFormatter

@Composable
private fun markColor(m: Mark): Color = when (m) {
    Mark.PRESENT, Mark.LATE -> Chronora.colors.good
    Mark.ABSENT -> Chronora.colors.bad
    Mark.NO_CLASS -> Chronora.muted
}

private fun markIcon(m: Mark): ImageVector = when (m) {
    Mark.PRESENT -> Icons.Default.CheckCircle
    Mark.LATE -> Icons.Default.Schedule
    Mark.ABSENT -> Icons.Default.Cancel
    Mark.NO_CLASS -> Icons.Default.EventBusy
}

/**
 * One class with its attendance. Before the class: nothing to tap. After (or during) it: two clear buttons.
 * Once marked it locks into a coloured status; changing it needs a deliberate confirmation.
 */
@Composable
internal fun ClassRow(o: ClassOccurrence, s: Subject, mark: Mark?, past: Boolean, showDate: Boolean = false, onMark: (Mark?) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var changing by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    fun commit(m: Mark?) {
        val before = mark
        onMark(m)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        Feedback.show(if (m == null) "${s.name}: mark cleared" else "${s.name}: ${m.label.lowercase()}", undo = { onMark(before) })
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(Color(s.colorHex)))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(s.name + if (o.type != ClassType.LECTURE) " · ${o.type.label}" else "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (showDate) o.date.format(DateTimeFormatter.ofPattern("EEE d MMM")) else null, "${clock(o.start)}–${clock(o.end)}", o.room.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Chronora.muted)
        }
        AnimatedContent(mark, transitionSpec = { (scaleIn(spring(dampingRatio = .45f, stiffness = Spring.StiffnessMediumLow), initialScale = .6f) + fadeIn()) togetherWith fadeOut() }, label = "mark") { m ->
            when {
                m != null -> {
                    val c = markColor(m)
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = .16f)).clickable { changing = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(markIcon(m), null, Modifier.size(18.dp), tint = c)
                        Text(" " + m.label, color = c, style = MaterialTheme.typography.labelLarge)
                    }
                }
                past -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarkButton("Present", Icons.Default.Check, Chronora.colors.good) { commit(Mark.PRESENT) }
                    MarkButton("Absent", Icons.Default.Close, Chronora.colors.bad) { commit(Mark.ABSENT) }
                    Box {
                        IconButton(onClick = { more = true }, Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "More", tint = Chronora.muted) }
                        DropdownMenu(more, { more = false }) {
                            DropdownMenuItem(text = { Text("Late") }, leadingIcon = { Icon(Icons.Default.Schedule, null) }, onClick = { more = false; commit(Mark.LATE) })
                            DropdownMenuItem(text = { Text("No class") }, leadingIcon = { Icon(Icons.Default.EventBusy, null) }, onClick = { more = false; commit(Mark.NO_CLASS) })
                        }
                    }
                }
                else -> Text("upcoming", style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            }
        }
    }
    if (changing && mark != null) AlertDialog(
        onDismissRequest = { changing = false },
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text("Change ${s.name}?") },
        text = { Text("Marked ${mark.label.lowercase()} · ${o.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))} ${clock(o.start)}") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Mark.values().filter { it != mark }.forEach { m -> TextButton(onClick = { changing = false; commit(m) }) { Text("Mark ${m.label.lowercase()}", color = markColor(m)) } }
                TextButton(onClick = { changing = false; commit(null) }) { Text("Clear") }
            }
        },
        dismissButton = { TextButton(onClick = { changing = false }) { Text("Keep") } }
    )
}

/** Round one-tap button: ✓ present, ✗ absent. */
@Composable
private fun MarkButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color.copy(alpha = .15f)).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = color)
    }
}
