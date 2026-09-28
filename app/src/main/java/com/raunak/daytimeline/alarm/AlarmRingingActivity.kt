package com.raunak.daytimeline.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    private var activeConfig: AlarmPersistentConfig? = null
    private var cameraCallback: ((android.graphics.Bitmap?) -> Unit)? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> cameraCallback?.invoke(bitmap); cameraCallback = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        runtime = AlarmMissionRuntime(this)
        val id = intent.getLongExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, -1L)
        activeConfig = AlarmPersistentStore(this).find(id)
        val config = activeConfig ?: run { finish(); return }
        flow = AlarmAlarmFlow(config.missionChain, AlarmMissionPolicy(config.maxSnoozes, config.snoozeMinutes, config.longPressMs, config.timeoutMinutes, config.backupDelayMinutes).validated())
        runtime.startDefaultAlarmSound()
        timeoutHandler.postDelayed({ if (!(flow?.isDismissed() ?: true)) dismissAlarm() }, config.timeoutMinutes * 60_000L)
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
            Text(activeConfig?.label ?: "Alarm", style = MaterialTheme.typography.titleLarge)
            Text("Mission ${f.missionIndex() + 1}", style = MaterialTheme.typography.labelLarge)
            when (type) {
                AlarmMissionType.MATH -> { Text("${math.first} = ?", style = MaterialTheme.typography.headlineMedium); OutlinedTextField(answer,{answer=it.filter(Char::isDigit)},label={Text("Answer")}); Button(onClick={if(AlarmChallengeEngine.validateMath(answer.toIntOrNull()?:Int.MIN_VALUE,math.second)) nextOrDismiss() else {answer="";math=AlarmChallengeEngine.math(2)}}){Text("Check")} }
                AlarmMissionType.TYPING -> { Text("Type exactly: ${mission?.payload}"); OutlinedTextField(answer,{answer=it},label={Text("Phrase")}); Button(onClick={if(AlarmChallengeEngine.validateTyping(answer,mission?.payload.orEmpty()))nextOrDismiss()}){Text("Verify")} }
                AlarmMissionType.MEMORY -> { Text("Repeat: ${mission?.payload}"); OutlinedTextField(memoryInput,{memoryInput=it.filter { c -> c.isDigit() || c == ',' }},label={Text("Sequence")}); Button(onClick={if(memoryInput.replace(" ","")==mission?.payload.orEmpty().replace(",", ""))nextOrDismiss()}){Text("Verify")} }
                AlarmMissionType.SHAKE, AlarmMissionType.WALK -> Text("Progress: $progress / ${mission?.target}",style=MaterialTheme.typography.headlineMedium)
                AlarmMissionType.SQUAT -> { Text("Complete ${mission?.target ?: 10} squats"); Text("Count them yourself, then hold the button below."); LongPressDismissButton(requiredMs=activeConfig?.longPressMs ?: 1200L, onComplete={nextOrDismiss()}) }
                AlarmMissionType.PHOTO -> { Text("Take a wake-up photo"); Button(onClick={cameraCallback={if(it!=null)nextOrDismiss()};camera.launch(null)}){Text("Open camera")} }
                AlarmMissionType.BARCODE -> { Text("Scan your registered barcode or QR code"); Button(onClick={cameraCallback={b->if(b!=null)AlarmCameraVerifier.scan(b){value->if(value!=null){scanned=value;nextOrDismiss()}}};camera.launch(null)}){Text("Scan")} ; if(scanned.isNotEmpty())Text("Detected: $scanned") }
                AlarmMissionType.MULTI -> nextOrDismiss()
            }
            val maxSnoozes=activeConfig?.maxSnoozes?:3
            OutlinedButton(onClick={if(f.snooze()){scheduleSnooze();dismissAlarm()}},enabled=f.canSnooze()){Text("Snooze ${activeConfig?.snoozeMinutes?:5} min (${f.snoozesUsed()}/$maxSnoozes)")}
        }
    }

    @Composable private fun LongPressDismissButton(requiredMs: Long, onComplete: () -> Unit) {
        var held by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(64.dp).pointerInput(requiredMs) { detectTapGestures(onPress = { held = true; val start=System.currentTimeMillis(); try { awaitRelease(); if(System.currentTimeMillis()-start >= requiredMs) onComplete() } finally { held=false } }) }) {
            Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text(if(held) "Keep holding…" else "Press and hold to complete") } }
        }
    }

    private fun nextOrDismiss() { val f=flow?:return; if(f.recordProgress(maxOf(1,f.currentMission()?.target?:1))) dismissAlarm() }
    private fun scheduleSnooze(){activeConfig?.let{AlarmManagerBridge(this).scheduleSnooze(it)}}
    private fun dismissAlarm(){
        val config=activeConfig ?: return
        runtime.release(); timeoutHandler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java)?.cancel((config.id xor (config.id ushr 32)).toInt())
        AlarmManagerBridge(this).cancel(config.id)
        if(config.enabled && config.repeatDays.isNotEmpty()) AlarmManagerBridge(this).schedule(config)
        flow?.dismiss()
        finishAndRemoveTask()
    }
    override fun onDestroy(){timeoutHandler.removeCallbacksAndMessages(null);runtime.release();super.onDestroy()}
}
