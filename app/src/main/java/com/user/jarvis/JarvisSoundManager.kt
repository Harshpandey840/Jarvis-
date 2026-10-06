package com.user.jarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object JarvisSoundManager {

    private fun playTone(startFreq: Double, endFreq: Double, durationMs: Int, volume: Float = 0.35f) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate * durationMs / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val freq = startFreq + (endFreq - startFreq) * progress
                    val envelope = when {
                        progress < 0.1 -> progress / 0.1
                        progress > 0.8 -> (1.0 - progress) / 0.2
                        else -> 1.0
                    }
                    val sample = sin(2.0 * PI * freq * t) * envelope * volume
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

    fun playStartup() { playTone(300.0, 800.0, 600, 0.4f) }
    fun playListening() { playTone(600.0, 1200.0, 200, 0.3f) }
    fun playProcessing() { playTone(1200.0, 1000.0, 300, 0.25f) }
    fun playSpeaking() { playTone(800.0, 850.0, 150, 0.2f) }
    fun playSuccess() { playTone(800.0, 1400.0, 250, 0.3f) }
    fun playError() { playTone(400.0, 200.0, 350, 0.3f) }
    fun playClick() { playTone(1500.0, 1500.0, 50, 0.15f) }
}
