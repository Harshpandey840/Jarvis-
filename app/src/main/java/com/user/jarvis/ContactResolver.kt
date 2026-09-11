package com.user.jarvis

import android.content.Context
import android.provider.ContactsContract

class ContactResolver(private val context: Context) {

    fun findNumber(spokenName: String): String? {
        val target = spokenName.trim().lowercase()
        val resolver = context.contentResolver

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var bestNumber: String? = null
        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                if (name.lowercase().contains(target) || target.contains(name.lowercase())) {
                    bestNumber = it.getString(numberIdx)
                    if (name.lowercase() == target) return bestNumber
                }
            }
        }
        return bestNumber
    }
}
