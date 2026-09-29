package com.raunak.daytimeline.alarm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

internal val RingBg = Color(0xFF0E1512)
internal val RingCard = Color(0xFF18221E)
internal val RingAccent = Color(0xFF8FD3B0)
internal val RingText = Color(0xFFF1F5F3)
internal val RingMuted = Color(0xFF9AAEA5)
internal val RingBad = Color(0xFFFF8A80)

/** Everything a mission needs from the ringing screen. */
class MissionHost(
    val runtime: AlarmMissionRuntime,
    val references: AlarmReferenceStore,
    val alarmId: Long,
    val phrases: List<String>,
    val onInteract: () -> Unit,
    val takePhoto: ((android.graphics.Bitmap?) -> Unit) -> Unit
)

@Composable
fun MissionView(mission: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    when (mission.type) {
        AlarmMissionType.MATH -> MathMission(mission, host, onDone)
        AlarmMissionType.MEMORY -> MemoryMission(mission, host, onDone)
        AlarmMissionType.TYPING -> TypingMission(mission, host, onDone)
        AlarmMissionType.SHAKE, AlarmMissionType.SQUAT, AlarmMissionType.WALK -> MotionMission(mission, host, onDone)
        AlarmMissionType.TAP -> TapMission(mission, host, onDone)
        AlarmMissionType.PHOTO -> PhotoMission(host, onDone)
        AlarmMissionType.BARCODE -> BarcodeMission(host, onDone)
        AlarmMissionType.MULTI -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Continue") }
    }
}

@Composable
private fun RoundLabel(text: String) = Text(text, color = RingMuted, style = MaterialTheme.typography.labelLarge)

@Composable
private fun Feedback(text: String) { if (text.isNotBlank()) Text(text, color = RingBad, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

@Composable
private fun MathMission(m: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    val total = m.target.coerceAtLeast(1)
    var round by remember { mutableIntStateOf(1) }
    var problem by remember { mutableStateOf(AlarmChallenges.math(m.difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    fun submit() {
        if (input.toIntOrNull() == problem.answer) {
            if (round >= total) onDone() else { round++; problem = AlarmChallenges.math(m.difficulty); input = ""; wrong = false }
        } else { wrong = true; input = ""; problem = AlarmChallenges.math(m.difficulty) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoundLabel("Problem $round of $total")
        Text(problem.text + " = ?", color = RingText, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(RingCard).border(2.dp, if (wrong) RingBad else RingAccent.copy(alpha = .4f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text(input.ifEmpty { " " }, color = RingText, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        }
        Feedback(if (wrong) "Not quite — here's a new one" else "")
        Keypad(onDigit = { d -> host.onInteract(); if (input.length < 7) input += d; wrong = false }, onBack = { host.onInteract(); input = input.dropLast(1) }, onOk = { host.onInteract(); submit() })
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBack: () -> Unit, onOk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val ok = k == "✓"
                    Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).background(if (ok) RingAccent else RingCard).clickable {
                        when (k) { "⌫" -> onBack(); "✓" -> onOk(); else -> onDigit(k) }
                    }, contentAlignment = Alignment.Center) {
                        Text(k, color = if (ok) RingBg else RingText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryMission(m: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    val total = m.target.coerceAtLeast(1)
    val (side, _) = AlarmChallenges.memorySize(m.difficulty)
    var round by remember { mutableIntStateOf(1) }
    var attempt by remember { mutableIntStateOf(0) }
    var pattern by remember { mutableStateOf(AlarmChallenges.memoryPattern(m.difficulty)) }
    var showing by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var miss by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(round, attempt) { showing = true; picked = emptySet(); miss = null; delay(1600L + pattern.size * 250L); showing = false }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoundLabel("Round $round of $total")
        Text(if (showing) "Remember the lit tiles" else "Tap the tiles that were lit", color = RingText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until side).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until side).forEach { c ->
                        val i = r * side + c
                        val lit = (showing && i in pattern) || i in picked
                        Box(Modifier.size((260 / side).dp).clip(RoundedCornerShape(12.dp))
                            .background(when { miss == i -> RingBad; lit -> RingAccent; else -> RingCard })
                            .clickable(enabled = !showing && miss == null && i !in picked) {
                                host.onInteract()
                                if (i in pattern) {
                                    picked = picked + i
                                    if (picked.size == pattern.size) {
                                        if (round >= total) onDone() else { round++; pattern = AlarmChallenges.memoryPattern(m.difficulty) }
                                    }
                                } else { miss = i; pattern = AlarmChallenges.memoryPattern(m.difficulty); attempt++ }
                            })
                    }
                }
            }
        }
        Feedback(if (miss != null) "Wrong tile — watch again" else "")
    }
}

@Composable
private fun TypingMission(m: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    val total = m.target.coerceAtLeast(1)
    var round by remember { mutableIntStateOf(1) }
    var phrase by remember { mutableStateOf(AlarmChallenges.phrase(m.difficulty, host.phrases)) }
    var input by remember { mutableStateOf("") }
    val ok = AlarmChallenges.typedPrefix(input, phrase)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoundLabel("Phrase $round of $total")
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = RingAccent)) { append(phrase.take(ok)) }
            withStyle(SpanStyle(color = RingText)) { append(phrase.drop(ok)) }
        }, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp)
        OutlinedTextField(input, { v ->
            host.onInteract(); input = v
            if (AlarmChallenges.typedOk(v, phrase)) { if (round >= total) onDone() else { round++; phrase = AlarmChallenges.phrase(m.difficulty, host.phrases); input = "" } }
        }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Type it here") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RingText, unfocusedTextColor = RingText, focusedBorderColor = RingAccent, cursorColor = RingAccent),
            isError = input.isNotEmpty() && ok < input.length)
    }
}

@Composable
private fun MotionMission(m: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    val target = m.target.coerceAtLeast(1)
    var progress by remember { mutableIntStateOf(0) }
    DisposableEffect(m) {
        val tick: (Int) -> Unit = { progress = it; host.onInteract() }
        when (m.type) {
            AlarmMissionType.SHAKE -> host.runtime.startShake(target, tick, onDone)
            AlarmMissionType.WALK -> host.runtime.startSteps(target, tick, onDone)
            else -> host.runtime.startSquats(target, tick, onDone)
        }
        onDispose { host.runtime.stopShake(); host.runtime.stopSteps(); host.runtime.stopSquats() }
    }
    val fraction by animateFloatAsState((progress.toFloat() / target).coerceIn(0f, 1f), label = "motion")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
        Text(when (m.type) { AlarmMissionType.SHAKE -> "Shake it!"; AlarmMissionType.WALK -> "Get up and walk"; else -> "Squat holding your phone" }, color = RingText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 18.dp.toPx(); val arc = Size(size.width - s, size.height - s); val tl = Offset(s / 2, s / 2)
                drawArc(RingCard, 0f, 360f, false, tl, arc, style = Stroke(s))
                drawArc(RingAccent, -90f, 360f * fraction, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.type.icon, fontSize = 36.sp)
                Text("$progress", color = RingText, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("of $target", color = RingMuted)
            }
        }
    }
}

