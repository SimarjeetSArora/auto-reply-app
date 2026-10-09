package com.simar.autoreply.data

import java.util.UUID

object Defaults {
    fun modes() = listOf(
        Mode(newId(), "Driving", "Hi! I'm driving right now and can't take your call. I'll call you back once I've stopped.", "car", 0),
        Mode(newId(), "Sleeping", "Hey, I'm asleep right now. I'll get back to you in the morning.", "moon", 1),
        Mode(newId(), "In class", "I'm in class and can't talk. I'll call you back after.", "school", 5),
        Mode(newId(), "In a meeting", "I'm in a meeting at the moment. I'll get back to you soon.", "work", 6),
    )

    /** Icon key and colour for modes saved before those existed, guessed from the name. */
    fun lookFor(name: String): Pair<String, Int> {
        val n = name.lowercase()
        return when {
            "driv" in n || "car" in n -> "car" to 0
            "sleep" in n || "night" in n -> "moon" to 1
            "class" in n || "study" in n || "school" in n || "college" in n -> "school" to 5
            "meet" in n || "work" in n || "office" in n -> "work" to 6
            "gym" in n || "workout" in n -> "gym" to 4
            "busy" in n -> "hourglass" to 3
            else -> "chat" to (n.hashCode().mod(8))
        }
    }

    /** Modes saved by the emoji-based version of the app. */
    val iconForEmoji = mapOf(
        "🚗" to "car", "🌙" to "moon", "📚" to "school", "💼" to "work", "🏋️" to "gym", "🍽️" to "food",
        "🎬" to "movie", "✈️" to "flight", "🏥" to "hospital", "🙏" to "pray", "🎮" to "game", "⏳" to "hourglass",
        "🔕" to "silent", "💬" to "chat", "🎧" to "music", "⚽" to "sports",
    )

    fun newId(): String = UUID.randomUUID().toString()
}
