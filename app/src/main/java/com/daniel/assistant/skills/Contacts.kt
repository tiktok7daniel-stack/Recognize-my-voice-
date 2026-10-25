package com.daniel.assistant.skills

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.daniel.assistant.voice.FuzzyMatcher
import com.daniel.assistant.voice.Transliterator

/** Finds a contact by spoken name (Arabic or Latin names, fuzzy). */
class Contacts(private val context: Context) {

    data class Contact(val name: String, val number: String)

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun find(spoken: String): Contact? {
        if (!hasPermission()) return null
        val all = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                all.add(Contact(name, number))
            }
        }
        var best: Contact? = null
        var bestScore = 0.68
        for (ct in all) {
            val first = ct.name.split(' ').firstOrNull().orEmpty()
            val score = maxOf(
                FuzzyMatcher.score(spoken, ct.name),
                FuzzyMatcher.score(spoken, first),
                FuzzyMatcher.score(spoken, Transliterator.toArabic(ct.name)),
                FuzzyMatcher.score(spoken, Transliterator.toArabic(first)),
            )
            if (score > bestScore) { bestScore = score; best = ct }
        }
        return best
    }

    /** International digits without "+" (for wa.me links), guessing the country from the SIM. */
    fun international(number: String): String {
        var n = number.filter { it.isDigit() || it == '+' }
        if (n.startsWith("+")) return n.drop(1)
        if (n.startsWith("00")) return n.drop(2)
        val iso = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso?.lowercase()
        val code = mapOf(
            "eg" to "20", "it" to "39", "sa" to "966", "ae" to "971", "kw" to "965", "qa" to "974", "jo" to "962",
            "us" to "1", "gb" to "44", "de" to "49", "fr" to "33", "es" to "34", "tr" to "90",
        )[iso] ?: "20"
        if (n.startsWith("0")) n = n.drop(1)
        return code + n
    }
}