@Composable
private fun TapMission(m: AlarmMission, host: MissionHost, onDone: () -> Unit) {
    val target = m.target.coerceAtLeast(1)
    var count by remember { mutableIntStateOf(0) }
    var area by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset(.5f, .5f)) }
    val dotPx = with(LocalDensity.current) { (72 - m.difficulty * 6).dp.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tap the dot · $count / $target", color = RingText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { count.toFloat() / target }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = RingAccent, trackColor = RingCard)
        Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)).background(RingCard).onSizeChanged { area = it }) {
            if (area.width > 0) Box(Modifier.offset { IntOffset(((area.width - dotPx) * pos.x).toInt(), ((area.height - dotPx) * pos.y).toInt()) }
                .size(with(LocalDensity.current) { dotPx.toDp() }).clip(CircleShape).background(RingAccent)
                .clickable {
                    host.onInteract(); count++
                    if (count >= target) onDone() else pos = Offset(Random.nextFloat(), Random.nextFloat())
                })
        }
    }
}

@Composable
private fun PhotoMission(host: MissionHost, onDone: () -> Unit) {
    val registered = host.references.hasPhoto(host.alarmId)
    var feedback by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text("📷", fontSize = 64.sp)
        Text(if (registered) "Go to the spot you registered and take the same photo" else "No photo registered for this alarm", color = RingText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Button(enabled = registered, onClick = {
            host.onInteract()
            host.takePhoto { bmp -> if (bmp != null && host.references.verifyPhoto(host.alarmId, bmp)) onDone() else feedback = "Doesn't match — try the same angle and light" }
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Take photo") }
        if (!registered) Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip this mission") }
        Feedback(feedback)
    }
}

@Composable
private fun BarcodeMission(host: MissionHost, onDone: () -> Unit) {
    val expected = host.references.barcode(host.alarmId)
    var feedback by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text("🔳", fontSize = 64.sp)
        Text(if (expected != null) "Scan the code you registered" else "No code registered for this alarm", color = RingText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Button(enabled = expected != null, onClick = {
            host.onInteract()
            host.takePhoto { bmp -> if (bmp == null) feedback = "No picture taken" else AlarmCameraVerifier.scan(bmp) { v -> if (v != null && v == expected) onDone() else feedback = if (v == null) "No code found — get closer" else "That's a different code" } }
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Scan") }
        if (expected == null) Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip this mission") }
        Feedback(feedback)
    }
}
