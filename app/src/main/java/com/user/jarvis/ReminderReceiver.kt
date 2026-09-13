package com.user.jarvis

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("reminder_text") ?: "Reminder"
        val isRecurring = intent.getBooleanExtra("is_recurring", false)
        val hour = intent.getIntExtra("reminder_hour", -1)
        val minute = intent.getIntExtra("reminder_minute", -1)

        val channelId = "jarvis_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Jarvis Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Jarvis yaad dila raha hai")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)

        if (isRecurring && hour in 0..23) {
            val nextCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_text", text)
                putExtra("is_recurring", true)
                putExtra("reminder_hour", hour)
                putExtra("reminder_minute", minute)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, System.currentTimeMillis().toInt(), nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextCalendar.timeInMillis, pendingIntent)
        }
    }
}
