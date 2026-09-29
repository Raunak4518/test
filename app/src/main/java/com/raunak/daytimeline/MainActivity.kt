package com.raunak.daytimeline

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.raunak.daytimeline.productivity.ChronoraSecurityStore
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.alarm.AlarmCenter

class MainActivity : FragmentActivity() {
    private var unlocked = mutableStateOf(true)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val activityRecognitionPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onResume() {
        super.onResume()
        if (security.appLockEnabled && !authenticated && !authenticating) requestDeviceCredential()
    }

    override fun onStop() {
        super.onStop()
        authenticated = false
    }

    private fun requestDeviceCredential() {
        val manager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!manager.isKeyguardSecure) return
        val intent = manager.createConfirmDeviceCredentialIntent("Unlock Chronora", "Authenticate to continue")
        if (intent != null) { authenticating = true; credentialLauncher.launch(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val lockEnabled = com.raunak.daytimeline.features.CompletionStore(this).appLockEnabled()
        unlocked.value = !lockEnabled
        if (lockEnabled) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = androidx.biometric.BiometricPrompt(this, executor, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { unlocked.value = true }
            })
            val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Chronora")
                .setSubtitle("Authenticate to access your private productivity data")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
        }
        setContent {
            if (!unlocked.value) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Chronora is locked") } }
                return@setContent
            }
            var alarms by remember { mutableStateOf(false) }
            var completion by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(!com.raunak.daytimeline.features.CompletionStore(applicationContext).onboardingComplete()) }
            val app = remember { AppContainer(applicationContext) }
            val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
            val productivity = remember { OfflineProductivityStore(applicationContext) }
            Box(Modifier.fillMaxSize()) {
                PowerHome(onOpenAlarms = {
                    if (Build.VERSION.SDK_INT >= 29 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    alarms = true
                })
                FloatingActionButton(onClick = { completion = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 12.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("+" )
                }
            }
            if (alarms) {
                AlarmCenter(this@MainActivity) { alarms = false }
            }
            if (completion) {
                CompletionCenter(vm, productivity) { completion = false }
            }
            if (showOnboarding) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Welcome to Chronora") },
                    text = { Text("Chronora is offline-first. Tasks, habits, notes, alarms, reviews, study cards, analytics and backup remain on this device. Use the Command Center for calendar views, dependencies, search, study, analytics, reviews, ICS/CSV and focus controls.") },
                    confirmButton = { Button(onClick = { com.raunak.daytimeline.features.CompletionStore(applicationContext).setOnboardingComplete(); showOnboarding = false }) { Text("Start planning") } }
                )
            }
        }
    }
}
