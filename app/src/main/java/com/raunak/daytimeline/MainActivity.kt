package com.raunak.daytimeline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            var advanced by mutableStateOf(false)
            Box(Modifier.fillMaxSize()) {
                PowerHome()
                if (!advanced) {
                    FloatingActionButton(
                        onClick = { advanced = true },
                        modifier = Modifier.padding(start = 18.dp, top = 10.dp),
                        containerColor = androidx.compose.ui.graphics.Color(0xFF55786A),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ) { Icon(Icons.Default.AutoAwesome, "Open advanced productivity tools") }
                }
            }
            if (advanced) AdvancedHub { advanced = false }
        }
    }
}
