package com.raunak.daytimeline.pro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Full-screen block / mindful-pause screen shown over distracting apps. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_BLOCK
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val canUnlock = intent.getBooleanExtra(EXTRA_CAN_UNLOCK, false)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 10)
        val reopenApp = intent.getBooleanExtra(EXTRA_REOPEN, true)
        val store = FocusGuardStore(this)
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var remaining by remember { mutableIntStateOf(seconds) }
                LaunchedEffect(Unit) { while (remaining > 0) { delay(1000); remaining-- } }
                val transition = rememberInfiniteTransition(label = "breath")
                val scale by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "scale")
                val config = remember { store.config }
                val emergencyLeft = remember { FocusGuardEngine.emergencyRemaining(config, store.runtime, LocalDate.now()) }

                Column(
                    Modifier.fillMaxSize().background(Color(0xFF101815)).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(if (mode == MODE_INTERVENE) "Pause before $label" else "$label is blocked", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(reason, color = Color(0xFFB9CCC2), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(36.dp))
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(200.dp).scale(scale).background(Color(0xFF55786A), CircleShape))
                        Text(if (scale > 0.8f) "Breathe out" else "Breathe in", color = Color.White)
                    }
                    Spacer(Modifier.height(36.dp))
                    Button(onClick = { goHome() }, modifier = Modifier.fillMaxWidth()) { Text("Back to what matters") }
                    Spacer(Modifier.height(8.dp))
                    if (mode == MODE_INTERVENE) {
                        OutlinedButton(
                            enabled = remaining == 0,
                            onClick = {
                                store.runtime = FocusGuardEngine.grant(store.config, store.runtime, pkg, System.currentTimeMillis())
                                reopen(pkg)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (remaining > 0) "Open $label in ${remaining}s" else "Open $label for ${config.interventionGrantMinutes} min") }
                    } else if (canUnlock) {
                        OutlinedButton(
                            enabled = remaining == 0 && emergencyLeft > 0,
                            onClick = {
                                FocusGuardEngine.useEmergencyUnlock(store.config, store.runtime, LocalDate.now(), System.currentTimeMillis())?.let { store.runtime = it }
                                if (reopenApp) reopen(pkg) else finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (remaining > 0) "Emergency unlock in ${remaining}s" else "Emergency unlock ${config.emergencyUnlockMinutes} min ($emergencyLeft left today)") }
                    } else {
                        Text("Locked mode is on. This block ends with the session.", color = Color(0xFF8FA39A), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun reopen(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REASON = "reason"
        const val EXTRA_CAN_UNLOCK = "canUnlock"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_REOPEN = "reopen"
        const val MODE_BLOCK = "block"
        const val MODE_INTERVENE = "intervene"
    }
}
