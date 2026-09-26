package com.user.jarvis

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ElevenLabsManager {
    private val httpClient = OkHttpClient()

    fun speak(context: Context, text: String, fallback: () -> Unit, onComplete: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences("jarvis_keys", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("ELEVENLABS_API_KEY", null)

        if (apiKey.isNullOrBlank()) {
            Handler(Looper.getMainLooper()).post { fallback() }
            return
        }

        Thread {
            try {
                val jsonBody = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_multilingual_v2")
                }

                val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/pNInz6obpgDQGcFmaJcg")
                    .addHeader("xi-api-key", apiKey)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Handler(Looper.getMainLooper()).post { fallback() }
                        return@use
                    }

                    val responseBytes = response.body?.bytes()
                    if (responseBytes == null) {
                        Handler(Looper.getMainLooper()).post { fallback() }
                        return@use
                    }

                    val tempFile = File.createTempFile("elevenlabs_tts", ".mp3", context.cacheDir)
                    FileOutputStream(tempFile).use { it.write(responseBytes) }

                    Handler(Looper.getMainLooper()).post {
                        try {
                            val mediaPlayer = MediaPlayer()
                            mediaPlayer.setDataSource(tempFile.absolutePath)
                            mediaPlayer.setOnCompletionListener {
                                it.release()
                                tempFile.delete()
                                onComplete?.invoke()
                            }
                            mediaPlayer.setOnErrorListener { mp, _, _ ->
                                mp.release()
                                tempFile.delete()
                                fallback()
                                true
                            }
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            tempFile.delete()
                            fallback()
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { fallback() }
            }
        }.start()
    }
}
