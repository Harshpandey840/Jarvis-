package com.user.jarvis

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
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
        val localCommand = VoiceCommandProcessor.parse(spokenText)
        if (localCommand !is Command.Unknown) {
            runCommand(localCommand)
        } else {
            AiCommandParser.parse(spokenText, BuildConfig.GEMINI_API_KEY) { aiCommand ->
                runCommand(aiCommand)
            }
        }
    }

    private fun runCommand(command: Command) {
        when (command) {
            is Command.OpenApp -> {
                val opened = appLauncher.openApp(command.appName)
                onSpeak(if (opened) "${command.appName} khol raha hoon" else "${command.appName} nahi mila")
            }
            is Command.SendMessage -> {
                if (!hasPermission(Manifest.permission.SEND_SMS) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai message bhejne ki"); return
                }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) { onSpeak("${command.contactName} contact nahi mila"); return }
                val sent = messageSender.sendSms(number, command.text)
                onSpeak(if (sent) "${command.contactName} ko message bhej diya" else "Message nahi bhej paya")
            }
            is Command.CallContact -> {
                if (!hasPermission(Manifest.permission.CALL_PHONE) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
                    onSpeak("Permission nahi hai call karne ki"); return
                }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) { onSpeak("${command.contactName} contact nahi mila"); return }
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                onSpeak("${command.contactName} ko call kar raha hoon")
            }
            is Command.SendWhatsApp -> {
                if (!hasPermission(Manifest.permission.READ_CONTACTS)) { onSpeak("Permission nahi hai contacts dekhne ki"); return }
                val number = contactResolver.findNumber(command.contactName)
                if (number == null) { onSpeak("${command.contactName} contact nahi mila"); return }
                val cleanNumber = normalizeForWhatsApp(number)
                val encodedText = URLEncoder.encode(command.text, "UTF-8").replace("+", "%20")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedText")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onSpeak("${command.contactName} ke WhatsApp mein message tayyar hai, Send dabana")
                } catch (e: Exception) {
                    onSpeak("WhatsApp nahi khul paya")
                }
            }
            is Command.GoogleSearch -> {
                val encoded = URLEncoder.encode(command.query, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onSpeak("${command.query} search kar raha hoon")
            }
            is Command.PlaySong -> {
                val encoded = URLEncoder.encode(command.query, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onSpeak("${command.query} bajane ja raha hoon")
            }
            is Command.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    if (command.hour in 0..23) {
                        putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, command.minute.coerceIn(0, 59))
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    onSpeak(if (command.hour in 0..23) "Alarm laga raha hoon" else "Alarm app khol raha hoon, time set kar lena")
                } catch (e: Exception) {
                    onSpeak("Alarm nahi laga paya")
                }
            }
            is Command.SetReminder -> {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, command.dayOffset)
                    if (command.hour in 0..23) {
                        set(Calendar.HOUR_OF_DAY, command.hour)
                        set(Calendar.MINUTE, command.minute.coerceIn(0, 59))
                        set(Calendar.SECOND, 0)
                    }
                }
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                scheduleReminder(calendar.timeInMillis, command.text, command.hour, command.minute, command.isRecurring)
                val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(calendar.time)
                onSpeak(
                    if (command.isRecurring) "Theek hai, har roz $timeStr ke time yaad dilaunga"
                    else "Theek hai, $timeStr ko yaad dila dunga"
                )
            }
            is Command.SaveNote -> {
                val existing = prefs.getStringSet("notes", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                existing.add(command.text)
                prefs.edit().putStringSet("notes", existing).apply()
                onSpeak("Note save kar diya")
            }
            is Command.AddTodo -> {
                val existing = prefs.getStringSet("todos", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                existing.add(command.text)
                prefs.edit().putStringSet("todos", existing).apply()
                onSpeak("Todo list mein add kar diya")
            }
            Command.ReadTodos -> {
                val todos = prefs.getStringSet("todos", setOf())?.toList() ?: listOf()
                onSpeak(if (todos.isEmpty()) "Todo list khaali hai" else "Tumhari todo list: " + todos.joinToString(". "))
            }
            Command.ShareNotes -> {
                val notes = prefs.getStringSet("notes", setOf())?.toList() ?: listOf()
                shareText("Mere Notes:\n" + notes.joinToString("\n"))
            }
            Command.ShareTodos -> {
                val todos = prefs.getStringSet("todos", setOf())?.toList() ?: listOf()
                shareText("Meri Todo List:\n" + todos.joinToString("\n"))
            }
            Command.ReadClipboard -> {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    onSpeak(if (text.isBlank()) "Clipboard khaali hai" else "Clipboard mein hai: $text")
                } else {
                    onSpeak("Clipboard khaali hai")
                }
            }
            is Command.WriteClipboard -> {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Jarvis", command.text))
                onSpeak("Clipboard mein copy kar diya")
            }
            is Command.CreateContact -> {
                try {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, command.name)
                        putExtra(ContactsContract.Intents.Insert.PHONE, command.phone)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onSpeak("${command.name} ka contact tayyar kar diya, Save dabana")
                } catch (e: Exception) {
                    onSpeak("Contact nahi bana paya")
                }
            }
            is Command.ChatReply -> onSpeak(command.text)
            Command.LockPhone -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, JarvisDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    onSpeak("Lock kar raha hoon")
                    dpm.lockNow()
                } else {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Jarvis ko phone lock karne ke liye ye permission chahiye")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    onSpeak("Pehle lock permission allow karo, ek baar hi dena hoga")
                }
            }
            Command.ReadNotes -> {
                val notes = prefs.getStringSet("notes", setOf())?.toList() ?: listOf()
                onSpeak(if (notes.isEmpty()) "Koi note nahi hai" else "Tumhare notes hain: " + notes.joinToString(". "))
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
            Command.FlashlightOn -> { setTorch(true); onSpeak("Torch jala diya") }
            Command.FlashlightOff -> { setTorch(false); onSpeak("Torch bandh kar diya") }
            Command.OpenWifiSettings -> {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                onSpeak("WiFi settings khol raha hoon")
            }
            Command.OpenBluetoothSettings -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                onSpeak("Bluetooth settings khol raha hoon")
            }
            Command.SilentModeOn -> setRingerMode(AudioManager.RINGER_MODE_SILENT, "Silent kar diya")
            Command.SilentModeOff -> setRingerMode(AudioManager.RINGER_MODE_NORMAL, "Sound on kar diya")
            is Command.Unknown -> onSpeak("Samajh nahi aaya")
        }
    }

    private fun scheduleReminder(triggerAtMillis: Long, text: String, hour: Int, minute: Int, isRecurring: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("is_recurring", isRecurring)
            putExtra("reminder_hour", hour)
            putExtra("reminder_minute", minute)
        }
        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share karo").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            onSpeak("Share karne ka option khol raha hoon")
        } catch (e: Exception) {
            onSpeak("Share nahi kar paya")
        }
    }

    private fun setRingerMode(mode: Int, successMessage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
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
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) { }
    }

    private fun normalizeForWhatsApp(rawNumber: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        return if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
