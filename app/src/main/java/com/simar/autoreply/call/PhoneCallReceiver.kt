package com.simar.autoreply.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.simar.autoreply.data.AppStore

/**
 * Normal (cellular) calls: when the phone starts ringing and a mode is active, decline the call
 * (if enabled) and text the caller.
 *
 * The system sends PHONE_STATE twice per ring on Android 10+ (once without the number, once with
 * it, the latter only if we hold READ_CALL_LOG). We decline on whichever arrives first and text on
 * the one carrying the number; the cooldown claim makes sure we only text once.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        /** When we last hung up a call, so the second broadcast knows the first one already did. */
        @Volatile
        private var declinedAt = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return

        AppStore.init(context)
        val mode = AppStore.activeMode() ?: return
        val settings = AppStore.settings.value

        var declineNote = ""
        if (settings.declineCalls) {
            val now = SystemClock.elapsedRealtime()
            if (now - declinedAt > 5_000) {
                if (endCall(context)) declinedAt = now else declineNote = " (couldn't decline the call)"
            }
        }

        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (number.isNullOrBlank()) return
        val prefix = if (settings.declineCalls && declineNote.isEmpty()) "Declined · " else ""

        if (!AppStore.tryClaimReply("phone:" + number.filter { it.isDigit() }.takeLast(10))) {
            AppStore.addLog(
                number, mode.name,
                "${prefix}Not texted again – already texted in the last ${settings.cooldownMinutes} min$declineNote",
            )
            return
        }

        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(mode.message), null, null)
            AppStore.addLog(number, mode.name, "${prefix}SMS sent$declineNote", mode.message)
        } catch (e: Exception) {
            AppStore.addLog(number, mode.name, "${prefix}SMS failed: ${e.message}$declineNote")
        }
    }

    /** Hangs up the ringing call. Needs ANSWER_PHONE_CALLS; deprecated in API 29 but still honoured. */
    @SuppressLint("MissingPermission")
    private fun endCall(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            context.getSystemService(TelecomManager::class.java).endCall()
        } catch (e: Exception) {
            false
        }
    }
}
