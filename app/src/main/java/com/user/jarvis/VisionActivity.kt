package com.user.jarvis

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
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

class VisionActivity : Activity(), TextToSpeech.OnInitListener {

    private val REQUEST_IMAGE_CAPTURE = 101
    private var tts: TextToSpeech? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                analyzeImage(imageBitmap)
            } else {
                speakAndFinish("Image nahi mil paaya")
            }
        } else {
            finish()
        }
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
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
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
        }
    }

    private fun speakAndFinish(text: String) {
        runOnUiThread {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "VisionResponse")
            }

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    runOnUiThread { finish() }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread { finish() }
                }
            })

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "VisionResponse")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
