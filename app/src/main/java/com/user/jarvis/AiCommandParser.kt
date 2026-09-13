package com.user.jarvis

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiCommandParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private const val MODEL = "gemini-2.5-flash"

    private const val SYSTEM_PROMPT = "You are Jarvis, a warm and helpful personal Android voice assistant. The user speaks Hindi/English mixed (Hinglish). First decide: is this a DEVICE COMMAND (open app, call, message, alarm, etc) or just GENERAL TALK (a question, greeting, or chit-chat)? Respond with ONE JSON object ONLY, no markdown fences, no explanation outside the JSON. Format: {\"action\": \"one of open_app, call, message, whatsapp, search, play_song, set_alarm, save_note, read_notes, tell_time, tell_battery, volume_up, volume_down, flashlight_on, flashlight_off, wifi_settings, bluetooth_settings, silent_on, silent_off, lock_phone, chat\", \"target\": \"app name or contact name or empty\", \"text\": \"message text, search query, or your natural short spoken reply if action is chat\", \"hour\": alarm hour 0-23 or -1, \"minute\": alarm minute 0-59 or -1}. If it is general talk, a question, or anything that is not clearly one of the device actions, use action \"chat\" and put a short, warm, natural Hinglish reply (like a helpful friend would say, 1-3 sentences, no markdown) in the text field — actually answer their question or respond to what they said. Output only the JSON object."

    fun parse(spokenText: String, apiKey: String, onResult: (Command) -> Unit) {
        if (apiKey.isBlank()) {
            onResult(Command.ChatReply("AI abhi setup nahi hai, API key check karo"))
            return
        }
        Thread {
            try {
                val prompt = "$SYSTEM_PROMPT\n\nUser said: \"$spokenText\""
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
                mainHandler.post { onResult(Command.ChatReply("Network mein dikkat aa rahi hai")) }
            }
        }.start()
    }

    private fun mapToCommand(json: JSONObject, fallbackRaw: String): Command {
        val action = json.optString("action", "chat")
        val target = json.optString("target", "")
        val text = json.optString("text", "")
        val hour = json.optInt("hour", -1)
        val minute = json.optInt("minute", -1)

        return when (action) {
            "open_app" -> Command.OpenApp(target)
            "call" -> Command.CallContact(target)
            "message" -> Command.SendMessage(target, text)
            "whatsapp" -> Command.SendWhatsApp(target, text)
            "search" -> Command.GoogleSearch(text.ifBlank { target })
            "play_song" -> Command.PlaySong(text.ifBlank { target })
            "set_alarm" -> Command.SetAlarm(hour, minute)
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
            "chat" -> Command.ChatReply(text.ifBlank { "Haan bolo" })
            else -> Command.ChatReply(text.ifBlank { "Samajh nahi paya" })
        }
    }
}
