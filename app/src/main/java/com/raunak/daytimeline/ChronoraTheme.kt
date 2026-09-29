package com.raunak.daytimeline

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raunak.daytimeline.features.OfflineProductivityStore
import com.raunak.daytimeline.ui.ChronoraThemeBase

/** App theme following the Light / Dark / System choice in settings. */
@Composable
fun ChronoraTheme(context: Context, content: @Composable () -> Unit) {
    val store = androidx.compose.runtime.remember(context) { OfflineProductivityStore(context.applicationContext) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    ChronoraThemeBase(dark, content)
}
