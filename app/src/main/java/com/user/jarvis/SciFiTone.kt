package com.user.jarvis

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SciFiTone {

    fun play() {
        Thread {
            try {
                val sampleRate = 44100
                val durationMs = 260
                val numSamples = sampleRate * durationMs / 1000
                val buffer = ShortArray(numSamples)

                val startFreq = 650.0
                val endFreq = 1450.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val freq = startFreq + (endFreq - startFreq) * progress
                    val envelope = when {
                        progress < 0.1 -> progress / 0.1
                        progress > 0.8 -> (1.0 - progress) / 0.2
                        else -> 1.0
                    }
                    val sample = sin(2.0 * PI * freq * t) * envelope * 0.35
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
}
