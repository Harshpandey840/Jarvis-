package com.user.jarvis

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AiCommandParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private const val MODEL = "gemini-2.5-flash"
    private val history = mutableListOf<Pair<String, String>>()

    private const val SYSTEM_PROMPT = "You are Jarvis, a warm and helpful personal Android voice assistant. The user speaks Hindi/English mixed (Hinglish). You are given the current date and time and recent conversation history for context. First decide: is this a DEVICE COMMAND or GENERAL TALK (question, greeting, chit-chat, weather, news, translation, calculation, follow-up question)? Respond with ONE JSON object ONLY, no markdown fences, no explanation outside the JSON. Format: {\"action\": \"one of open_app, call, message, whatsapp, search, play_song, set_alarm, set_reminder, add_todo, read_todos, save_note, read_notes, read_clipboard, write_clipboard, tell_time, tell_battery, volume_up, volume_down, flashlight_on, flashlight_off, wifi_settings, bluetooth_settings, silent_on, silent_off, lock_phone, chat\", \"target\": \"app name or contact name or empty\", \"text\": \"message text, search query, todo item, clipboard text, or your natural short spoken reply if action is chat\", \"hour\": hour 0-23 or -1, \"minute\": minute 0-59 or -1, \"day_offset\": 0 for today, 1 for tomorrow, 2 for day after, or -1 if not time related}. Use set_reminder when the user asks to be reminded of something at a specific time (e.g. \"kal 8 baje yaad dilana\") — put the reminder content in text, and compute correct hour/minute/day_offset from the current date and time given to you. For weather, news, translation, calculation, or any factual question, use action chat and give your best real specific answer in text (1-3 short spoken Hinglish sentences) — actually answer, do not refuse. If truly unsure, say so briefly. Output only the JSON object."

    fun parse(spokenText: String, apiKey: String, onResult: (Command) -> Unit) {
        if (apiKey.isBlank()) {
            onResult(Command.ChatReply("API key khaali hai, secret check karo"))
            return
        }
        Thread {
            try {
                val nowStr = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                val historyText = if (history.isNotEmpty()) {
                    "Recent conversation:\n" + history.joinToString("\n") { "User: ${it.first}\nJarvis: ${it.second}" } + "\n\n"
                } else ""
                val prompt = "$SYSTEM_PROMPT\n\nCurrent date and time: $nowStr.\n\n$historyText" +
                    "User said: \"$spokenText\""

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
                        }
                    ))
                }
                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body)
                    .build()

                    client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    val root = JSONObject(responseText)

                    if (root.has("error")) {
                        val message = root.getJSONObject("error").optString("message", "Unknown error")
                        mainHandler.post { onResult(Command.ChatReply("API error: $message")) }
                        return@use
                    }
                    if (!root.has("candidates")) {
                        val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason", "no reason")
                        mainHandler.post { onResult(Command.ChatReply("AI ne jawab nahi diya: ${blockReason ?: "unknown"}")) }
                        return@use
                    }

                    val rawText = root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    val cleaned = rawText.trim()
                        .removePrefix("```json").removePrefix("```")
                        .removeSuffix("```").trim()
                    val parsed = JSONObject(cleaned)
                    val command = mapToCommand(parsed, spokenText)
                    mainHandler.post { onResult(command) }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onResult(Command.ChatReply("Error: ${e.javaClass.simpleName} — ${e.message}"))
                }
            }
        }.start()
    }

    private fun pushHistory(userText: String, replyText: String) {
        history.add(userText to replyText)
        while (history.size > 6) history.removeAt(0)
    }

    private fun mapToCommand(json: JSONObject, fallbackRaw: String): Command {
        val action = json.optString("action", "chat")
        val target = json.optString("target", "")
        val text = json.optString("text", "")
        val hour = json.optInt("hour", -1)
        val minute = json.optInt("minute", -1)
        val dayOffset = json.optInt("day_offset", 0)

        return when (action) {
            "open_app" -> Command.OpenApp(target)
            "call" -> Command.CallContact(target)
            "message" -> Command.SendMessage(target, text)
            "whatsapp" -> Command.SendWhatsApp(target, text)
            "search" -> Command.GoogleSearch(text.ifBlank { target })
            "play_song" -> Command.PlaySong(text.ifBlank { target })
            "set_alarm" -> Command.SetAlarm(hour, minute)
            "set_reminder" -> Command.SetReminder(dayOffset.coerceAtLeast(0), hour, minute, text.ifBlank { target })
            "add_todo" -> Command.AddTodo(text.ifBlank { target })
            "read_todos" -> Command.ReadTodos
            "read_clipboard" -> Command.ReadClipboard
            "write_clipboard" -> Command.WriteClipboard(text.ifBlank { target })
            "save_note" -> Command.SaveNote(text.ifBlank { target })
            "read_notes" -> Command.ReadNotes
            "tell_time" -> Command.TellTime
            "tell_battery" -> Command.TellBattery
            "volume_up" -> Command.VolumeUp
            "volume_down" -> Command.VolumeDown
            "flashlight_on" -> Command.FlashlightOn
            "flashlight_off" -> Command.FlashlightOff
            "wifi_settings" -> Command.OpenWifiSettings
            "bluetooth_settings" -> Command.OpenBluetoothSettings
            "silent_on" -> Command.SilentModeOn
            "silent_off" -> Command.SilentModeOff
            "lock_phone" -> Command.LockPhone
            "chat" -> {
                val reply = text.ifBlank { "Haan bolo" }
                pushHistory(fallbackRaw, reply)
                Command.ChatReply(reply)
            }
            else -> Command.ChatReply(text.ifBlank { "Samajh nahi paya" })
        }
    }
}
