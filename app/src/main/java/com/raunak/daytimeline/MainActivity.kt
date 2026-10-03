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
    private val quickAddRequest = mutableStateOf(false)

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
        handleOpen(intent)
        com.raunak.daytimeline.update.AppUpdater.scheduleDailyCheck(applicationContext)
        runCatching { com.raunak.daytimeline.pro.FocusMode.refresh(applicationContext) }
        if (com.raunak.daytimeline.features.OfflineProductivityStore(applicationContext).settings.value.pinnedQuickAdd) com.raunak.daytimeline.productivity.QuickAddNotification.show(applicationContext)

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: android.content.Intent?) {
        if (intent?.getStringExtra(com.raunak.daytimeline.update.AppUpdater.EXTRA_OPEN) == com.raunak.daytimeline.update.AppUpdater.OPEN_UPDATE) {
            com.raunak.daytimeline.update.UpdateNav.requested.value = true
            intent.removeExtra(com.raunak.daytimeline.update.AppUpdater.EXTRA_OPEN)
        }
        if (intent?.getStringExtra("chronora.open") == "trackers") {
            com.raunak.daytimeline.trackers.TrackerNav.open.value = true
            intent.removeExtra("chronora.open")
        }
        if (intent?.getStringExtra(com.raunak.daytimeline.classroom.ClassroomSync.EXTRA_OPEN) == "classroom") {
            com.raunak.daytimeline.campus.CampusNav.open("Classroom")
            intent.removeExtra(com.raunak.daytimeline.classroom.ClassroomSync.EXTRA_OPEN)
        }
        if (intent?.getStringExtra(com.raunak.daytimeline.pro.FocusWidget.EXTRA_OPEN) == com.raunak.daytimeline.pro.FocusWidget.OPEN_QUICK_ADD) {
            quickAddRequest.value = true
            intent.removeExtra(com.raunak.daytimeline.pro.FocusWidget.EXTRA_OPEN)
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
                onOpenCommandCenter = { completion = true },
                openQuickAdd = quickAddRequest.value,
                onQuickAddHandled = { quickAddRequest.value = false },
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
        }

        if (alarms) {
            androidx.activity.compose.BackHandler { alarms = false }
            AlarmCenter(this@MainActivity) {
                alarms = false
            }
        }

        if (completion) {
            androidx.activity.compose.BackHandler { completion = false }
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
