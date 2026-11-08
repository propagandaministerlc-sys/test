package app.klar.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import app.klar.HearingRepository
import app.klar.Meters
import app.klar.dsp.HearingDsp
import app.klar.model.HearingSettings
import app.klar.model.MicSource
import kotlin.math.max

/**
 * Microphone → [HearingDsp] → headphones, on a dedicated real-time thread.
 *
 * Two microphone modes:
 *  - PHONE: the phone's built-in microphone, played back over Bluetooth
 *    media audio (A2DP). Best sound; the phone works like a remote microphone.
 *  - HEADSET: the AirPods' microphones via the Bluetooth hands-free profile.
 *    Narrow-band "telephone" sound but a bit less delay.
 */
class AudioEngine(
    private val context: Context,
    private val onFailure: (String) -> Unit,
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::run, "KlarAudio").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
    }

    @SuppressLint("MissingPermission") // Checked by the UI before the service starts.
    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val am = context.getSystemService(AudioManager::class.java)
        val initial = HearingRepository.settings.value
        val headsetMic = initial.micSource == MicSource.HEADSET

        var record: AudioRecord? = null
        var track: AudioTrack? = null
        var suppressor: NoiseSuppressor? = null
        try {
            if (headsetMic) {
                if (!AudioDevices.startHeadsetMic(context, am)) {
                    throw EngineException(
                        "Das Mikrofon der AirPods ist nicht verfügbar. Wähle das Telefon-Mikrofon oder verbinde die AirPods neu.",
                    )
                }
            } else {
                am.mode = AudioManager.MODE_NORMAL
            }

            val sampleRate = if (headsetMic) {
                16000
            } else {
                am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
                    ?.takeIf { it in 16000..96000 } ?: 48000
            }
            val dsp = HearingDsp(sampleRate)
            val block = dsp.blockSize

            val source = if (headsetMic) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                // Least processed source that every device supports: no AGC, flat response.
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            val minRecord = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minRecord <= 0) throw EngineException("Das Mikrofon unterstützt dieses Format nicht.")
            record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minRecord, block * 2 * 4))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw EngineException("Das Mikrofon konnte nicht geöffnet werden. Nutzt eine andere App es gerade?")
            }
            if (!headsetMic) AudioDevices.builtInMic(am)?.let { record.setPreferredDevice(it) }

            if (NoiseSuppressor.isAvailable()) {
                suppressor = runCatching { NoiseSuppressor.create(record.audioSessionId) }.getOrNull()
            }

            val minTrack = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (headsetMic) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA,
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minTrack, block * 2 * 4 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                throw EngineException("Die Wiedergabe konnte nicht gestartet werden.")
            }

            val pcm = ShortArray(block)
            val input = FloatArray(block)
            val output = FloatArray(block * 2)
            var applied: HearingSettings? = null
            var suppressorOn: Boolean? = null
            val meterEvery = max(1, sampleRate / block / 15) // about 15 updates per second
            var meterCount = 0

            record.startRecording()
            track.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING) // one block of silence as cushion
            track.play()

            while (running) {
                var read = 0
                while (read < block && running) {
                    val n = record.read(pcm, read, block - read)
                    if (n < 0) throw EngineException("Das Mikrofon hat die Verbindung verloren (Fehler $n).")
                    read += n
                }
                if (!running) break
                for (i in 0 until block) input[i] = pcm[i] / 32768f

                val settings = HearingRepository.settings.value
                if (settings !== applied) {
                    dsp.configure(settings)
                    applied = settings
                }
                if (suppressor != null && suppressorOn != settings.systemNoiseSuppressor) {
                    runCatching { suppressor?.setEnabled(settings.systemNoiseSuppressor) }
                    suppressorOn = settings.systemNoiseSuppressor
                }

                dsp.process(input, output)
                track.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)

                if (++meterCount >= meterEvery) {
                    meterCount = 0
                    HearingRepository.publishMeters(
                        Meters(dsp.inputDb, dsp.outputDb, dsp.reductionDb, dsp.speechProbability),
                    )
                }
            }
        } catch (e: EngineException) {
            running = false
            onFailure(e.message ?: "Unbekannter Fehler")
        } catch (e: Exception) {
            running = false
            onFailure("Audiofehler: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { track?.pause(); track?.flush() }
            runCatching { track?.release() }
            runCatching { record?.stop() }
            runCatching { suppressor?.release() }
            runCatching { record?.release() }
            if (headsetMic) runCatching { AudioDevices.stopHeadsetMic(am) }
        }
    }

    private class EngineException(message: String) : Exception(message)
}
