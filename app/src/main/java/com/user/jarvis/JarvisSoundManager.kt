package com.user.jarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object JarvisSoundManager {

    private fun playTone(startFreq: Double, endFreq: Double, durationMs: Int, volume: Float = 0.35f, modulate: Boolean = false) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate * durationMs / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples

                    // Frequency glide
                    var freq = startFreq + (endFreq - startFreq) * progress

                    if (modulate) {
                        freq += sin(2.0 * PI * 15.0 * t) * 50.0 // Add wobble effect
                    }

                    val envelope = when {
                        progress < 0.1 -> progress / 0.1 // Fast attack
                        progress > 0.7 -> (1.0 - progress) / 0.3 // Smooth release
                        else -> 1.0
                    }

                    // Create richer sound by mixing sine and square-ish harmonics
                    val baseSample = sin(2.0 * PI * freq * t)
                    val harmonicSample = sin(4.0 * PI * freq * t) * 0.3
                    var sample = (baseSample + harmonicSample) * envelope * volume

                    if (sample > 1.0) sample = 1.0
                    if (sample < -1.0) sample = -1.0

                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                Thread.sleep(durationMs.toLong() + 50)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) { }
        }.start()
    }

    // Deep futuristic startup tone
    fun playStartup() { playTone(150.0, 450.0, 800, 0.4f, modulate = true) }

    // Short recognizable wake
    fun playListening() { playTone(800.0, 1400.0, 150, 0.3f) }

    // Low scanning data sound
    fun playProcessing() { playTone(400.0, 380.0, 400, 0.25f, modulate = true) }

    // Slight speak initiation
    fun playSpeaking() { playTone(900.0, 900.0, 100, 0.15f) }

    // Clean confirmation tone
    fun playSuccess() { playTone(800.0, 1600.0, 300, 0.3f) }

    // Warning tone
    fun playError() { playTone(300.0, 150.0, 400, 0.4f) }

    // Subtle Notification
    fun playNotificationSound() { playTone(1200.0, 1200.0, 200, 0.2f) }

    // Shutdown
    fun playShutdownSound() { playTone(400.0, 100.0, 700, 0.4f) }

    // UI Click
    fun playClick() { playTone(1600.0, 1600.0, 40, 0.1f) }
}
