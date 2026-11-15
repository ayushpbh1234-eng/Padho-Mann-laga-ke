package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AmbientSoundPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private var currentSound: String? = null
    private var isMuted: Boolean = false

    fun start(sound: String, muted: Boolean) {
        isMuted = muted
        if (muted || sound.startsWith("Silence")) {
            stop()
            return
        }
        if (currentSound == sound && playJob?.isActive == true) {
            return
        }
        stop()
        currentSound = sound
        playJob = scope.launch {
            runTrack(sound)
        }
    }

    fun setMuted(muted: Boolean, activeSound: String, isRunning: Boolean) {
        isMuted = muted
        if (muted || !isRunning || activeSound.startsWith("Silence")) {
            stop()
        } else {
            start(activeSound, false)
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        currentSound = null
    }

    private fun CoroutineScope.runTrack(sound: String) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            return
        }

        try {
            track.play()
            val buffer = ShortArray(1024)
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var phase1 = 0.0
            var phase2 = 0.0
            var phase3 = 0.0
            var sampleCounter = 0L
            val random = Random(42)

            val chords = listOf(
                Triple(220.0, 261.63, 329.63),
                Triple(196.0, 246.94, 293.66),
                Triple(174.61, 220.0, 261.63),
                Triple(164.81, 196.0, 246.94)
            )

            while (isActive) {
                for (i in buffer.indices) {
                    val sample = when {
                        sound.startsWith("Barish") -> {
                            val white = random.nextDouble(-1.0, 1.0)
                            b0 = 0.997 * b0 + white * 0.029
                            b1 = 0.985 * b1 + white * 0.032
                            b2 = 0.950 * b2 + white * 0.048
                            val sway = 0.75 + 0.25 * sin(2.0 * PI * sampleCounter / (sampleRate * 6.0))
                            (b0 + b1 + b2) * 0.22 * sway
                        }
                        sound.startsWith("Cafe") -> {
                            val white = random.nextDouble(-1.0, 1.0)
                            b0 = 0.992 * b0 + white * 0.018
                            val hum = sin(phase1) * 0.04
                            phase1 += 2.0 * PI * 110.0 / sampleRate
                            if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                            b0 * 0.30 + hum
                        }
                        else -> {
                            val chordIdx = ((sampleCounter / (sampleRate * 4)) % chords.size).toInt()
                            val (f1, f2, f3) = chords[chordIdx]
                            phase1 += 2.0 * PI * f1 / sampleRate
                            phase2 += 2.0 * PI * f2 / sampleRate
                            phase3 += 2.0 * PI * f3 / sampleRate
                            if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                            if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                            if (phase3 > 2.0 * PI) phase3 -= 2.0 * PI
                            (sin(phase1) + sin(phase2) * 0.7 + sin(phase3) * 0.5) * 0.08
                        }
                    }
                    sampleCounter++
                    buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Throwable) {
            // Safely ignore audio teardown errors
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }
}
