package com.simar.autoreply.logic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.simar.autoreply.R
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.ContactNames
import com.simar.autoreply.data.LogEntry
import com.simar.autoreply.ui.MainActivity
import com.simar.autoreply.ui.modeColor
import androidx.compose.ui.graphics.toArgb

/**
 * The app's two notifications:
 *  - an ongoing one while a mode is on: which mode, until when, and who has called so far;
 *  - a summary when the mode ends: "While you were Driving · 3 calls · Divi, Mom…".
 */
object Summary {
    private const val CHANNEL_SUMMARY = "summary"
    private const val CHANNEL_ACTIVE = "active_mode"
    private const val ID_SUMMARY = 1
    private const val ID_ACTIVE = 2

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Missed-call summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Who called while a mode was on"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVE, "Active mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a mode is on, with the calls so far"
                setShowBadge(false)
            },
        )
    }

    private fun openActivity(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Callers since [since], one line each, plus a one-line overview. */
    private class Calls(context: Context, since: Long) {
        val entries: List<LogEntry> = AppStore.log.value.filter { it.time >= since }
        private val byNumber = entries.groupBy { it.number }
        private val names = ContactNames.lookup(context, byNumber.keys)
        private fun label(number: String) = names[number] ?: number

        val overview: String = run {
            val people = byNumber.keys.map(::label)
            buildString {
                append(if (entries.size == 1) "1 call" else "${entries.size} calls")
                append(" · ")
                append(people.take(3).joinToString(", "))
                if (people.size > 3) append(" +${people.size - 3}")
            }
        }

        fun addLines(style: NotificationCompat.InboxStyle) = byNumber.entries.take(6).forEach { (number, list) ->
            val count = if (list.size > 1) " (${list.size}×)" else ""
            style.addLine("${label(number)}$count — ${describe(list.first().status)}")
        }
    }

    // ---- Ongoing "mode is on" notification ----

    /** Shows, refreshes or removes the ongoing notification to match the current mode. */
    fun updateActive(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val cur = ModeState.current()
        if (cur == null || !canNotify(context)) {
            nm.cancel(ID_ACTIVE)
            return
        }
        channels(nm)

        val calls = Calls(context, AppStore.settings.value.sessionStart)
        val title = "${cur.mode.name} is on"
        val whenText = when {
            cur.until == null -> "Until you turn it off"
            cur.scheduled -> "Scheduled until ${ModeState.formatUntil(context, cur.until)}"
            else -> "Until ${ModeState.formatUntil(context, cur.until)}"
        }
        val text = if (calls.entries.isEmpty()) "$whenText · No calls yet" else calls.overview
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_tile)
            .setColor(modeColor(cur.mode.color).toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(whenText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openActivity(context))
            // Android 14+ lets people swipe away ongoing notifications; put it straight back.
            .setDeleteIntent(action(context, NotificationActions.ACTION_REPOST))
            .addAction(0, "Turn off", action(context, NotificationActions.ACTION_TURN_OFF))
        if (calls.entries.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(whenText)
            calls.addLines(style)
            builder.setStyle(style)
        }
        nm.notify(ID_ACTIVE, builder.build())
    }

    private fun action(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, NotificationActions::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ---- End-of-mode summary ----

    fun sessionEnded(context: Context, modeName: String?, since: Long) {
        if (!AppStore.settings.value.summaryNotifications || !canNotify(context)) return
        val calls = Calls(context, since)
        if (calls.entries.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java)
        channels(nm)
        val title = "While you were ${modeName ?: "busy"}"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText("Tap to see details")
        calls.addLines(style)
        nm.notify(
            ID_SUMMARY,
            NotificationCompat.Builder(context, CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_tile)
                .setColor(0xFF4F46E5.toInt())
                .setContentTitle(title)
                .setContentText(calls.overview)
                .setStyle(style)
                .setContentIntent(openActivity(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun describe(status: String) = when {
        status.startsWith("Rang through") -> "rang through"
        status.startsWith("Answered") -> "you answered"
        "SMS sent" in status -> if (status.startsWith("Declined")) "declined & texted" else "texted"
        "Not texted" in status -> "not texted"
        else -> "couldn't text"
    }
}

/** Buttons and swipe-away on the ongoing notification. */
class NotificationActions : BroadcastReceiver() {
    companion object {
        const val ACTION_TURN_OFF = "com.simar.autoreply.TURN_OFF"
        const val ACTION_REPOST = "com.simar.autoreply.REPOST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppStore.init(context)
        when (intent.action) {
            ACTION_TURN_OFF -> ModeState.turnOff(context)
            ACTION_REPOST -> Summary.updateActive(context)
        }
    }
}
