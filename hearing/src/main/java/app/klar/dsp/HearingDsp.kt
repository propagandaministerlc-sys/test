package app.klar.dsp

import app.klar.model.HearingSettings
import kotlin.math.abs
import kotlin.math.max

/**
 * The complete hearing-aid signal chain for one mono microphone signal:
 *
 * high-pass → spectral noise reduction, voice focus and per-ear EQ →
 * volume with wide-dynamic-range compression → balance → safety limiter.
 *
 * Call [configure] whenever settings change and [process] once per block of
 * [blockSize] samples. Not thread-safe: use it from the audio thread only.
 */
class HearingDsp(val sampleRate: Int) {
    val fftSize = if (sampleRate >= 32000) 512 else 256
    private val spectral = SpectralProcessor(sampleRate, fftSize)
    val blockSize = spectral.hop

    private val highPass = Biquad.highPass(sampleRate, 90.0)
    private val work = FloatArray(blockSize)
    private val left = FloatArray(blockSize)
    private val right = FloatArray(blockSize)

    private val compAttack = smoothingCoeff(0.005, sampleRate.toDouble())
    private val compRelease = smoothingCoeff(0.12, sampleRate.toDouble())
    private val volumeSmoothing = smoothingCoeff(0.05, sampleRate.toDouble())
    private val limiterRelease = smoothingCoeff(0.08, sampleRate.toDouble())

    private var compEnvelope = 1e-9f
    private var ratio = 2f
    private var volumeTargetDb = 0f
    private var volumeDb = 0f
    private var balanceLeft = 1f
    private var balanceRight = 1f
    private var ceiling = 0.5f
    private var limiterGain = 1f

    /** Input level of the last block in dBFS (RMS). */
    var inputDb = -120f
        private set

    /** Output level of the last block in dBFS (RMS). */
    var outputDb = -120f
        private set

    val speechProbability get() = spectral.speechProbability
    val reductionDb get() = spectral.reductionDb

    fun configure(s: HearingSettings) {
        spectral.configure(s.noiseReduction, s.voiceFocus, s.eqLeft, s.eqRight, HearingSettings.BANDS)
        volumeTargetDb = s.volumeDb.coerceIn(0f, HearingSettings.MAX_VOLUME_DB)
        ratio = 1f + 2f * s.compression.coerceIn(0f, 1f)
        val b = s.balance.coerceIn(-1f, 1f)
        balanceLeft = if (b > 0f) 1f - b else 1f
        balanceRight = if (b < 0f) 1f + b else 1f
        ceiling = dbToLin(s.maxOutputDb.coerceIn(HearingSettings.MIN_CEILING_DB, HearingSettings.MAX_CEILING_DB))
    }

    /** Gain reduction in dB for an input level, soft-knee downward compression. */
    internal fun compressionDb(levelDb: Float): Float {
        val over = levelDb - COMP_THRESHOLD_DB
        val slope = 1f - 1f / ratio
        return when {
            over <= -COMP_KNEE_DB / 2 -> 0f
            over >= COMP_KNEE_DB / 2 -> over * slope
            else -> {
                val x = over + COMP_KNEE_DB / 2
                slope * x * x / (2 * COMP_KNEE_DB)
            }
        }
    }

    /**
     * @param input [blockSize] mono samples in -1..1
     * @param outInterleaved 2 × [blockSize] stereo samples (L, R, L, R, ...)
     */
    fun process(input: FloatArray, outInterleaved: FloatArray) {
        var sumIn = 0.0
        for (i in 0 until blockSize) {
            val x = highPass.process(input[i])
            work[i] = x
            sumIn += x * x
        }
        inputDb = powerToDb((sumIn / blockSize).toFloat())

        spectral.process(work, left, right)

        var sumOut = 0.0
        for (i in 0 until blockSize) {
            var a = left[i]
            var b = right[i]

            val x2 = max(a * a, b * b)
            compEnvelope += (if (x2 > compEnvelope) compAttack else compRelease) * (x2 - compEnvelope)
            volumeDb += (volumeTargetDb - volumeDb) * volumeSmoothing
            val gain = dbToLin(volumeDb - compressionDb(powerToDb(compEnvelope)))
            a *= gain * balanceLeft
            b *= gain * balanceRight

            // Instant-attack peak limiter: the output never exceeds the ceiling.
            val peak = max(abs(a), abs(b))
            val needed = if (peak > ceiling) ceiling / peak else 1f
            limiterGain = if (needed < limiterGain) needed else limiterGain + (1f - limiterGain) * limiterRelease
            a = (a * limiterGain).coerceIn(-ceiling, ceiling)
            b = (b * limiterGain).coerceIn(-ceiling, ceiling)
            if (a.isNaN() || b.isNaN()) {
                a = 0f
                b = 0f
            }

            outInterleaved[2 * i] = a
            outInterleaved[2 * i + 1] = b
            sumOut += a * a + b * b
        }
        outputDb = powerToDb((sumOut / (2 * blockSize)).toFloat())
    }

    companion object {
        const val COMP_THRESHOLD_DB = -45f
        const val COMP_KNEE_DB = 10f
    }
}
