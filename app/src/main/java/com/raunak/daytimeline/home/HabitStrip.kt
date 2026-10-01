package com.raunak.daytimeline.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.features.HabitEngine
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.features.streak
import com.raunak.daytimeline.ui.Chronora
import com.raunak.daytimeline.ui.Feedback
import java.time.LocalDate

/** Today's habits as bubbles: tap to tick (with Undo), long list scrolls sideways. Hidden when there are none. */
@Composable
fun HabitStrip(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val store = remember { OfflineProductivityStore(context.applicationContext) }
    val habits by store.habits.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val haptics = LocalHapticFeedback.current
    val list = habits.filter { !it.archived && (HabitEngine.dueToday(it, today) || HabitEngine.isDone(it, today)) }
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Habits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${list.count { HabitEngine.isDone(it, today) }}/${list.size}", style = MaterialTheme.typography.labelLarge, color = Chronora.muted,
                modifier = Modifier.clickable(onClick = onOpen))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { h ->
                val done = HabitEngine.isDone(h, today)
                val c = Color(h.color)
                val fill by animateColorAsState(if (done) c else Color.Transparent, label = "habit")
                val pop by animateFloatAsState(if (done) 1f else .92f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessMedium), label = "pop")
                Column(Modifier.width(68.dp).clip(MaterialTheme.shapes.small).clickable {
                    store.toggleHabit(h.id, today)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    Feedback.show(if (done) "${h.name} unticked" else "${h.name} ✓", undo = { store.toggleHabit(h.id, today) })
                }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).scale(pop).clip(CircleShape).background(fill).border(3.dp, c, CircleShape), contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, "Done", tint = Color.White)
                        else Text(h.name.take(1).uppercase(), color = c, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(h.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    val st = h.streak(today)
                    if (st > 1) Text("🔥$st", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
                }
            }
        }
    }
}
