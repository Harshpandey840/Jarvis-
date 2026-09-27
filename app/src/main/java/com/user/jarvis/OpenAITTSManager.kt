package com.user.jarvis

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object OpenAITTSManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun speak(
        context: Context,
        text: String,
        fallback: () -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            Handler(Looper.getMainLooper()).post {
                onComplete?.invoke()
            }
            return
        }

        val prefs = context.getSharedPreferences("jarvis_keys", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("OPENAI_TTS_API_KEY", null)

        if (apiKey.isNullOrEmpty()) {
            Handler(Looper.getMainLooper()).post { fallback() }
            return
        }

        Thread {
            var tempFile: File? = null

            try {
                val safeText = if (text.trim().isNotEmpty()) text else "System alert."
                val jsonBody = JSONObject()
                jsonBody.put("model", "tts-1")
                jsonBody.put("voice", "onyx")
                jsonBody.put("input", safeText)

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://open-ai-text-to-speech1.p.rapidapi.com/")
                    .addHeader("x-rapidapi-host", "open-ai-text-to-speech1.p.rapidapi.com")
                    .addHeader("x-rapidapi-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error body"
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "OpenAI TTS Error: $errorBody", Toast.LENGTH_LONG).show()
                            fallback()
                        }
                        return@use
                    }

                    val inputStream = response.body?.byteStream()
                    if (inputStream == null) {
                        Handler(Looper.getMainLooper()).post { fallback() }
                        return@use
                    }

                    tempFile = File.createTempFile("jarvis_voice_", ".mp3", context.cacheDir)
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    Handler(Looper.getMainLooper()).post {
                        try {
                            val mediaPlayer = MediaPlayer()
                            mediaPlayer.setDataSource(tempFile!!.absolutePath)
                            mediaPlayer.setOnCompletionListener { player ->
                                player.release()
                                tempFile?.delete()
                                onComplete?.invoke()
                            }
                            mediaPlayer.setOnErrorListener { player, _, _ ->
                                player.release()
                                tempFile?.delete()
                                fallback()
                                true
                            }
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            tempFile?.delete()
                            fallback()
                        }
                    }
                }
            } catch (e: Exception) {
                tempFile?.delete()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "TTS Network Exception", Toast.LENGTH_LONG).show()
                    fallback()
                }
            }
        }.start()
    }
}
