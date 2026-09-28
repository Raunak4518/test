package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

class AlarmRingingActivity : ComponentActivity() {
    private lateinit var runtime: AlarmMissionRuntime
    private var flow: AlarmAlarmFlow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        runtime = AlarmMissionRuntime(this)
        runtime.startDefaultAlarmSound()
        val type = runCatching { MissionType.valueOf(intent.getStringExtra(EXTRA_MISSION) ?: MissionType.NONE.name) }.getOrDefault(MissionType.NONE)
        flow = AlarmAlarmFlow(listOf(AlarmMissionCatalog.single(type)))
        setContent { RingingScreen() }
    }

    @Composable
    private fun RingingScreen() {
        val alarmFlow = flow ?: return
        var progress by remember { mutableIntStateOf(0) }
        var answer by remember { mutableStateOf("") }
        var challenge by remember { mutableStateOf(AlarmChallengeMath.generate(2)) }
        var snoozes by remember { mutableIntStateOf(alarmFlow.snoozesUsed()) }
        val mission = alarmFlow.currentMission()
        val type = mission?.type ?: MissionType.NONE
        DisposableEffect(type) {
            if (type == MissionType.SHAKE) runtime.startShake(max(5, mission.target), { progress = it }) { dismiss() }
            if (type == MissionType.STEPS) runtime.startSteps(max(1, mission.target), { progress = it }) { dismiss() }
            onDispose { runtime.stopShake(); runtime.stopSteps() }
        }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("WAKE UP", style = MaterialTheme.typography.displaySmall)
            Text("${mission?.label ?: "Complete the mission"}", style = MaterialTheme.typography.headlineSmall)
            when (type) {
                MissionType.MATH -> {
                    Text("${challenge.expression} = ?", style = MaterialTheme.typography.headlineMedium)
                    TextField(answer, { answer = it.filter(Char::isDigit) }, label = { Text("Answer") })
                    Button(onClick = { if (answer.toIntOrNull() == challenge.answer) { if (alarmFlow.advanceAfterCorrectMath()) dismiss() } else { answer = ""; challenge = AlarmChallengeMath.generate(2) } }) { Text("Check") }
                }
                MissionType.TYPING -> {
                    Text("Type exactly: ${mission.payload}")
                    TextField(answer, { answer = it }, label = { Text("Phrase") })
                    Button(onClick = { if (alarmFlow.verifyText(answer)) dismiss() }) { Text("Verify") }
                }
                MissionType.SHAKE, MissionType.STEPS -> Text("Progress: $progress / ${mission.target}", style = MaterialTheme.typography.headlineMedium)
                else -> {
                    Text("Press and hold anywhere to dismiss")
                    Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { if (alarmFlow.dismiss()) dismiss() }) })
                }
            }
            OutlinedButton(onClick = { if (alarmFlow.snooze()) { snoozes++; scheduleSnooze() } }, enabled = alarmFlow.canSnooze()) {
                Text("Snooze 10 min ($snoozes/${AlarmMissionPolicy().maxSnoozes})")
            }
        }
    }

    private fun scheduleSnooze() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmChallengeReceiver::class.java).apply {
            putExtra(AlarmRingingActivity.EXTRA_MISSION, intent.getStringExtra(EXTRA_MISSION) ?: MissionType.NONE.name)
            putExtra(AlarmRingingActivity.EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE) ?: "Alarm")
        }
        val pending = PendingIntent.getBroadcast(this, 7100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + 10 * 60_000L
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        dismiss()
    }

    private fun dismiss() { runtime.release(); finishAndRemoveTask() }
    override fun onDestroy() { runtime.release(); super.onDestroy() }

    companion object {
        const val EXTRA_MISSION = "alarm_mission"
        const val EXTRA_TITLE = "alarm_title"
    }
}
