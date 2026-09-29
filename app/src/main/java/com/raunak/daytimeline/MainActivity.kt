package com.raunak.daytimeline

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raunak.daytimeline.features.OfflineProductivityStore
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.alarm.AlarmCenter

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val activityRecognitionPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            var alarms by remember { mutableStateOf(false) }
            var completion by remember { mutableStateOf(false) }
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
        }
    }
}
