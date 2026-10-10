package com.simar.autoreply.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for modes, schedules, contact rules, settings and the reply log.
 * Everything is small, so it lives as JSON in SharedPreferences and is mirrored into StateFlows
 * that the UI observes. Turning modes on/off goes through [com.simar.autoreply.logic.ModeState].
 */
object AppStore {
    private const val MAX_LOG = 200
    private const val BACKUP_VERSION = 1

    private lateinit var prefs: SharedPreferences

    private val _modes = MutableStateFlow<List<Mode>>(emptyList())
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    private val _rules = MutableStateFlow<List<ContactRule>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())

    val modes: StateFlow<List<Mode>> = _modes
    val schedules: StateFlow<List<Schedule>> = _schedules
    val rules: StateFlow<List<ContactRule>> = _rules
    val settings: StateFlow<Settings> = _settings
    val log: StateFlow<List<LogEntry>> = _log

    /** Called from the Application, receivers, tile and widget; safe to call repeatedly. */
    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("auto_reply", Context.MODE_PRIVATE)
        load()
        migrateOnce(context)
    }

    private fun load() {
        _modes.value = prefs.getString("modes", null)?.let { parseList(it) { o -> o.toMode() } }
            ?: Defaults.modes().also { saveModes(it) }
        _schedules.value = prefs.getString("schedules", null)?.let { parseList(it) { o -> o.toSchedule() } } ?: emptyList()
        _rules.value = prefs.getString("rules", null)?.let { parseList(it) { o -> o.toContactRule() } } ?: emptyList()
        _settings.value = prefs.getString("settings", null)?.let { JSONObject(it).toSettings() } ?: Settings()
        _log.value = prefs.getString("log", null)?.let { parseList(it) { o -> o.toLogEntry() } } ?: emptyList()
    }

    /** One-off clean-up of data left by the removed internet-call and voicemail experiments. */
    private fun migrateOnce(context: Context) {
        if (prefs.getBoolean("migrated_v2", false)) return
        prefs.edit().remove("profiles").remove("reply_methods").remove("app_locked").apply()
        java.io.File(context.filesDir, "voicemails").deleteRecursively()
        java.io.File(context.filesDir, "captures").deleteRecursively()
        saveLog(_log.value.filter { "SMS" in it.status || "Not texted" in it.status })
        prefs.edit().putBoolean("migrated_v2", true).apply()
    }

    private fun <T> parseList(json: String, parse: (JSONObject) -> T): List<T> =
        JSONArray(json).let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } }

    private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject) = JSONArray(map(toJson))

    // ---- Modes ----

    fun upsertMode(mode: Mode) = saveModes(_modes.value.upsert(mode) { it.id == mode.id })

    /** Also removes the mode's schedules. Turn the mode off first via ModeState if it's on. */
    fun deleteMode(id: String) {
        saveModes(_modes.value.filterNot { it.id == id })
        saveSchedules(_schedules.value.filterNot { it.modeId == id })
    }

    private fun saveModes(list: List<Mode>) {
        _modes.value = list
        prefs.edit().putString("modes", list.toJsonArray { it.toJson() }.toString()).apply()
    }

    // ---- Schedules ----

    fun upsertSchedule(schedule: Schedule) = saveSchedules(_schedules.value.upsert(schedule) { it.id == schedule.id })

    fun deleteSchedule(id: String) = saveSchedules(_schedules.value.filterNot { it.id == id })

    private fun saveSchedules(list: List<Schedule>) {
        _schedules.value = list
        prefs.edit().putString("schedules", list.toJsonArray { it.toJson() }.toString()).apply()
    }

    // ---- Contact rules (VIP / custom message) ----

    fun ruleFor(number: String): ContactRule? = phoneKey(number).let { k -> _rules.value.find { it.key == k } }

    fun upsertRule(rule: ContactRule) = saveRules(_rules.value.upsert(rule) { it.key == rule.key })

    fun deleteRule(key: String) = saveRules(_rules.value.filterNot { it.key == key })

    private fun saveRules(list: List<ContactRule>) {
        _rules.value = list
        prefs.edit().putString("rules", list.toJsonArray { it.toJson() }.toString()).apply()
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
        saveLog((listOf(LogEntry(System.currentTimeMillis(), number, mode, status, message)) + _log.value).take(MAX_LOG))
    }

    fun deleteLog(entry: LogEntry) = saveLog(_log.value - entry)

    fun clearLog() = saveLog(emptyList())

    private fun saveLog(list: List<LogEntry>) {
        _log.value = list
        prefs.edit().putString("log", list.toJsonArray { it.toJson() }.toString()).apply()
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

    // ---- Recent calls, for "called twice = urgent" ----

    /** Records a ringing call from [key] and returns when they last called before it (null if never / long ago). */
    @Synchronized
    fun recordCall(key: String, now: Long): Long? {
        val calls = JSONObject(prefs.getString("recent_calls", "{}")!!)
        val previous = if (calls.has(key)) calls.getLong(key) else null
        calls.put(key, now)
        // Keep the map small: only the last day matters.
        calls.keys().asSequence().toList().forEach { k -> if (now - calls.getLong(k) > 86_400_000) calls.remove(k) }
        prefs.edit().putString("recent_calls", calls.toString()).apply()
        return previous
    }

    // ---- The ring being handled right now ----
    // Android can report the same ring more than once. A ring lasts until the phone goes idle,
    // so a new call, however soon after, is always handled.

    /** Returns false if this ring from [key] is already being handled. */
    @Synchronized
    fun beginRing(key: String, now: Long): Boolean {
        val current = prefs.getString("ringing", null)?.split('@')
        // The 2-minute cap covers a missed idle broadcast (e.g. the app was killed mid-call).
        if (current != null && current[0] == key && now - (current.getOrNull(1)?.toLongOrNull() ?: 0) < 120_000) return false
        prefs.edit().putString("ringing", "$key@$now").commit()
        return true
    }

    /** The phone stopped ringing (call ended, declined or answered and hung up). */
    fun endRing() = prefs.edit().remove("ringing").apply()

    // ---- Backup ----

    fun exportJson(): String = JSONObject()
        .put("app", "auto-reply")
        .put("version", BACKUP_VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("modes", _modes.value.toJsonArray { it.toJson() })
        .put("schedules", _schedules.value.toJsonArray { it.toJson() })
        .put("rules", _rules.value.toJsonArray { it.toJson() })
        .put("settings", _settings.value.copy(sessionModeId = null, sessionStart = 0).toJson())
        .toString(2)

    /** Replaces modes, schedules, contact rules and settings. Throws if [json] isn't a backup. */
    fun importJson(json: String) {
        val o = JSONObject(json)
        require(o.optString("app") == "auto-reply") { "This isn't an Auto Reply backup." }
        saveModes(parseList(o.getJSONArray("modes").toString()) { it.toMode() })
        saveSchedules(parseList(o.optJSONArray("schedules")?.toString() ?: "[]") { it.toSchedule() })
        saveRules(parseList(o.optJSONArray("rules")?.toString() ?: "[]") { it.toContactRule() })
        val current = _settings.value
        updateSettings {
            o.getJSONObject("settings").toSettings()
                .copy(sessionModeId = current.sessionModeId, sessionStart = current.sessionStart)
        }
    }

    private fun <T> List<T>.upsert(item: T, same: (T) -> Boolean): List<T> =
        if (any(same)) map { if (same(it)) item else it } else this + item
}
