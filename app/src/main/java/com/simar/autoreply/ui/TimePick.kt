package com.simar.autoreply.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import java.util.Calendar

/** Shows the system time picker (matching light/dark mode) and reports minutes after midnight. */
fun pickTime(context: Context, initialMinute: Int, onPicked: (Int) -> Unit) {
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    TimePickerDialog(
        context,
        if (dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
        { _, h, m -> onPicked(h * 60 + m) },
        initialMinute / 60, initialMinute % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

/** The next time the clock shows [minute] (later today, or tomorrow). */
fun nextOccurrence(minute: Int, now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

fun minuteOfDay(time: Long): Int = Calendar.getInstance().apply { timeInMillis = time }
    .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
