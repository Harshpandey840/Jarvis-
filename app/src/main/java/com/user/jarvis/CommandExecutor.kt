package com.user.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit
) {
    private val appLauncher = AppLauncher(context)
    private val contactResolver = ContactResolver(context)
    private val messageSender = MessageSender(context)

    fun execute(spokenText: String) {
        when (val command = VoiceCommandProcessor.parse(spokenText)) {
            is Command.OpenApp -> {
                val opened = appLauncher.openApp(command.appName)
                onSpeak(if (opened) "${command.appName} khol raha hoon" else "${command.appName} nahi mila")
            }
            is Command.SendMessage -> {
                if (!hasPermission(Manifest.permission.SEND_SMS) ||
                    !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai message bhejne ki")
                    return
                }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }
                val sent = messageSender.sendSms(number, command.text)
                onSpeak(if (sent) "${command.contactName} ko message bhej diya" else "Message nahi bhej paya")
            }
            is Command.CallContact -> {
                if (!hasPermission(Manifest.permission.CALL_PHONE) ||
                    !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai call karne ki")
                    return
                }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                onSpeak("${command.contactName} ko call kar raha hoon")
            }
            is Command.Unknown -> {
                onSpeak("Samajh nahi aaya")
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
