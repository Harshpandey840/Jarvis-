package com.user.jarvis

import android.content.Context
import android.telephony.SmsManager

class MessageSender(private val context: Context) {

    fun sendSms(number: String, message: String): Boolean {
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
