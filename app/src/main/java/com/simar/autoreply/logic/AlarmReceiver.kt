package com.simar.autoreply.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simar.autoreply.data.AppStore

/**
 * Fires at schedule/timer boundaries, and after boot, app updates and clock changes (which
 * clear or invalidate alarms). All it does is re-evaluate the mode state.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppStore.init(context)
        ModeState.refresh(context)
    }
}
