package com.raunak.daytimeline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.raunak.daytimeline.alarm.AlarmCenter

class MainActivity : ComponentActivity() {
    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val activityRecognitionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            var advanced by remember { mutableStateOf(false) }
            var suite by remember { mutableStateOf(false) }
            var powerTools by remember { mutableStateOf(false) }
            var alarms by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                PowerHome()
                if (!advanced && !suite && !powerTools && !alarms) Column(modifier = Modifier.padding(start = 18.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FloatingActionButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION); alarms = true }) { Icon(Icons.Default.Alarm, "Open alarms") }
                    FloatingActionButton(onClick = { advanced = true }) { Icon(Icons.Default.AutoAwesome, "Open productivity tools") }
                    FloatingActionButton(onClick = { powerTools = true }) { Icon(Icons.Default.Build, "Open offline power tools") }
                }
            }
            if (advanced) AdvancedHub { advanced = false; suite = true }
            if (suite) OfflineSuite { suite = false }
            if (powerTools) OfflinePowerTools { powerTools = false }
            if (alarms) AlarmCenter(this@MainActivity) { alarms = false }
        }
    }

}
