package com.user.jarvis

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

class VisionActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            analyzeImage(bitmap)
        } else {
            speakAndFinish("Image nahi mil paaya")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        takePicturePreview.launch(null)
    }

    private fun analyzeImage(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()
        val base64Image = Base64.getEncoder().encodeToString(byteArray)

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            speakAndFinish("API key nahi hai")
            return
        }

        Thread {
            try {
                val prompt = "Briefly describe this image"

                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }

                val partImage = JSONObject().apply { put("inlineData", inlineData) }
                val partText = JSONObject().apply { put("text", prompt) }

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().put(partText).put(partImage))
                        }
                    ))
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash-001:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    val root = JSONObject(responseText)

                    if (root.has("error")) {
                        speakAndFinish("Analysis error")
                        return@use
                    }

                    val rawText = root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    speakAndFinish(rawText.trim())
                }
            } catch (e: Exception) {
                speakAndFinish("Kuch gadbad ho gayi analysis mein")
            }
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            VoicePreferences.applySavedVoice(this, tts!!)
            tts?.setPitch(0.78f)
            tts?.setSpeechRate(0.98f)
        }
    }

    private fun speakAndFinish(text: String) {
        runOnUiThread {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VisionResponse")
            finish()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
