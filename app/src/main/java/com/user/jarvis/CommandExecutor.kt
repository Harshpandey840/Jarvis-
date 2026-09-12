package com.user.jarvis

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandExecutor(
    private val context: Context,
    private val onSpeak: (String) -> Unit
) {
    private val appLauncher = AppLauncher(context)
    private val contactResolver = ContactResolver(context)
    private val messageSender = MessageSender(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)

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
            is Command.SendWhatsApp -> {
                if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai contacts dekhne ki")
                    return
                }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) {
                    onSpeak("${command.contactName} contact nahi mila")
                    return
                }
                val cleanNumber = normalizeForWhatsApp(number)
                val encodedText = URLEncoder.encode(command.text, "UTF-8")
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$cleanNumber?text=$encodedText")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    onSpeak("${command.contactName} ke WhatsApp mein message tayyar hai, send dabana")
                } catch (e: Exception) {
                    onSpeak("WhatsApp nahi khul paya")
                }
            }
            is Command.GoogleSearch -> {
                val encoded = URLEncoder.encode(command.query, "UTF-8")
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=$encoded")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                onSpeak("${command.query} search kar raha hoon")
            }
            is Command.PlaySong -> {
                val encoded = URLEncoder.encode(command.query, "UTF-8")
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                onSpeak("${command.query} bajane ja raha hoon")
            }
            is Command.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    if (command.hour in 0..23) {
                        putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    onSpeak(
                        if (command.hour in 0..23) "Alarm laga raha hoon"
                        else "Alarm app khol raha hoon, time set kar lena"
                    )
                } catch (e: Exception) {
                    onSpeak("Alarm nahi laga paya")
                }
            }
            is Command.SaveNote -> {
                val existing = prefs.getStringSet("notes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                existing.add(command.text)
                prefs.edit().putStringSet("notes", existing).apply()
                onSpeak("Note save kar diya")
            }
            Command.ReadNotes -> {
                val notes = prefs.getStringSet("notes", setOf())?.toList() ?: listOf()
                if (notes.isEmpty()) {
                    onSpeak("Koi note nahi hai")
                } else {
                    onSpeak("Tumhare notes hain: " + notes.joinToString(". "))
                }
            }
            Command.TellTime -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                onSpeak("Abhi time hai $time")
            }
            Command.TellBattery -> {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                onSpeak("Battery $level percent hai")
            }
            Command.VolumeUp -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                onSpeak("Volume badha diya")
            }
            Command.VolumeDown -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                onSpeak("Volume kam kar diya")
            }
            Command.FlashlightOn -> {
                setTorch(true)
                onSpeak("Torch jala diya")
            }
            Command.FlashlightOff -> {
                setTorch(false)
                onSpeak("Torch bandh kar diya")
            }
            Command.OpenWifiSettings -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onSpeak("WiFi settings khol raha hoon")
            }
            Command.OpenBluetoothSettings -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onSpeak("Bluetooth settings khol raha hoon")
            }
            Command.SilentModeOn -> {
                setRingerMode(AudioManager.RINGER_MODE_SILENT, "Silent kar diya")
            }
            Command.SilentModeOff -> {
                setRingerMode(AudioManager.RINGER_MODE_NORMAL, "Sound on kar diya")
            }
            is Command.Unknown -> {
                onSpeak("Samajh nahi aaya")
            }
        }
    }

    private fun setRingerMode(mode: Int, successMessage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onSpeak("Pehle Jarvis ko 'Do Not Disturb' permission do")
            return
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = mode
        onSpeak(successMessage)
    }

    private fun setTorch(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
            }
        } catch (e: Exception) {
            // some devices don't support this cleanly — ignore
        }
    }

    private fun normalizeForWhatsApp(rawNumber: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        return if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
