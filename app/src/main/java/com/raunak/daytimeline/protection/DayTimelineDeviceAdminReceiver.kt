package com.raunak.daytimeline.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DayTimelineDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        context.getSharedPreferences("protection", Context.MODE_PRIVATE).edit().putBoolean("admin_enabled", true).apply()
        Toast.makeText(context, "Day Timeline protection enabled", Toast.LENGTH_SHORT).show()
    }
    override fun onDisabled(context: Context, intent: Intent) {
        context.getSharedPreferences("protection", Context.MODE_PRIVATE).edit().putBoolean("admin_enabled", false).apply()
        Toast.makeText(context, "Day Timeline protection disabled", Toast.LENGTH_SHORT).show()
    }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? =
        "Protection is being disabled. Alarms and productivity data will remain local, but Android will allow normal uninstall after protection is disabled."
}