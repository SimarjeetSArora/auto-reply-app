package com.simar.autoreply.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.ContactNames
import com.simar.autoreply.data.ContactRule
import com.simar.autoreply.data.UnknownCallers
import com.simar.autoreply.data.phoneKey
import com.simar.autoreply.logic.Messages
import com.simar.autoreply.logic.ModeState
import com.simar.autoreply.logic.Summary

/**
 * Normal (cellular) calls. When the phone rings while a mode is on:
 *   VIP contact            → let it ring
 *   called again recently  → let it ring (probably urgent)
 *   unknown number         → per setting: text / decline only / let it ring
 *   otherwise              → decline (if enabled) and text the mode's (or contact's) message
 *
 * Android 10+ sends PHONE_STATE twice per ring, once without the number. Every decision here
 * needs the number, so only the broadcast carrying it is acted on.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_IDLE -> {
                AppStore.init(context)
                AppStore.endRing()
                return
            }
            TelephonyManager.EXTRA_STATE_RINGING -> Unit
            else -> return
        }
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (number.isNullOrBlank()) return

        AppStore.init(context)
        ModeState.refresh(context)
        val active = ModeState.current() ?: return
        val mode = active.mode
        val settings = AppStore.settings.value
        val key = phoneKey(number)
        val now = System.currentTimeMillis()

        if (!AppStore.beginRing(key, now)) return
        val previousCall = AppStore.recordCall(key, now)
        fun log(status: String, message: String = "") {
            AppStore.addLog(number, mode.name, status, message)
            Summary.updateActive(context)
        }

        val rule = AppStore.ruleFor(number)
        if (rule?.vip == true) return log("Rang through · VIP")
        if (settings.urgentOverride && previousCall != null && now - previousCall <= settings.urgentWindowMinutes * 60_000L) {
            return log("Rang through · called again within ${settings.urgentWindowMinutes} min")
        }

        val name = ContactNames.nameFor(context, number)
        // Without contacts permission we can't tell, so treat everyone as known.
        val unknown = name == null && rule == null && ContactNames.canRead(context)
        if (unknown && settings.unknownCallers == UnknownCallers.RING) return log("Rang through · unknown number")

        val decline = settings.declineCalls || (unknown && settings.unknownCallers == UnknownCallers.DECLINE)
        val reply = Reply(context, number, active, rule, name, unknown)
        val delay = if (decline) settings.declineDelaySeconds else 0
        if (delay <= 0) return respond(context, reply, decline)

        // Let it ring for a while first, so you can still pick up. goAsync keeps the receiver
        // alive for the wait (well within the system's 60 s limit for background broadcasts).
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                when {
                    isRinging(context) -> respond(context, reply, decline = true)
                    isInCall(context) -> reply.log("Answered · Not texted (you picked up)")
                    // They gave up before we declined: it's a missed call, so still text them.
                    else -> respond(context, reply, decline = false, missed = true)
                }
            } finally {
                pending.finish()
            }
        }, delay * 1000L)
    }

    private class Reply(
        val context: Context,
        val number: String,
        val active: ModeState.Active,
        val rule: ContactRule?,
        val name: String?,
        val unknown: Boolean,
    ) {
        /** Logs the outcome and refreshes the ongoing notification's list of callers. */
        fun log(status: String, message: String = "") {
            AppStore.addLog(number, active.mode.name, status, message)
            Summary.updateActive(context)
        }
    }

    /** Declines (if asked) and texts the caller, respecting the unknown-caller and repeat settings. */
    private fun respond(context: Context, r: Reply, decline: Boolean, missed: Boolean = false) {
        val settings = AppStore.settings.value
        val mode = r.active.mode
        val number = r.number
        val unknown = r.unknown
        val rule = r.rule
        val name = r.name
        fun log(status: String, message: String = "") = r.log(status, message)
        val key = phoneKey(number)

        var note = ""
        val declined = decline && endCall(context).also { ok -> if (!ok) note = " (couldn't decline the call)" }
        val prefix = when {
            declined -> "Declined · "
            missed -> "Missed · "
            else -> ""
        }

        if (unknown && settings.unknownCallers == UnknownCallers.DECLINE) {
            return log("${prefix}Not texted (unknown number)$note")
        }
        if (!AppStore.tryClaimReply("phone:$key")) {
            return log("${prefix}Not texted again – already texted in the last ${settings.cooldownMinutes} min$note")
        }

        val template = rule?.messageFor(mode.id) ?: mode.message
        val text = Messages.render(context, template, name ?: rule?.name, r.active.until)
        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            log("${prefix}SMS sent$note", text)
        } catch (e: Exception) {
            log("${prefix}SMS failed: ${e.message}$note")
        }
    }

    @SuppressLint("MissingPermission")
    private fun isRinging(context: Context) = runCatching {
        @Suppress("DEPRECATION")
        context.getSystemService(TelephonyManager::class.java).callState == TelephonyManager.CALL_STATE_RINGING
    }.getOrDefault(true)

    @SuppressLint("MissingPermission")
    private fun isInCall(context: Context) =
        runCatching { context.getSystemService(TelecomManager::class.java).isInCall }.getOrDefault(false)

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
