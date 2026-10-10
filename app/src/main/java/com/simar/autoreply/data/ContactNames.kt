package com.simar.autoreply.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/** Resolves phone numbers to the names saved in the phone's contacts. */
object ContactNames {

    fun canRead(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Names for [numbers] that match a contact; numbers without a contact are left out.
     * Does disk I/O, so call it off the main thread.
     */
    fun lookup(context: Context, numbers: Collection<String>): Map<String, String> {
        if (!canRead(context)) return emptyMap()
        return numbers.distinct().mapNotNull { number -> nameFor(context, number)?.let { number to it } }.toMap()
    }

    /** PhoneLookup matches regardless of formatting (+91, spaces, leading 0…). */
    fun nameFor(context: Context, number: String): String? {
        if (number.isBlank() || !canRead(context)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        } catch (e: Exception) {
            null
        }
    }
}
