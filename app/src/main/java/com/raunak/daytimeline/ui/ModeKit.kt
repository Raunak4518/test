package com.raunak.daytimeline.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ turn-off protection

/** How hard it is to switch a protection off: a wait, then a sentence typed out. Both are editable. */
object OffGuardPrefs {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("chronora_off_guard", Context.MODE_PRIVATE)
    const val DEFAULT_PHRASE = "I choose my goals over this"
    fun waitSeconds(c: Context) = prefs(c).getInt("wait", 30)
    fun phrase(c: Context) = prefs(c).getString("phrase", DEFAULT_PHRASE)?.ifBlank { DEFAULT_PHRASE } ?: DEFAULT_PHRASE
    fun set(c: Context, wait: Int, phrase: String) = prefs(c).edit().putInt("wait", wait.coerceIn(5, 600)).putString("phrase", phrase.trim().ifBlank { DEFAULT_PHRASE }).apply()
}

private val keepLines = listOf(
    "You turned this on for a reason.",
    "Urges fade in a few minutes. This one will too.",
    "Future you will thank you for keeping it on.",
    "Take a breath. Do you really need this right now?",
    "Every time you hold the line, it gets easier."
)

class OffGuardState {
    var pending by mutableStateOf<Pair<String, () -> Unit>?>(null)
    /** Asks before running [action], which loosens a protection named [what]. */
    fun ask(what: String, action: () -> Unit) { pending = what to action }
}

/**
 * The friction before loosening anything: a countdown that restarts if you leave, then typing a sentence.
 * "Keep it on" is the big button; turning off is the small one.
 */
@Composable
fun rememberOffGuard(): OffGuardState {
    val s = remember { OffGuardState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    s.pending?.let { (what, action) ->
        val wait = remember { OffGuardPrefs.waitSeconds(context) }
        val phrase = remember { OffGuardPrefs.phrase(context) }
        val line = remember { keepLines.random() }
        var left by remember { mutableIntStateOf(wait) }
        var typed by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); while (left > 0) { delay(1000); left-- } }
        val ready = left == 0 && typed.trim().equals(phrase, ignoreCase = true)
        AlertDialog(
            onDismissRequest = { s.pending = null },
            icon = { Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text("Turn off $what?", textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(line, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    if (left > 0) {
                        val p by animateFloatAsState(left / wait.toFloat(), tween(900, easing = LinearEasing), label = "wait")
                        val ring = MaterialTheme.colorScheme.primary; val track = MaterialTheme.colorScheme.surfaceVariant
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val w = 8.dp.toPx(); val sz = Size(size.width - w, size.height - w)
                                drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
                                drawArc(ring, -90f, 360f * p, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
                            }
                            Text("$left", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Type this to continue", style = MaterialTheme.typography.labelLarge, color = Chronora.muted)
                        Text("“$phrase”", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { s.pending = null; Feedback.show("Good call. Kept on 💪") }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Keep it on") }
            },
            dismissButton = {
                TextButton(enabled = ready, onClick = { s.pending = null; action() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (left > 0) "Turn off in ${left}s" else "Turn off", color = if (ready) Chronora.colors.bad else Chronora.muted)
                }
            }
        )
    }
    return s
}

// ------------------------------------------------------------------ a mode you switch on and off

/**
 * The headline card for a mode (filter, bedtime, session): grey and quiet when off; when on, a vivid gradient,
 * a glowing power button that pulses, an ON badge and a little bounce with a haptic tick.
 */
@Composable
fun ModeHero(on: Boolean, title: String, status: String, icon: ImageVector, modifier: Modifier = Modifier, onLabel: String = "ON", onToggle: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val offBg = MaterialTheme.colorScheme.surface
    val top by animateColorAsState(if (on) lerp(accent, Color.Black, .25f) else offBg, tween(500), label = "top")
    val bottom by animateColorAsState(if (on) lerp(accent, Color.White, .12f) else offBg, tween(500), label = "bottom")
    val content = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    val muted = if (on) Color.White.copy(alpha = .78f) else Chronora.muted
    var bounce by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (bounce) 1.12f else 1f, spring(dampingRatio = .35f, stiffness = 500f), label = "bounce", finishedListener = { bounce = false })
    var lastOn by remember { mutableStateOf(on) }
    LaunchedEffect(on) { if (on && !lastOn) { bounce = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }; lastOn = on }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val halo by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "halo")

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(top, bottom))).padding(vertical = 22.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            if (on) Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color.White.copy(alpha = .28f * (1 - halo)), radius = r * (.55f + .45f * halo))
                drawCircle(Color.White.copy(alpha = .14f * (1 - halo)), radius = r * (.55f + .45f * ((halo + .5f) % 1f)))
            }
            Box(Modifier.size(86.dp).scale(scale).clip(CircleShape).background(if (on) Color.White else accent).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (on) icon else Icons.Default.PowerSettingsNew, if (on) "Turn off $title" else "Turn on $title", tint = if (on) accent else Color.White, modifier = Modifier.size(42.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(14.dp), tint = content)
                Text(" " + if (on) onLabel else "OFF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = content)
            }
        }
        Text(status, color = muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        CompositionLocalProvider(LocalContentColor provides content) { extra() }
    }
}

/** A small removable text chip (a website, a time): the cross removes it, the label does nothing. */
@Composable
fun TextChip(text: String, onRemove: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, "Remove $text", Modifier.size(16.dp)) }
    }
}
