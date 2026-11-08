package app.klar.model

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Result of the in-app hearing test: the quietest tone level (dBFS) each ear
 * heard at each frequency in [HearingSettings.BANDS].
 *
 * The levels are relative. The app cannot know how loud the headphones play
 * at a given phone volume, so the result describes the *shape* of a person's
 * hearing (which frequencies are harder to hear), not an absolute audiogram.
 */
data class Audiogram(
    val left: List<Float>,
    val right: List<Float>,
    val timestamp: Long,
) {
    fun thresholds(ear: Ear) = if (ear == Ear.LEFT) left else right

    /** Hearing loss per band in dB, relative to the best-hearing band of either ear. */
    val lossLeft: List<Float> get() = HearingTest.relativeLoss(left, right).first
    val lossRight: List<Float> get() = HearingTest.relativeLoss(left, right).second
}

object HearingTest {
    /** First tone level. */
    const val START_DB = -85f
    /** Level step between tones. */
    const val STEP_DB = 5f
    /** Loudest tone played. If not heard by then, the band counts as "not heard". */
    const val MAX_DB = -15f
    /** Value stored for a band that was not heard at [MAX_DB]. */
    const val NOT_HEARD_DB = MAX_DB + STEP_DB

    /** Upper limit of the suggested equalizer boost. */
    const val MAX_GAIN_DB = 20f

    /**
     * Approximate normal hearing threshold per band, relative to 1 kHz.
     * Ears are least sensitive at low and very high frequencies, so a higher
     * threshold there is normal and must not be treated as a hearing loss.
     */
    val NORMAL_OFFSET_DB = listOf(12f, 5f, 0f, -2f, -4f, 10f)

    /** Test order: 1 kHz first (like clinical audiometry), then up, then down. */
    val FREQUENCY_ORDER = listOf(2, 3, 4, 5, 1, 0)

    /** All steps of a full test: left ear first, then right. */
    val STEPS: List<Pair<Ear, Int>> =
        listOf(Ear.LEFT, Ear.RIGHT).flatMap { ear -> FREQUENCY_ORDER.map { ear to it } }

    fun relativeLoss(left: List<Float>, right: List<Float>): Pair<List<Float>, List<Float>> {
        val nl = left.mapIndexed { i, t -> t - NORMAL_OFFSET_DB[i] }
        val nr = right.mapIndexed { i, t -> t - NORMAL_OFFSET_DB[i] }
        val best = min(nl.min(), nr.min())
        return nl.map { it - best } to nr.map { it - best }
    }

    /**
     * Suggested equalizer per ear using the "half-gain rule" from hearing-aid
     * fitting: boost each band by about half of the measured loss.
     */
    fun suggestedEq(audiogram: Audiogram): Pair<List<Float>, List<Float>> {
        val (l, r) = relativeLoss(audiogram.left, audiogram.right)
        fun gain(loss: Float) = (loss * 0.5f).roundToInt().toFloat().coerceIn(0f, MAX_GAIN_DB)
        return l.map(::gain) to r.map(::gain)
    }
}
