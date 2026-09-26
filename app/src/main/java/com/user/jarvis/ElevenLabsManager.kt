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

object ElevenLabsManager {

    private const val VOICE_ID = "pNInz6obpgDQGcFmaJcg"

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

        // Step 1: Check Key First
        val prefs = context.getSharedPreferences("jarvis_keys", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("ELEVENLABS_API_KEY", null)

        if (apiKey.isNullOrEmpty() || apiKey == "YOUR_ELEVENLABS_API_KEY") {
            Handler(Looper.getMainLooper()).post { fallback() }
            return
        }

        Thread {
            var tempFile: File? = null

            // Step 4: Wrap in Try-Catch
            try {
                // Step 2: Strict ElevenLabs Network Call
                val jsonBody = JSONObject()
                jsonBody.put("text", text)
                jsonBody.put("model_id", "eleven_multilingual_v2")

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID")
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Accept", "audio/mpeg")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    // Step 3: Handle Response
                    if (!response.isSuccessful) {
                        val errorCode = response.code
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "ElevenLabs Error: $errorCode", Toast.LENGTH_LONG).show()
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
