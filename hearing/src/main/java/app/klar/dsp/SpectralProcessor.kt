package app.klar.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Short-time Fourier processing with 50 % overlap (sqrt-Hann analysis and
 * synthesis windows, which reconstruct the input exactly when all gains are 1).
 *
 * Per frame it
 *  1. tracks the noise floor of every frequency bin (minimum follower),
 *  2. computes a Wiener noise-reduction gain with decision-directed SNR
 *     estimation (smooth, few "musical noise" artefacts),
 *  3. detects speech in the 300–3400 Hz band and quietens pauses between words
 *     when voice focus is on,
 *  4. applies the static voice-focus and equalizer curve, separately per ear.
 *
 * Latency is one hop (fftSize / 2 samples).
 */
class SpectralProcessor(val sampleRate: Int, val fftSize: Int) {
    val hop = fftSize / 2
    private val bins = fftSize / 2 + 1
    private val fft = Fft(fftSize)

    private val window = FloatArray(fftSize) {
        sqrt(0.5f - 0.5f * cos(2.0 * PI * it / fftSize).toFloat())
    }
    private val inBuf = FloatArray(fftSize)
    private val accLeft = FloatArray(fftSize)
    private val accRight = FloatArray(fftSize)
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val workRe = FloatArray(fftSize)
    private val workIm = FloatArray(fftSize)

    private val smoothPower = FloatArray(bins)
    private val noise = FloatArray(bins)
    private val prevGain = FloatArray(bins) { 1f }
    private val prevPost = FloatArray(bins) { 1f }
    private val nrGain = FloatArray(bins) { 1f }
    private val staticLeft = FloatArray(bins) { 1f }
    private val staticRight = FloatArray(bins) { 1f }

    private val framesPerSecond = sampleRate.toFloat() / hop
    private val noiseRise = 10f.pow(NOISE_RISE_DB_PER_SEC / 10f / framesPerSecond)
    private val speechLo = binOf(300f)
    private val speechHi = binOf(3400f)
    private val holdLength = (0.3f * framesPerSecond).roundToInt()
    private val initFrames = (0.25f * framesPerSecond).roundToInt()
    private var holdFrames = 0
    private var frames = 0

    private var noiseReduction = 0f
    private var voiceFocus = 0f

    /** 0..1, smoothed. */
    var speechProbability = 0f
        private set

    /** Average attenuation applied to the speech band in the last frame, in dB (>= 0). */
    var reductionDb = 0f
        private set

    fun binFrequency(k: Int) = k * sampleRate.toFloat() / fftSize

    private fun binOf(hz: Float) = (hz * fftSize / sampleRate).roundToInt().coerceIn(1, bins - 1)

    fun configure(
        noiseReduction: Float,
        voiceFocus: Float,
        eqLeftDb: List<Float>,
        eqRightDb: List<Float>,
        bands: List<Int>,
    ) {
        this.noiseReduction = noiseReduction.coerceIn(0f, 1f)
        this.voiceFocus = voiceFocus.coerceIn(0f, 1f)
        for (k in 0 until bins) {
            val f = binFrequency(k)
            val focusDb = VoiceFocus.weightDb(f, this.voiceFocus)
            staticLeft[k] = dbToLin(focusDb + Equalizer.gainDb(f, bands, eqLeftDb))
            staticRight[k] = dbToLin(focusDb + Equalizer.gainDb(f, bands, eqRightDb))
        }
    }

    /** Consumes [hop] input samples and produces [hop] samples per ear. */
    fun process(input: FloatArray, outLeft: FloatArray, outRight: FloatArray) {
        System.arraycopy(inBuf, hop, inBuf, 0, fftSize - hop)
        System.arraycopy(input, 0, inBuf, fftSize - hop, hop)
        for (i in 0 until fftSize) {
            re[i] = inBuf[i] * window[i]
            im[i] = 0f
        }
        fft.forward(re, im)
        updateGains()
        synthesize(staticLeft, accLeft, outLeft)
        synthesize(staticRight, accRight, outRight)
    }

