package com.simar.autoreply.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for modes, settings and the reply log.
 * Everything is small, so it lives as JSON in SharedPreferences and is mirrored into StateFlows
 * that the UI observes.
 */
object AppStore {
    private const val MAX_LOG = 200

    private lateinit var prefs: SharedPreferences

    private val _modes = MutableStateFlow<List<Mode>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())

    val modes: StateFlow<List<Mode>> = _modes
    val settings: StateFlow<Settings> = _settings
    val log: StateFlow<List<LogEntry>> = _log

    /** Called from the Application, receiver and tile; safe to call repeatedly. */
    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("auto_reply", Context.MODE_PRIVATE)
        // Leftovers from the removed internet-call and voicemail experiments.
        prefs.edit().remove("profiles").remove("reply_methods").remove("app_locked").apply()
        java.io.File(context.filesDir, "voicemails").deleteRecursively()

        _modes.value = prefs.getString("modes", null)
            ?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getJSONObject(it).toMode() } } }
            ?: Defaults.modes().also { saveModes(it) }
        _settings.value = prefs.getString("settings", null)?.let { JSONObject(it).toSettings() } ?: Settings()
        val stored = prefs.getString("log", null)
            ?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getJSONObject(it).toLogEntry() } } }
            ?: emptyList()
        // Drop entries written by the removed WhatsApp/voicemail experiments; only SMS replies remain.
        _log.value = stored.filter { "SMS" in it.status || "Not texted" in it.status }
        if (_log.value.size != stored.size) {
            prefs.edit().putString("log", JSONArray(_log.value.map { it.toJson() }).toString()).apply()
        }
    }

    // ---- Modes ----

    fun activeMode(): Mode? = _settings.value.activeModeId?.let { id -> _modes.value.find { it.id == id } }

    fun setActiveMode(id: String?) {
        updateSettings { it.copy(activeModeId = id, lastModeId = id ?: it.lastModeId) }
        // A new mode session starts fresh: everyone can get one reply again.
        clearCooldowns()
    }

    fun upsertMode(mode: Mode) {
        val list = _modes.value
        val updated = if (list.any { it.id == mode.id }) list.map { if (it.id == mode.id) mode else it } else list + mode
        saveModes(updated)
    }

    fun deleteMode(id: String) {
        saveModes(_modes.value.filterNot { it.id == id })
        if (_settings.value.activeModeId == id) setActiveMode(null)
    }

    private fun saveModes(list: List<Mode>) {
        _modes.value = list
        prefs.edit().putString("modes", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    // ---- Settings ----

    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit().putString("settings", s.toJson().toString()).apply()
    }

    // ---- Log ----

    @Synchronized
    fun addLog(number: String, mode: String, status: String, message: String = "") {
        val list = (listOf(LogEntry(System.currentTimeMillis(), number, mode, status, message)) + _log.value).take(MAX_LOG)
        _log.value = list
        prefs.edit().putString("log", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun deleteLog(entry: LogEntry) {
        _log.value = _log.value - entry
        prefs.edit().putString("log", JSONArray(_log.value.map { it.toJson() }).toString()).apply()
    }

    fun clearLog() {
        _log.value = emptyList()
        prefs.edit().remove("log").apply()
    }

    // ---- Cooldowns: one reply per number per mode session (and per cooldown window) ----

    /** Returns true and records the reply if [key] hasn't been replied to recently. */
    @Synchronized
    fun tryClaimReply(key: String): Boolean {
        val now = System.currentTimeMillis()
        val cooldowns = JSONObject(prefs.getString("cooldowns", "{}")!!)
        val last = cooldowns.optLong(key, 0L)
        if (now - last < _settings.value.cooldownMinutes * 60_000L) return false
        cooldowns.put(key, now)
        prefs.edit().putString("cooldowns", cooldowns.toString()).apply()
        return true
    }

    fun clearCooldowns() = prefs.edit().remove("cooldowns").apply()
}
