package app.klar.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

internal fun dbToLin(db: Float): Float = 10f.pow(db / 20f)
internal fun powerToDb(p: Float): Float = 10f * log10(max(p, 1e-12f))
internal fun linToDb(x: Float): Float = 20f * log10(max(x, 1e-6f))

/** One-pole smoothing coefficient for a time constant in seconds. */
internal fun smoothingCoeff(seconds: Double, rate: Double): Float =
    (1.0 - exp(-1.0 / (seconds * rate))).toFloat()

/** RBJ biquad in transposed direct form II. */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y.toFloat()
    }

    companion object {
        fun highPass(sampleRate: Int, cutoffHz: Double, q: Double = 0.7071): Biquad {
            val w0 = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val cosW = cos(w0)
            val a0 = 1 + alpha
            return Biquad(
                b0 = (1 + cosW) / 2 / a0,
                b1 = -(1 + cosW) / a0,
                b2 = (1 + cosW) / 2 / a0,
                a1 = -2 * cosW / a0,
                a2 = (1 - alpha) / a0,
            )
        }
    }
}

/** Frequency weighting that brings speech forward. */
object VoiceFocus {
    /** Gain in dB at [hz] for a focus amount 0..1. */
    fun weightDb(hz: Float, focus: Float): Float {
        if (focus <= 0f) return 0f
        // Cut rumble and hum below the voice range.
        val low = when {
            hz < 150f -> -18f
            hz < 300f -> -18f * (300f - hz) / 150f
            else -> 0f
        }
        // Presence boost where consonants live (they carry most of speech intelligibility).
        val presence = when {
            hz < 800f -> 0f
            hz < 1200f -> 5f * (hz - 800f) / 400f
            hz < 4000f -> 5f
            hz < 5500f -> 5f * (5500f - hz) / 1500f
            else -> 0f
        }
        // Soften hiss and clatter above the speech range.
        val high = when {
            hz < 6000f -> 0f
            hz < 8000f -> -10f * (hz - 6000f) / 2000f
            else -> -10f
        }
        return (low + presence + high) * focus
    }
}

object Equalizer {
    /** Gain in dB at [hz], interpolated linearly over log-frequency between band centres. */
    fun gainDb(hz: Float, bands: List<Int>, gains: List<Float>): Float {
        if (hz <= bands.first()) return gains.first()
        if (hz >= bands.last()) return gains.last()
        val lf = log2(hz)
        for (i in 0 until bands.size - 1) {
            val a = log2(bands[i].toFloat())
            val b = log2(bands[i + 1].toFloat())
            if (lf <= b) {
                val t = (lf - a) / (b - a)
                return gains[i] + t * (gains[i + 1] - gains[i])
            }
        }
        return gains.last()
    }
}
