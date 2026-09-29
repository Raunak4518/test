package com.raunak.daytimeline.productivity

import android.app.KeyguardManager
import android.content.Context

class ChronoraSecurityStore(context: Context) {
    private val prefs = context.getSharedPreferences("chronora_security", Context.MODE_PRIVATE)
    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) { prefs.edit().putBoolean("app_lock", value).apply() }

    fun canUseDeviceCredential(context: Context): Boolean {
        val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return manager.isKeyguardSecure
    }
}
