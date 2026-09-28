package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max

class AlarmRingingActivity : ComponentActivity() {
    private lateinit var runtime: AlarmMissionRuntime
    private var flow: AlarmAlarmFlow? = null
    private var activeConfig: AlarmPersistentConfig? = null
    private var cameraCallback: ((android.graphics.Bitmap?) -> Unit)? = null
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> cameraCallback?.invoke(bitmap); cameraCallback = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        runtime = AlarmMissionRuntime(this)
        val id = intent.getLongExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, -1L)
        activeConfig = AlarmPersistentStore(this).find(id)
        val missions = activeConfig?.missionChain ?: listOf(AlarmMissionCatalog.default(AlarmMissionType.TYPING))
        flow = AlarmAlarmFlow(missions, AlarmMissionPolicy(maxSnoozes = activeConfig?.maxSnoozes ?: 3, snoozeMinutes = activeConfig?.snoozeMinutes ?: 5).validated())
        runtime.startDefaultAlarmSound()
        setContent { RingingScreen() }
    }

    @Composable private fun RingingScreen() {
        val f = flow ?: return
        var progress by remember { mutableIntStateOf(f.progress()) }
        var answer by remember { mutableStateOf("") }
        var memoryInput by remember { mutableStateOf("") }
        var math by remember { mutableStateOf(AlarmChallengeEngine.math(2)) }
        var scanned by remember { mutableStateOf("") }
        val mission = f.currentMission()
        val type = mission?.type ?: AlarmMissionType.TYPING
        DisposableEffect(type) {
            when (type) {
                AlarmMissionType.SHAKE -> runtime.startShake(max(5, mission?.target ?: 30), { progress = it }) { nextOrDismiss() }
                AlarmMissionType.WALK -> runtime.startSteps(max(1, mission?.target ?: 40), { progress = it }) { nextOrDismiss() }
                else -> Unit
            }
            onDispose { runtime.stopShake(); runtime.stopSteps() }
        }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("WAKE UP", style = MaterialTheme.typography.displaySmall)
            Text(type.name, style = MaterialTheme.typography.labelLarge)
            when (type) {
                AlarmMissionType.MATH -> { Text("${math.first} = ?", style = MaterialTheme.typography.headlineMedium); OutlinedTextField(answer,{answer=it.filter(Char::isDigit)},label={Text("Answer")}); Button(onClick={if(AlarmChallengeEngine.validateMath(answer.toIntOrNull()?:Int.MIN_VALUE,math.second)) nextOrDismiss() else {answer="";math=AlarmChallengeEngine.math(2)}}){Text("Check")} }
                AlarmMissionType.TYPING -> { Text("Type exactly: ${mission?.payload}"); OutlinedTextField(answer,{answer=it},label={Text("Phrase")}); Button(onClick={if(AlarmChallengeEngine.validateTyping(answer,mission?.payload.orEmpty()))nextOrDismiss()}){Text("Verify")} }
                AlarmMissionType.MEMORY -> { val expected=mission?.payload.orEmpty(); Text("Repeat: $expected"); OutlinedTextField(memoryInput,{memoryInput=it.filter(Char::isDigit)},label={Text("Sequence")}); Button(onClick={if(memoryInput.replace(" ","")==expected.replace(",",""))nextOrDismiss()}){Text("Verify")}; Text("Enter digits without spaces") }
                AlarmMissionType.SHAKE, AlarmMissionType.WALK -> Text("Progress: $progress / ${mission?.target}",style=MaterialTheme.typography.headlineMedium)
                AlarmMissionType.SQUAT -> { Text("Complete ${mission?.target ?: 10} squats"); Button(onClick={nextOrDismiss()}){Text("I completed them")}; Text("Keep this as a physical wake-up mission; the app does not disable emergency/device controls.") }
                AlarmMissionType.PHOTO -> { Text("Take a photo to complete this mission"); Button(onClick={cameraCallback={if(it!=null)nextOrDismiss()};camera.launch(null)}){Text("Open camera")} }
                AlarmMissionType.BARCODE -> { Text("Scan your saved barcode / QR code"); Button(onClick={cameraCallback={b->if(b!=null)AlarmCameraVerifier.scan(b){value->if(value!=null){scanned=value;nextOrDismiss()}}};camera.launch(null)}){Text("Scan")} ; if(scanned.isNotEmpty())Text("Scanned: $scanned") }
                AlarmMissionType.MULTI -> nextOrDismiss()
            }
            val maxSnoozes=activeConfig?.maxSnoozes?:3
            OutlinedButton(onClick={if(f.snooze()){scheduleSnooze();dismiss()}},enabled=f.canSnooze()){Text("Snooze ${activeConfig?.snoozeMinutes?:5} min (${f.snoozesUsed()}/$maxSnoozes)")}
        }
    }

    private fun nextOrDismiss() { val f=flow?:return; if(f.recordProgress(maxOf(1,f.currentMission()?.target?:1))) dismiss() else Unit }
    private fun scheduleSnooze(){activeConfig?.let{val manager=getSystemService(AlarmManager::class.java);AlarmManagerBridge(this).scheduleSnooze(it)}}
    private fun dismiss(){runtime.release();finishAndRemoveTask()}
    override fun onDestroy(){runtime.release();super.onDestroy()}
    companion object { const val EXTRA_MISSION="alarm_mission"; const val EXTRA_TITLE="alarm_title" }
}
