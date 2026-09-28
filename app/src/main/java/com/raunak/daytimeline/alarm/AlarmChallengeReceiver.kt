package com.raunak.daytimeline.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Entry point for AlarmManager: launches the real ringing UI. */
class AlarmChallengeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmRingingActivity.EXTRA_TITLE, intent.getStringExtra(AlarmRingingActivity.EXTRA_TITLE))
            putExtra(AlarmRingingActivity.EXTRA_MISSION, intent.getStringExtra(AlarmRingingActivity.EXTRA_MISSION) ?: MissionType.NONE.name)
        }
        context.startActivity(launch)
    }
}
