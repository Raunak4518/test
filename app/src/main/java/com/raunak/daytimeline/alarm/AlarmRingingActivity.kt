package com.raunak.daytimeline.alarm

import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AlarmRingingActivity : ComponentActivity() {
    companion object { const val EXTRA_TEST_MODE = "alarm_test_mode" }
    private lateinit var runtime: AlarmMissionRuntime
    private lateinit var references: AlarmReferenceStore
    private var flow: AlarmAlarmFlow? = null
    private var activeConfig: AlarmPersistentConfig? = null
    private var missionRevision by androidx.compose.runtime.mutableIntStateOf(0)
    private var cameraCallback: ((android.graphics.Bitmap?) -> Unit)? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
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
        runtime.startAlarmSound(config)
        // Timeout is a safety telemetry threshold, not an automatic dismissal: a wake-up alarm must not silently clear before its mission chain is completed.
        setContent { RingingScreen() }
    }

    @Composable private fun RingingScreen() {
        val f = flow ?: return
        val config = activeConfig ?: return
        val revision = missionRevision
        var progress by remember { mutableIntStateOf(f.progress()) }
        var answer by remember { mutableStateOf("") }
        var memoryInput by remember { mutableStateOf("") }
        var memoryVisible by remember(config.id, f.missionIndex()) { mutableStateOf(true) }
        var math by remember(config.id, f.missionIndex()) { mutableStateOf(AlarmChallengeEngine.math(f.currentMission()?.difficulty ?: 2)) }
        var scanned by remember { mutableStateOf("") }
        var feedback by remember { mutableStateOf("") }
        val mission = f.currentMission()
        val type = mission?.type ?: AlarmMissionType.TYPING

        LaunchedEffect(type, f.missionIndex(), revision) {
            answer = ""; memoryInput = ""; feedback = ""; progress = f.progress()
            if (type == AlarmMissionType.MEMORY) {
                memoryVisible = true
                kotlinx.coroutines.delay(1800L)
                memoryVisible = false
            }
        }

        DisposableEffect(type, f.missionIndex(), revision) {
            when (type) {
                AlarmMissionType.SHAKE -> runtime.startShake(maxOf(5, mission?.target ?: 30), { progress = it }) { nextOrDismiss() }
                AlarmMissionType.WALK -> runtime.startSteps(maxOf(1, mission?.target ?: 40), { progress = it }) { nextOrDismiss() }
                AlarmMissionType.SQUAT -> runtime.startSquats(maxOf(1, mission?.target ?: 10), { progress = it }) { nextOrDismiss() }
                else -> Unit
            }
            onDispose { runtime.stopShake(); runtime.stopSteps(); runtime.stopSquats() }
        }

        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (intent.getBooleanExtra(EXTRA_TEST_MODE, false)) "ALARM TEST" else "WAKE UP", style = MaterialTheme.typography.displaySmall)
            Text(config.label, style = MaterialTheme.typography.titleLarge)
            Text("Mission ${f.missionIndex() + 1} / ${config.missionChain.size}", style = MaterialTheme.typography.labelLarge)
            if (config.timeoutMinutes > 0) Text("Mission timeout ${config.timeoutMinutes}m · alarm remains active until verified", style = MaterialTheme.typography.labelSmall)
            when (type) {
                AlarmMissionType.MATH -> {
                    Text("${math.first} = ?", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(answer, { answer = it.filter(Char::isDigit) }, label = { Text("Answer") })
                    Button(onClick = { if (AlarmChallengeEngine.validateMath(answer.toIntOrNull() ?: Int.MIN_VALUE, math.second)) nextOrDismiss() else { answer = ""; math = AlarmChallengeEngine.math(mission?.difficulty ?: 2); feedback = "Incorrect. Solve the new problem." } }) { Text("Check") }
                }
                AlarmMissionType.TYPING -> {
                    Text("Type exactly: ${mission?.payload}")
                    OutlinedTextField(answer, { answer = it }, label = { Text("Phrase") })
                    Button(onClick = { if (AlarmChallengeEngine.validateTyping(answer, mission?.payload.orEmpty())) nextOrDismiss() else feedback = "Text does not match." }) { Text("Verify") }
                }
                AlarmMissionType.MEMORY -> {
                    if (memoryVisible) { Text(mission?.payload.orEmpty(), style = MaterialTheme.typography.headlineMedium); Text("Memorize it. It will disappear shortly.") }
                    else {
                        Text("Recall the sequence")
                        OutlinedTextField(memoryInput, { memoryInput = it.filter { c -> c.isDigit() || c == ',' } }, label = { Text("Sequence") })
                        Button(onClick = { val expected = mission?.payload.orEmpty().replace(",", "").replace(" ", ""); val actual = memoryInput.replace(",", "").replace(" ", ""); if (actual == expected) nextOrDismiss() else feedback = "Sequence is incorrect." }) { Text("Verify") }
                    }
                }
                AlarmMissionType.SHAKE, AlarmMissionType.WALK, AlarmMissionType.SQUAT -> {
                    Text("Progress: $progress / ${mission?.target}", style = MaterialTheme.typography.headlineMedium)
                    Text(if (type == AlarmMissionType.SQUAT) "Complete the detected squat cycles." else if (type == AlarmMissionType.WALK) "Walk until the step target is reached." else "Shake the phone firmly.")
                }
                AlarmMissionType.PHOTO -> {
                    val registered = references.hasPhoto(config.id)
                    Text(if (registered) "Take the registered wake-up photo." else "Register a reference photo for this mission first.")
                    Button(enabled = registered, onClick = {
                        cameraCallback = { bitmap -> if (bitmap != null && references.verifyPhoto(config.id, bitmap)) nextOrDismiss() else feedback = "Photo does not match the registered reference." }
                        camera.launch(null)
                    }) { Text("Verify photo") }
                }
                AlarmMissionType.BARCODE -> {
                    val expected = references.barcode(config.id)
                    Text(if (expected == null) "Register a barcode for this mission first." else "Scan the registered barcode or QR code.")
                    Button(enabled = expected != null, onClick = {
                        cameraCallback = { bitmap -> if (bitmap != null) AlarmCameraVerifier.scan(bitmap) { value -> if (value != null && value == expected) { scanned = value; nextOrDismiss() } else { scanned = value.orEmpty(); feedback = "Wrong barcode." } } }
                        camera.launch(null)
                    }) { Text("Scan") }
                    if (scanned.isNotEmpty()) Text("Detected: $scanned")
                }
                AlarmMissionType.MULTI -> { Text("Mission chain"); Button(onClick = { nextOrDismiss() }) { Text("Continue") } }
            }
            if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.error)
            val policy = config.snoozePolicy()
            val nextSnooze = policy.nextDuration(f.snoozesUsed(), f.totalSnoozeMinutes(), java.time.LocalDateTime.now(), java.time.LocalDateTime.now())
            OutlinedButton(onClick = { if (f.snooze(nextSnooze)) { AlarmManagerBridge(this@AlarmRingingActivity).scheduleSnooze(config, nextSnooze); dismissAlarm(cancelSnooze = false, rescheduleRepeat = false) } }, enabled = nextSnooze > 0 && f.canSnooze()) {
                Text("Snooze ${nextSnooze.coerceAtLeast(0)} min (${f.snoozesUsed()}/${config.maxSnoozes})")
            }
        }
    }

    private fun nextOrDismiss() {
        val f = flow ?: return
        if (f.recordProgress(maxOf(1, f.currentMission()?.target ?: 1))) dismissAlarm() else missionRevision++
    }

    private fun dismissAlarm(cancelSnooze: Boolean = true, rescheduleRepeat: Boolean = true) {
        val config = activeConfig ?: return
        val testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
        runtime.release()
        timeoutHandler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java)?.cancel((config.id xor (config.id ushr 32)).toInt())
        val bridge = AlarmManagerBridge(this)
        bridge.cancelScheduledCycle(config.id)
        if (!testMode) {
            val runtimeStore = AlarmRuntimeStore(this)
            runtimeStore.markDismissed(config.id)
            if (rescheduleRepeat && config.enabled && config.isRepeating() && !config.deleteAfterRinging) bridge.schedule(config)
            if (cancelSnooze && config.wakeCheckMinutes > 0) bridge.scheduleWakeChecksAfterDismissal(config)
            if (config.deleteAfterRinging && !config.isRepeating()) {
                bridge.cancel(config.id)
                AlarmPersistentStore(this).delete(config.id)
                references.clear(config.id)
                runtimeStore.clear(config.id)
            }
        }
        flow?.dismiss()
        finishAndRemoveTask()
    }

    override fun onDestroy() { timeoutHandler.removeCallbacksAndMessages(null); runtime.release(); super.onDestroy() }
}