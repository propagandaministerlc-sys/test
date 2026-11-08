package app.klar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import app.klar.model.Ear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Plays the hearing-test tones: short sine beeps on one ear at an exact level. */
class TonePlayer : AutoCloseable {
    private val sampleRate = 48000
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
        .also { it.play() }

    /**
     * Plays one beep followed by silence and returns when it has been queued.
     * @return the uptime at which the beep started to be written
     */
    suspend fun beep(hz: Int, ear: Ear, levelDb: Float, toneMs: Int = 450, gapMs: Int = 450): Long =
        withContext(Dispatchers.IO) {
            val toneFrames = sampleRate * toneMs / 1000
            val totalFrames = toneFrames + sampleRate * gapMs / 1000
            val buffer = FloatArray(totalFrames * 2)
            val amplitude = 10f.pow(levelDb / 20f)
            val ramp = sampleRate * 0.03f
            val channel = if (ear == Ear.LEFT) 0 else 1
            for (i in 0 until toneFrames) {
                val envelope = min(1f, min(i / ramp, (toneFrames - 1 - i) / ramp))
                buffer[2 * i + channel] = amplitude * envelope * sin(2.0 * PI * hz * i / sampleRate).toFloat()
            }
            val started = SystemClock.uptimeMillis()
            var offset = 0
            val chunk = 2 * sampleRate / 20
            while (offset < buffer.size) {
                coroutineContext.ensureActive()
                val n = track.write(buffer, offset, min(chunk, buffer.size - offset), AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                offset += n
            }
            started
        }

    override fun close() {
        runCatching { track.pause(); track.flush() }
        track.release()
    }
}
