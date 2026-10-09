package com.simar.autoreply.data

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

data class Settings(
    val activeModeId: String? = null,
    val lastModeId: String? = null,
    /** Don't text the same number again within this many minutes (per mode session). */
    val cooldownMinutes: Int = 30,
    /** Hang up the ringing call (the caller hears it being declined) before texting them. */
    val declineCalls: Boolean = true,
)

data class LogEntry(
    val time: Long,
    val number: String,
    val mode: String,
    val status: String,
    /** The SMS text that went out (empty for skipped/failed, and for entries from older versions). */
    val message: String = "",
)

internal fun Mode.toJson() = JSONObject()
    .put("id", id).put("name", name).put("message", message).put("icon", icon).put("color", color)

internal fun JSONObject.toMode(): Mode {
    val name = getString("name")
    val (guessIcon, guessColor) = Defaults.lookFor(name)
    val icon = optString("icon", "").ifEmpty { Defaults.iconForEmoji[optString("emoji")] ?: guessIcon }
    return Mode(getString("id"), name, getString("message"), icon, optInt("color", guessColor))
}

internal fun Settings.toJson() = JSONObject()
    .put("activeModeId", activeModeId ?: JSONObject.NULL)
    .put("lastModeId", lastModeId ?: JSONObject.NULL)
    .put("cooldownMinutes", cooldownMinutes)
    .put("declineCalls", declineCalls)

internal fun JSONObject.toSettings() = Settings(
    activeModeId = if (isNull("activeModeId")) null else optString("activeModeId"),
    lastModeId = if (isNull("lastModeId")) null else optString("lastModeId"),
    cooldownMinutes = optInt("cooldownMinutes", 30),
    declineCalls = optBoolean("declineCalls", true) || optBoolean("voicemail", false),
)

internal fun LogEntry.toJson() = JSONObject()
    .put("time", time).put("number", number).put("mode", mode).put("status", status).put("message", message)

internal fun JSONObject.toLogEntry() =
    LogEntry(
        getLong("time"), optString("number", optString("caller")), getString("mode"), getString("status"),
        optString("message", ""),
    )
