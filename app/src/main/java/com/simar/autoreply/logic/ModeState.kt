package com.simar.autoreply.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.simar.autoreply.data.AppStore
import com.simar.autoreply.data.Mode
import com.simar.autoreply.data.Schedule
import com.simar.autoreply.tile.ModeTileService
import com.simar.autoreply.widget.ModeWidget
import java.util.Calendar

/**
 * Decides which mode is on right now and keeps everything that depends on it in step.
 *
 * Precedence: a mode turned on by hand (until its timer, if any) → otherwise an enabled schedule
 * whose window covers now (unless schedules were switched off by hand until a later time).
 * Everything is evaluated lazily from stored state, so a late alarm can't leave a stale mode on:
 * alarms only exist to post the summary and refresh the widget/tile on time.
 */
object ModeState {

    data class Active(val mode: Mode, val until: Long?, val scheduled: Boolean)

    fun current(now: Long = System.currentTimeMillis()): Active? {
        val s = AppStore.settings.value
        val modes = AppStore.modes.value
        s.activeModeId?.let { id ->
            val mode = modes.find { it.id == id }
            if (mode != null && (s.activeUntil == null || now < s.activeUntil)) return Active(mode, s.activeUntil, scheduled = false)
        }
        if (now < s.suppressedUntil) return null
        for (schedule in AppStore.schedules.value) {
            if (!schedule.enabled) continue
            val end = windowEnd(schedule, now) ?: continue
            val mode = modes.find { it.id == schedule.modeId } ?: continue
            return Active(mode, end, scheduled = true)
        }
        return null
    }

    // ---- User actions ----

    fun turnOn(context: Context, modeId: String, until: Long? = null) {
        AppStore.updateSettings { it.copy(activeModeId = modeId, activeUntil = until, lastModeId = modeId, suppressedUntil = 0) }
        refresh(context)
    }

    fun turnOff(context: Context) {
        val cur = current()
        AppStore.updateSettings {
            it.copy(
                activeModeId = null,
                activeUntil = null,
                // Turning off a scheduled mode keeps it off until that schedule would have ended.
                suppressedUntil = if (cur?.scheduled == true) cur.until ?: 0 else it.suppressedUntil,
            )
        }
        refresh(context)
    }

    /** Keep the current mode on until [until] (null = until turned off). */
    fun setUntil(context: Context, until: Long?) {
        val cur = current() ?: return
        AppStore.updateSettings { it.copy(activeModeId = cur.mode.id, activeUntil = until, lastModeId = cur.mode.id) }
        refresh(context)
    }

    fun toggle(context: Context) {
        if (current() != null) {
            turnOff(context)
        } else {
            val modes = AppStore.modes.value
            val target = modes.find { it.id == AppStore.settings.value.lastModeId } ?: modes.firstOrNull() ?: return
            turnOn(context, target.id)
        }
    }

    /** Off → first mode → … → last mode → off. */
    fun cycle(context: Context) {
        val modes = AppStore.modes.value
        val idx = current()?.let { cur -> modes.indexOfFirst { it.id == cur.mode.id } } ?: -1
        val next = modes.getOrNull(idx + 1)
        if (next == null) turnOff(context) else turnOn(context, next.id)
    }

    // ---- Keeping things in step ----

    /**
     * Call after anything that can change which mode is on (and from alarms). Ends/starts mode
     * sessions, clears an expired timer, schedules the next alarm and refreshes widget + tile.
     */
    @Synchronized
    fun refresh(context: Context) {
        val now = System.currentTimeMillis()
        val s = AppStore.settings.value
        if (s.activeUntil != null && now >= s.activeUntil) {
            AppStore.updateSettings { it.copy(activeModeId = null, activeUntil = null) }
        }

        val cur = current(now)
        val session = AppStore.settings.value
        if (cur?.mode?.id != session.sessionModeId) {
            if (session.sessionModeId != null) {
                val name = AppStore.modes.value.find { it.id == session.sessionModeId }?.name
                Summary.sessionEnded(context, name, session.sessionStart)
            }
            AppStore.updateSettings { it.copy(sessionModeId = cur?.mode?.id, sessionStart = now) }
            // A new mode session starts fresh: everyone can get one reply again.
            AppStore.clearCooldowns()
        }

        scheduleNextAlarm(context, now)
        Summary.updateActive(context)
        ModeWidget.updateAll(context)
        runCatching { TileService.requestListeningState(context, ComponentName(context, ModeTileService::class.java)) }
    }

    private fun scheduleNextAlarm(context: Context, now: Long) {
        val times = buildList {
            AppStore.settings.value.activeUntil?.let { add(it) }
            AppStore.settings.value.suppressedUntil.takeIf { it > now }?.let { add(it) }
            AppStore.schedules.value.filter { it.enabled }.forEach { addAll(boundaries(it, now)) }
        }
        val next = times.filter { it > now }.minOrNull()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (next == null) return
        // A minute or two late is fine (see class doc), so exact alarms are only a nicety.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, pi)
        }
    }

    // ---- Schedule maths ----

    private fun atMinute(dayStart: Calendar, minute: Int, plusDays: Int = 0): Long =
        (dayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, plusDays)
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }.timeInMillis

    private fun midnight(time: Long, plusDays: Int = 0) = Calendar.getInstance().apply {
        timeInMillis = time
        add(Calendar.DAY_OF_YEAR, plusDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun Schedule.overnight() = endMinute <= startMinute

    /** If [schedule]'s window covers [now], when it ends; otherwise null. */
    fun windowEnd(schedule: Schedule, now: Long): Long? {
        // A window can only have started today or (overnight ones) yesterday.
        for (back in 0..1) {
            val day = midnight(now, -back)
            if (day.get(Calendar.DAY_OF_WEEK) !in schedule.days) continue
            val start = atMinute(day, schedule.startMinute)
            val end = atMinute(day, schedule.endMinute, if (schedule.overnight()) 1 else 0)
            if (now in start until end) return end
        }
        return null
    }

    /** Upcoming start/end times of [schedule] over the next week. */
    private fun boundaries(schedule: Schedule, now: Long): List<Long> = (-1..7).flatMap { d ->
        val day = midnight(now, d)
        if (day.get(Calendar.DAY_OF_WEEK) !in schedule.days) emptyList()
        else listOf(
            atMinute(day, schedule.startMinute),
            atMinute(day, schedule.endMinute, if (schedule.overnight()) 1 else 0),
        )
    }

    // ---- Formatting ----

    fun formatMinute(context: Context, minute: Int): String {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60) }
        return DateFormat.getTimeFormat(context).format(c.time)
    }

    /** "4:30 pm", "tomorrow 7:00 am" or "Mon 7:00 am". */
    fun formatUntil(context: Context, time: Long): String {
        val clock = DateFormat.getTimeFormat(context).format(time)
        return when {
            DateUtils.isToday(time) -> clock
            DateUtils.isToday(time - DateUtils.DAY_IN_MILLIS) -> "tomorrow $clock"
            else -> "${DateFormat.format("EEE", time)} $clock"
        }
    }
}
