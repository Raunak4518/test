package com.raunak.daytimeline.alarm

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max

class AlarmRingingActivity : ComponentActivity() {
    private lateinit var runtime: AlarmMissionRuntime
    private var flow: AlarmAlarmFlow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard?.requestDismissKeyguard(this, null)
        runtime = AlarmMissionRuntime(this)
        val typeName = intent.getStringExtra(EXTRA_MISSION) ?: MissionType.NONE.name
        val type = runCatching { MissionType.valueOf(typeName) }.getOrDefault(MissionType.NONE)
        val mission = AlarmMissionCatalog.single(type)
        flow = AlarmAlarmFlow(listOf(mission))
        setContent { RingingScreen() }
    }

    @Composable
    private fun RingingScreen() {
        val alarmFlow = flow ?: return
        var mission by remember { mutableStateOf(alarmFlow.currentMission()) }
        var progress by remember { mutableIntStateOf(0) }
        var answer by remember { mutableStateOf("") }
        var challenge by remember { mutableStateOf(AlarmChallengeMath.generate(2)) }
        var snoozes by remember { mutableIntStateOf(0) }
        val type = mission?.type ?: MissionType.NONE

        DisposableEffect(type) {
            if (type == MissionType.SHAKE) runtime.startShake(max(5, mission?.target ?: 30), { progress = it }) { dismiss() }
            if (type == MissionType.STEPS) runtime.startSteps(max(1, mission?.target ?: 40), { progress = it }) { dismiss() }
            onDispose { runtime.stopShake(); runtime.stopSteps() }
        }

        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("WAKE UP", style = MaterialTheme.typography.displaySmall)
            Text("Complete the challenge to dismiss this alarm.", style = MaterialTheme.typography.bodyLarge)
            Text("Mission: ${mission?.label ?: "Long press"}", style = MaterialTheme.typography.headlineSmall)

            when (type) {
                MissionType.MATH -> {
                    Text("${challenge.expression} = ?", style = MaterialTheme.typography.headlineMedium)
                    TextField(value = answer, onValueChange = { answer = it.filter(Char::isDigit) }, label = { Text("Answer") })
                    Button(onClick = {
                        if (answer.toIntOrNull() == challenge.answer) dismiss() else {
                            answer = ""
                            challenge = AlarmChallengeMath.generate(2)
                        }
                    }) { Text("Check") }
                }
                MissionType.TYPING -> {
                    Text("Type: ${mission?.payload}", style = MaterialTheme.typography.headlineSmall)
                    TextField(value = answer, onValueChange = { answer = it }, label = { Text("Type it exactly") })
                    Button(onClick = { if (alarmFlow.verifyText(answer)) dismiss() }) { Text("Verify") }
                }
                MissionType.SHAKE, MissionType.STEPS -> {
                    Text("Progress: $progress / ${mission?.target}", style = MaterialTheme.typography.headlineMedium)
                    Text("Keep going until the target is reached.")
                }
                else -> {
                    Text("Press and hold to dismiss", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { if (alarmFlow.dismiss()) dismiss() }) { Text("Dismiss") }
                }
            }

            OutlinedButton(onClick = {
                if (alarmFlow.snooze()) snoozes++
            }, enabled = alarmFlow.canSnooze()) {
                Text("Snooze 10 min ($snoozes/${AlarmMissionPolicy().maxSnoozes})")
            }
        }
    }

    private fun dismiss() {
        runtime.release()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        runtime.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MISSION = "alarm_mission"
        const val EXTRA_TITLE = "alarm_title"
    }
}
