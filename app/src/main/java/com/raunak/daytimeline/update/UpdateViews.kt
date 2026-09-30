package com.raunak.daytimeline.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.raunak.daytimeline.ui.Chronora
import kotlinx.coroutines.launch

/** Check → download → install, in one dialog. */
@Composable
fun UpdateDialog(close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var check by remember { mutableStateOf<UpdateCheck?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf("") }
    var canInstall by remember { mutableStateOf(AppUpdater.canInstall(context)) }
    val lifecycle = LocalLifecycleOwner.current
    // Re-check the "install unknown apps" switch when coming back from Settings.
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { canInstall = AppUpdater.canInstall(context); UpdateStatus.lastError?.let { status = it; UpdateStatus.lastError = null } } }
    LaunchedEffect(Unit) { check = AppUpdater.check() }

    fun update(r: AppRelease) {
        if (!AppUpdater.canInstall(context)) { runCatching { context.startActivity(AppUpdater.unknownSourcesIntent(context)) }; return }
        progress = 0f; status = "Downloading…"
        scope.launch {
            runCatching {
                val apk = AppUpdater.download(context, r) { p -> progress = p }
                status = "Installing…"
                AppUpdater.install(context, apk)
            }.onFailure { progress = null; status = it.message ?: "Update failed" }
        }
    }

    AlertDialog(onDismissRequest = { if (progress == null) close() }, title = { Text("App update") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Installed: ${AppUpdater.currentName}", color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
            when (val c = check) {
                null -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Checking…") }
                UpdateCheck.UpToDate -> Text("You have the latest version ✓", fontWeight = FontWeight.SemiBold, color = Chronora.colors.good)
                is UpdateCheck.Failed -> Text(c.message, color = Chronora.colors.bad)
                is UpdateCheck.Available -> {
                    val r = c.release
                    Text("${r.versionName} is ready", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (r.sizeBytes > 0) Text("%.1f MB".format(r.sizeBytes / 1_048_576.0), color = Chronora.muted, style = MaterialTheme.typography.bodySmall)
                    if (r.notes.isNotBlank()) Text(r.notes.take(1200), style = MaterialTheme.typography.bodySmall)
                    if (!canInstall) Text("Allow Chronora to install updates (one time) — tap Update and switch it on.", style = MaterialTheme.typography.bodySmall, color = Chronora.colors.warn)
                    progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) }
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        when (val c = check) {
            is UpdateCheck.Available -> Button(enabled = progress == null, onClick = { update(c.release) }) { Text(if (canInstall) "Update now" else "Allow & update") }
            is UpdateCheck.Failed -> TextButton(onClick = { check = null; scope.launch { check = AppUpdater.check() } }) { Text("Try again") }
            else -> TextButton(onClick = close) { Text("Close") }
        }
    }, dismissButton = { if (check is UpdateCheck.Available && progress == null) TextButton(onClick = close) { Text("Later") } })
}