    private fun updateGains() {
        frames++
        val strength = noiseReduction
        val overSubtraction = 1f + 1.5f * strength
        val floor = dbToLin(-MAX_REDUCTION_DB * strength)
        var bandPower = 0f
        var bandNoise = 0f

        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            if (frames <= initFrames) {
                // Start-up: the plain average of the first frames is the best noise guess.
                smoothPower[k] += (p - smoothPower[k]) / frames
                noise[k] = max(smoothPower[k] / NOISE_BIAS, NOISE_EPS)
            } else {
                val s = 0.9f * smoothPower[k] + 0.1f * p
                smoothPower[k] = s
                noise[k] = max(if (s < noise[k]) s else noise[k] * noiseRise, NOISE_EPS)
            }

            val n = noise[k] * NOISE_BIAS
            if (k in speechLo..speechHi) {
                bandPower += p
                bandNoise += n
            }

            val post = p / (n * overSubtraction)
            val prio = DD_ALPHA * prevGain[k] * prevGain[k] * prevPost[k] +
                (1f - DD_ALPHA) * max(post - 1f, 0f)
            val g = prio / (1f + prio)
            prevGain[k] = g
            prevPost[k] = min(post, 1000f)
            nrGain[k] = floor + (1f - floor) * g
        }

        // Voice activity in the speech band.
        val speechNow = frames > initFrames && bandPower > bandNoise * SPEECH_SNR_THRESHOLD
        if (speechNow) holdFrames = holdLength else if (holdFrames > 0) holdFrames--
        val target = if (speechNow || holdFrames > 0) 1f else 0f
        val rate = if (target > speechProbability) 0.35f else 0.06f
        speechProbability += (target - speechProbability) * rate

        // Quieten the gaps between words, more with stronger voice focus.
        val expansion = dbToLin(-EXPANSION_DB * voiceFocus * (1f - speechProbability))
        var sumDb = 0f
        for (k in speechLo..speechHi) sumDb += linToDb(nrGain[k])
        reductionDb = max(0f, -(sumDb / (speechHi - speechLo + 1)) - linToDb(expansion))
        for (k in 0 until bins) nrGain[k] *= expansion
    }

    private fun synthesize(staticGain: FloatArray, acc: FloatArray, out: FloatArray) {
        for (k in 0 until bins) {
            val g = staticGain[k] * nrGain[k]
            workRe[k] = re[k] * g
            workIm[k] = im[k] * g
        }
        for (k in 1 until fftSize / 2) {
            workRe[fftSize - k] = workRe[k]
            workIm[fftSize - k] = -workIm[k]
        }
        fft.inverse(workRe, workIm)
        for (i in 0 until fftSize) acc[i] += workRe[i] * window[i]
        System.arraycopy(acc, 0, out, 0, hop)
        System.arraycopy(acc, hop, acc, 0, fftSize - hop)
        acc.fill(0f, fftSize - hop, fftSize)
    }

    companion object {
        private const val NOISE_EPS = 1e-12f
        /** How fast the noise estimate may rise when the floor gets louder. */
        private const val NOISE_RISE_DB_PER_SEC = 4f
        /**
         * A minimum follower sits below the mean noise power (about 0.74 × the
         * mean with this smoothing, measured on white noise); this corrects for it.
         */
        private const val NOISE_BIAS = 1.35f
        private const val DD_ALPHA = 0.96f
        /** Attenuation floor at full noise-reduction strength. */
        const val MAX_REDUCTION_DB = 22f
        /** Pause attenuation at full voice focus. */
        const val EXPANSION_DB = 8f
        /** Speech band must be this many times (about 3 dB) above its noise floor. */
        private const val SPEECH_SNR_THRESHOLD = 2f
    }
}
