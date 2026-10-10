package com.simar.autoreply.logic

import android.content.Context

/** Fills the placeholders a mode's message can contain. */
object Messages {
    const val NAME = "{name}"
    const val UNTIL = "{until}"
    const val VOICE_NOTE_LINE = "If it's important, leave me a WhatsApp voice note."

    /**
     * {name} → the caller's first name from contacts ("there" if unknown).
     * {until} → when the current mode ends ("later" if it has no end).
     */
    fun render(context: Context, template: String, callerName: String?, until: Long?): String {
        val first = callerName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
        return template
            .replace(NAME, first ?: "there")
            .replace(UNTIL, until?.let { ModeState.formatUntil(context, it) } ?: "later")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }
}
