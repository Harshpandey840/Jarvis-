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
import java.util.concurrent.TimeUnit

object ElevenLabsManager {

    private const val API_KEY = "a578fb1343730eb8901efc64c0395cecba3faaee21987bfc7d8fd52c138eb62b"
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
        if (API_KEY == "YOUR_ELEVENLABS_API_KEY" || API_KEY.isBlank()) {
            Handler(Looper.getMainLooper()).post { fallback() }
            return
        }

        if (text.isBlank()) {
            Handler(Looper.getMainLooper()).post {
                onComplete?.invoke()
            }
            return
        }

        Thread {
            var tempFile: File? = null

            try {
                val jsonBody = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_multilingual_v2")
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.75)
                        put("style", 0.0)
                        put("use_speaker_boost", true)
                    })
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID")
                    .addHeader("xi-api-key", API_KEY)
                    .addHeader("Accept", "audio/mpeg")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    if (!response.isSuccessful) {
                        Handler(Looper.getMainLooper()).post {
                            fallback()
                        }
                        return@use
                    }

                    val audioBytes = response.body?.bytes()

                    if (audioBytes == null || audioBytes.isEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            fallback()
                        }
                        return@use
                    }

                    tempFile = File.createTempFile(
                        "jarvis_voice_",
                        ".mp3",
                        context.cacheDir
                    )

                    FileOutputStream(tempFile).use {
                        it.write(audioBytes)
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
                    fallback()
                }
            }
        }.start()
    }
}
