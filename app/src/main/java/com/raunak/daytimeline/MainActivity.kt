package com.raunak.daytimeline

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
            Surface(Modifier.fillMaxSize()) {
                PowerHome(onOpenAlarms = {
                    if (Build.VERSION.SDK_INT >= 29 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                    alarms = true
                })
            }
            if (alarms) {
                AlarmCenter(this@MainActivity) { alarms = false }
            }
        }
    }
}
