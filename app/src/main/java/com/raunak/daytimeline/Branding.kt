package com.raunak.daytimeline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

const val CHRONORA_NAME = "Chronora"
const val CHRONORA_TAGLINE = "Plan • Focus • Build • Become"
const val RAUNAK_GITHUB_URL = "https://github.com/Raunak4518"

fun openRaunakGithub(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(RAUNAK_GITHUB_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun MadeByRaunak(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Made by", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Raunak",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { openRaunakGithub(context) }
        )
    }
}

@Composable
fun ChronoraBrandLine(modifier: Modifier = Modifier) {
    Text(
        "$CHRONORA_NAME  ·  $CHRONORA_TAGLINE",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
