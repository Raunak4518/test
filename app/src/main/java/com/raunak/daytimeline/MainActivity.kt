package com.raunak.daytimeline

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.alarm.AlarmCenter
import com.raunak.daytimeline.features.CompletionStore
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.productivity.ChronoraSecurityStore

class MainActivity : FragmentActivity() {
    private val security by lazy { ChronoraSecurityStore(applicationContext) }
    private var authenticated = false
    private var authenticating = false

    private val credentialLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            authenticating = false
            authenticated = it.resultCode == RESULT_OK
            if (!authenticated && security.appLockEnabled) finish()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val activityRecognitionPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ChronoraTheme(applicationContext) {
            val lockEnabled = security.appLockEnabled
            if (lockEnabled && !authenticated) {
                Surface(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Chronora is locked",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            } else {
                ChronoraContent()
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (security.appLockEnabled && !authenticated && !authenticating) {
            requestDeviceCredential()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!authenticating) authenticated = false
    }

    private fun requestDeviceCredential() {
        val manager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!manager.isKeyguardSecure) return

        val intent = manager.createConfirmDeviceCredentialIntent(
            "Unlock Chronora",
            "Authenticate to continue"
        )
        if (intent != null) {
            authenticating = true
            credentialLauncher.launch(intent)
        }
    }

    @Composable
    private fun ChronoraContent() {
        var alarms by remember { mutableStateOf(false) }
        var completion by remember { mutableStateOf(false) }
        var showOnboarding by remember {
            mutableStateOf(!CompletionStore(applicationContext).onboardingComplete())
        }

        val app = remember { AppContainer(applicationContext) }
        val vm: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory(app))
        val productivity = remember {
            OfflineProductivityStore(applicationContext)
        }

        Box(Modifier.fillMaxSize()) {
            PowerHome(
                onOpenAlarms = {
                    if (
                        Build.VERSION.SDK_INT >= 29 &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACTIVITY_RECOGNITION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        activityRecognitionPermission.launch(
                            Manifest.permission.ACTIVITY_RECOGNITION
                        )
                    }
                    alarms = true
                }
            )

            FloatingActionButton(
                onClick = { completion = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 86.dp, end = 12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text("+")
            }
        }

        if (alarms) {
            AlarmCenter(this@MainActivity) {
                alarms = false
            }
        }

        if (completion) {
            CompletionCenter(vm, productivity) {
                completion = false
            }
        }

        if (showOnboarding) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Welcome to Chronora") },
                text = {
                    Text(
                        "Chronora is offline-first. Tasks, habits, notes, alarms, " +
                            "reviews, study cards, analytics and backup remain on this device. " +
                            "Use the Command Center for calendar views, dependencies, search, " +
                            "study, analytics, reviews, ICS/CSV and focus controls."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            CompletionStore(applicationContext).setOnboardingComplete()
                            showOnboarding = false
                        }
                    ) {
                        Text("Start planning")
                    }
                }
            )
        }
    }
}
