package com.raunak.daytimeline.alarm

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The alarm itself: ringing → missions → good-morning briefing.
 * The sound drops while you work on a mission and comes back if you stop; leaving mid-mission brings
 * the alarm back to the front.
 */
class AlarmRingingActivity : ComponentActivity() {
    companion object { const val EXTRA_TEST_MODE = "alarm_test_mode" }

    private enum class Phase { RINGING, MISSION, BRIEFING }

    private lateinit var runtime: AlarmMissionRuntime
    private lateinit var references: AlarmReferenceStore
    private var flow: AlarmAlarmFlow? = null
    private var activeConfig: AlarmPersistentConfig? = null
    private var phase by mutableStateOf(Phase.RINGING)
    private var missionIndex by mutableIntStateOf(0)
    private var lastInteraction = 0L
    private var missionStartedAt = 0L
    private var finished = false
    private var launchingCamera = false
    private var cameraCallback: ((android.graphics.Bitmap?) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val testMode get() = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        launchingCamera = false
        cameraCallback?.invoke(bitmap)
        cameraCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        runtime = AlarmMissionRuntime(this)
        references = AlarmReferenceStore(this)
        val id = intent.getLongExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, -1L)
        activeConfig = AlarmPersistentStore(this).find(id)
        val config = activeConfig ?: run { finish(); return }
        flow = AlarmAlarmFlow(config.missionChain, AlarmMissionPolicy(config.maxSnoozes, config.snoozeMinutes, config.longPressMs, config.timeoutMinutes, config.backupDelayMinutes).validated())
        if (!testMode) AlarmHistoryStore(this).onRing(config.id)
        runtime.startAlarmSound(config)
        // Back never dismisses an alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = Unit })
        setContent { com.raunak.daytimeline.ui.ChronoraThemeBase(dark = true) { Surface(Modifier.fillMaxSize(), color = RingBg) { Screen(config) } } }
    }

    /** Leaving the alarm while it's unfinished brings it back (camera missions excepted). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!finished && !launchingCamera && phase != Phase.BRIEFING && AlarmPrefs(this).keepOnTop && !testMode) {
            handler.postDelayed({ if (!finished) startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }, 1500)
        }
    }

    private fun interact() {
        lastInteraction = System.currentTimeMillis()
        runtime.setQuiet(true)
    }

    @Composable
    private fun Screen(config: AlarmPersistentConfig) {
        // Back to full volume when a mission sits idle.
        LaunchedEffect(phase) {
            if (phase != Phase.MISSION) return@LaunchedEffect
            val idle = AlarmPrefs(this@AlarmRingingActivity).idleSeconds * 1000L
            interact()
            while (true) {
                delay(1000)
                if (System.currentTimeMillis() - lastInteraction > idle) { runtime.setQuiet(false); runtime.resumeVibration(config); lastInteraction = Long.MAX_VALUE / 2 }
            }
        }
        when (phase) {
            Phase.RINGING -> RingingView(config)
            Phase.MISSION -> MissionScreen(config)
            Phase.BRIEFING -> BriefingView(config)
        }
    }

    @Composable
    private fun RingingView(config: AlarmPersistentConfig) {
        val f = flow ?: return
        var now by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
        val policy = config.snoozePolicy()
        val snooze = policy.nextDuration(f.snoozesUsed() + AlarmHistoryStore(this).snoozes(config.id), f.totalSnoozeMinutes(), LocalDateTime.now(), LocalDateTime.now())
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = RingMuted, style = MaterialTheme.typography.titleMedium)
            Text("%02d:%02d".format(now.hour, now.minute), color = RingText, fontSize = 88.sp, fontWeight = FontWeight.Light)
            Text(config.label, color = RingText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (testMode) Text("Preview", color = RingAccent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(RingAccent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(RingAccent.copy(alpha = .35f)), contentAlignment = Alignment.Center) { Text("⏰", fontSize = 40.sp) }
            }
            Spacer(Modifier.weight(1f))
            if (config.missionChain.isNotEmpty()) Text(config.missionChain.joinToString("  ") { it.type.icon } + "  to turn off", color = RingMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (config.missionChain.isEmpty()) complete(config) else { missionStartedAt = System.currentTimeMillis(); phase = Phase.MISSION }
            }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = RingAccent, contentColor = RingBg)) {
                Text(if (config.missionChain.isEmpty()) "Dismiss" else "Start mission", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (snooze > 0 && f.canSnooze()) OutlinedButton(onClick = { doSnooze(config, snooze) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Text("Snooze $snooze min", color = RingText, fontSize = 17.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    @Composable
    private fun MissionScreen(config: AlarmPersistentConfig) {
        val f = flow ?: return
        val mission = config.missionChain.getOrNull(missionIndex) ?: return
        val host = remember(missionIndex) {
            MissionHost(runtime, references, config.id, AlarmPrefs(this).phrases, ::interact) { cb -> launchingCamera = true; cameraCallback = cb; camera.launch(null) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                config.missionChain.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i < missionIndex) RingAccent else if (i == missionIndex) RingAccent.copy(alpha = .6f) else RingCard))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${mission.type.icon}  ${mission.type.title}", color = RingText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (config.missionChain.size > 1) Text("${missionIndex + 1}/${config.missionChain.size}", color = RingMuted)
            }
            key(missionIndex) {
                MissionView(mission, host) {
                    if (f.recordProgress(mission.target.coerceAtLeast(1))) complete(config) else { missionIndex = f.missionIndex(); interact() }
                }
            }
        }
    }

    @Composable
    private fun BriefingView(config: AlarmPersistentConfig) {
        val now = LocalTime.now()
        val greeting = when (now.hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        val briefing = remember {
            // Storage may be locked before the first unlock after boot; the briefing is optional.
            runCatching {
                val data = com.raunak.daytimeline.campus.CampusStore.get(applicationContext).data.value
                com.raunak.daytimeline.campus.WakeBriefing.quote(data.settings, LocalDate.now()) to com.raunak.daytimeline.campus.WakeBriefing.lines(data, LocalDate.now())
            }.getOrNull()
        }
        val stats = remember { AlarmHistoryStore(this).all().let { AlarmHistoryStore.stats(it.takeLast(14)) } }
        val missionSecs = ((System.currentTimeMillis() - missionStartedAt) / 1000).toInt().takeIf { missionStartedAt > 0 }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("☀️", fontSize = 48.sp)
            Text(greeting, color = RingText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) + " · up at %02d:%02d".format(now.hour, now.minute), color = RingMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                missionSecs?.let { BriefTile("Missions", if (it >= 60) "${it / 60}m ${it % 60}s" else "${it}s", Modifier.weight(1f)) }
                if (stats.records > 0) BriefTile("On-time streak", "${stats.onTimeStreak}", Modifier.weight(1f))
                if (stats.records > 0) BriefTile("On time (2 wks)", "${stats.onTimeRate}%", Modifier.weight(1f))
            }
            briefing?.first?.let { Text("“$it”", color = RingAccent, style = MaterialTheme.typography.titleMedium) }
            briefing?.second?.takeIf { it.isNotEmpty() }?.let { lines ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RingCard).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Today", color = RingMuted, style = MaterialTheme.typography.labelLarge)
                    lines.forEach { Text(it, color = RingText) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::finishAll, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = RingAccent, contentColor = RingBg)) {
                Text("Start my day", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun BriefTile(label: String, value: String, modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(16.dp)).background(RingCard).padding(12.dp)) {
            Text(value, color = RingText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = RingMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Start)
        }
    }

    private fun doSnooze(config: AlarmPersistentConfig, minutes: Int) {
        val f = flow ?: return
        if (!f.snooze(minutes)) return
        if (!testMode) { AlarmHistoryStore(this).onSnooze(config.id); AlarmManagerBridge(this).scheduleSnooze(config, minutes) }
        dismissAlarm(cancelSnooze = false, rescheduleRepeat = false)
        finishAll()
    }

    /** Missions done: stop ringing, record, then show the briefing (or close). */
    private fun complete(config: AlarmPersistentConfig) {
        val secs = if (missionStartedAt > 0) ((System.currentTimeMillis() - missionStartedAt) / 1000).toInt() else 0
        if (!testMode) AlarmHistoryStore(this).onDismiss(config, secs)
        dismissAlarm()
        if (config.briefing) phase = Phase.BRIEFING else finishAll()
    }

    private fun finishAll() { finished = true; finishAndRemoveTask() }

    private fun dismissAlarm(cancelSnooze: Boolean = true, rescheduleRepeat: Boolean = true) {
        val config = activeConfig ?: return
        finished = true
        runtime.release()
        handler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java)?.cancel((config.id xor (config.id ushr 32)).toInt())
        val bridge = AlarmManagerBridge(this)
        bridge.cancelScheduledCycle(config.id)
        if (!testMode) {
            val runtimeStore = AlarmRuntimeStore(this)
            runtimeStore.markDismissed(config.id)
            if (rescheduleRepeat && config.enabled && config.isRepeating() && !config.deleteAfterRinging) bridge.schedule(config)
            if (cancelSnooze && config.wakeCheckMinutes > 0) bridge.scheduleWakeChecksAfterDismissal(config)
            if (cancelSnooze && config.id == com.raunak.daytimeline.campus.CampusScheduler.WAKE_ALARM_ID) com.raunak.daytimeline.campus.CampusScheduler.onWakeDismissed(this)
            if (cancelSnooze && config.deleteAfterRinging && !config.isRepeating()) {
                bridge.cancel(config.id)
                AlarmPersistentStore(this).delete(config.id)
                references.clear(config.id)
                runtimeStore.clear(config.id)
            }
        }
        flow?.dismiss()
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); runtime.release(); super.onDestroy() }
}
