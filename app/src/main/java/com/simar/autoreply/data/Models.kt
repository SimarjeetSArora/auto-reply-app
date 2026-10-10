package com.simar.autoreply.data

import org.json.JSONArray
import org.json.JSONObject

/** A situation like "Driving" with the message to text callers while it is active. */
data class Mode(
    val id: String,
    val name: String,
    val message: String,
    /** Icon key, see ui/ModeIcons.kt. */
    val icon: String = "chat",
    /** Index into the UI's accent palette. */
    val color: Int = 0,
)

/**
 * Turns [modeId] on automatically between [startMinute] and [endMinute] (minutes after midnight)
 * on [days] (Calendar.SUNDAY = 1 … SATURDAY = 7). If end ≤ start the window runs past midnight,
 * and the day is the one it starts on.
 */
data class Schedule(
    val id: String,
    val modeId: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int>,
    val enabled: Boolean = true,
)

/** Special handling for one contact. Matched on the last 10 digits of the number. */
data class ContactRule(
    val key: String,
    val name: String,
    val number: String,
    /** Always let their calls ring; never decline or text them. */
    val vip: Boolean = false,
    /** Sent instead of the mode's message when not blank, in any mode without its own below. */
    val message: String = "",
    /** Mode id → message for that mode only; takes priority over [message]. */
    val modeMessages: Map<String, String> = emptyMap(),
) {
    /** The personal message for [modeId], if this contact has one. */
    fun messageFor(modeId: String): String? =
        modeMessages[modeId]?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() }

    val hasMessages get() = message.isNotBlank() || modeMessages.values.any { it.isNotBlank() }
}

object UnknownCallers {
    const val TEXT = "text"
    const val DECLINE = "decline"
    const val RING = "ring"
}

data class Settings(
    /** Mode turned on by hand (or by extending a schedule with a timer). */
    val activeModeId: String? = null,
    /** When the manually-on mode switches itself off; null = until turned off. */
    val activeUntil: Long? = null,
    /** Schedules are ignored until this time (set when a scheduled mode is turned off by hand). */
    val suppressedUntil: Long = 0,
    val lastModeId: String? = null,
    /** Don't text the same number again within this many minutes (per mode session). */
    val cooldownMinutes: Int = 30,
    /** Hang up the ringing call before texting the caller. */
    val declineCalls: Boolean = true,
    /** Let the call ring this long before declining, so you can still pick up. 0 = straight away. */
    val declineDelaySeconds: Int = 0,
    /** A second call from the same number within [urgentWindowMinutes] rings through. */
    val urgentOverride: Boolean = true,
    val urgentWindowMinutes: Int = 3,
    /** What to do with numbers that aren't in contacts, see [UnknownCallers]. */
    val unknownCallers: String = UnknownCallers.TEXT,
    /** Notify with a summary of missed calls when a mode ends. */
    val summaryNotifications: Boolean = true,
    /** The mode session currently running (for the end-of-session summary). */
    val sessionModeId: String? = null,
    val sessionStart: Long = 0,
)

data class LogEntry(
    val time: Long,
    val number: String,
    val mode: String,
    val status: String,
    /** The SMS text that went out (empty for skipped/failed, and for entries from older versions). */
    val message: String = "",
)

/** Contacts and calls are matched on the last 10 digits, so +91 / 0 prefixes don't matter. */
fun phoneKey(number: String) = number.filter { it.isDigit() }.takeLast(10)

private fun JSONObject.optLongOrNull(key: String) = if (!has(key) || isNull(key)) null else optLong(key)
private fun JSONObject.optStringOrNull(key: String) = if (!has(key) || isNull(key)) null else optString(key)

internal fun Mode.toJson() = JSONObject()
    .put("id", id).put("name", name).put("message", message).put("icon", icon).put("color", color)

internal fun JSONObject.toMode(): Mode {
    val name = getString("name")
    val (guessIcon, guessColor) = Defaults.lookFor(name)
    val icon = optString("icon", "").ifEmpty { Defaults.iconForEmoji[optString("emoji")] ?: guessIcon }
    return Mode(getString("id"), name, getString("message"), icon, optInt("color", guessColor))
}

internal fun Schedule.toJson() = JSONObject()
    .put("id", id).put("modeId", modeId).put("start", startMinute).put("end", endMinute)
    .put("days", JSONArray(days.sorted())).put("enabled", enabled)

internal fun JSONObject.toSchedule(): Schedule {
    val d = getJSONArray("days")
    return Schedule(
        getString("id"), getString("modeId"), getInt("start"), getInt("end"),
        (0 until d.length()).map { d.getInt(it) }.toSet(), optBoolean("enabled", true),
    )
}

internal fun ContactRule.toJson() = JSONObject()
    .put("key", key).put("name", name).put("number", number).put("vip", vip).put("message", message)
    .put("modeMessages", JSONObject(modeMessages.filterValues { it.isNotBlank() }))

internal fun JSONObject.toContactRule() = ContactRule(
    getString("key"), optString("name"), optString("number"), optBoolean("vip"), optString("message"),
    optJSONObject("modeMessages")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap(),
)

internal fun Settings.toJson() = JSONObject()
    .put("activeModeId", activeModeId ?: JSONObject.NULL)
    .put("activeUntil", activeUntil ?: JSONObject.NULL)
    .put("suppressedUntil", suppressedUntil)
    .put("lastModeId", lastModeId ?: JSONObject.NULL)
    .put("cooldownMinutes", cooldownMinutes)
    .put("declineCalls", declineCalls)
    .put("declineDelaySeconds", declineDelaySeconds)
    .put("urgentOverride", urgentOverride)
    .put("urgentWindowMinutes", urgentWindowMinutes)
    .put("unknownCallers", unknownCallers)
    .put("summaryNotifications", summaryNotifications)
    .put("sessionModeId", sessionModeId ?: JSONObject.NULL)
    .put("sessionStart", sessionStart)

internal fun JSONObject.toSettings() = Settings(
    activeModeId = optStringOrNull("activeModeId"),
    activeUntil = optLongOrNull("activeUntil"),
    suppressedUntil = optLong("suppressedUntil", 0),
    lastModeId = optStringOrNull("lastModeId"),
    cooldownMinutes = optInt("cooldownMinutes", 30),
    // The removed voicemail experiment needed declining off; turn it back on for anyone who had it.
    declineCalls = optBoolean("declineCalls", true) || optBoolean("voicemail", false),
    declineDelaySeconds = optInt("declineDelaySeconds", 0),
    urgentOverride = optBoolean("urgentOverride", true),
    urgentWindowMinutes = optInt("urgentWindowMinutes", 3),
    unknownCallers = optString("unknownCallers", UnknownCallers.TEXT),
    summaryNotifications = optBoolean("summaryNotifications", true),
    sessionModeId = optStringOrNull("sessionModeId"),
    sessionStart = optLong("sessionStart", 0),
)

internal fun LogEntry.toJson() = JSONObject()
    .put("time", time).put("number", number).put("mode", mode).put("status", status).put("message", message)

internal fun JSONObject.toLogEntry() =
    LogEntry(
        getLong("time"), optString("number", optString("caller")), getString("mode"), getString("status"),
        optString("message", ""),
    )
