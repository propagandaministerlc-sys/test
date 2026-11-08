package app.klar.model

import kotlin.math.abs

/** Ready-made sound situations. Each one sets the three main sliders. */
enum class Preset(
    val label: String,
    val description: String,
    val voiceFocus: Float,
    val noiseReduction: Float,
    val compression: Float,
) {
    NATURAL(
        "Natürlich",
        "Leichte Verstärkung, die möglichst unverfälscht klingt.",
        voiceFocus = 0.15f, noiseReduction = 0.2f, compression = 0.3f,
    ),
    CONVERSATION(
        "Gespräch",
        "Hebt Stimmen in ruhiger bis mittellauter Umgebung hervor.",
        voiceFocus = 0.65f, noiseReduction = 0.5f, compression = 0.5f,
    ),
    RESTAURANT(
        "Restaurant",
        "Starke Rauschminderung und klarer Fokus auf Sprache in lauter Umgebung.",
        voiceFocus = 1f, noiseReduction = 0.85f, compression = 0.65f,
    ),
    TV(
        "TV",
        "Für Fernsehen und Musik. Lege das Telefon nah an den Lautsprecher.",
        voiceFocus = 0.35f, noiseReduction = 0.35f, compression = 0.45f,
    ),
}

enum class MicSource { PHONE, HEADSET }

enum class Ear { LEFT, RIGHT }

/** Everything the user can adjust. Immutable: each change produces a new instance. */
data class HearingSettings(
    /** Overall amplification in dB, 0..[MAX_VOLUME_DB]. */
    val volumeDb: Float = 12f,
    /** 0..1: how strongly the speech band is emphasised and pauses are quietened. */
    val voiceFocus: Float = Preset.CONVERSATION.voiceFocus,
    /** 0..1: strength of the spectral noise reduction. */
    val noiseReduction: Float = Preset.CONVERSATION.noiseReduction,
    /** 0..1: how much soft sounds are lifted relative to loud ones. */
    val compression: Float = Preset.CONVERSATION.compression,
    /** -1 (only left) .. 0 (centre) .. 1 (only right). */
    val balance: Float = 0f,
    /** Equalizer gain in dB per band in [BANDS], left ear. */
    val eqLeft: List<Float> = FLAT_EQ,
    /** Equalizer gain in dB per band in [BANDS], right ear. */
    val eqRight: List<Float> = FLAT_EQ,
    val micSource: MicSource = MicSource.PHONE,
    /** Safety ceiling of the output limiter in dBFS. */
    val maxOutputDb: Float = -6f,
    /** Also enable Android's own noise suppressor effect when the device has one. */
    val systemNoiseSuppressor: Boolean = false,
) {
    /** The preset whose values match the current sliders, or null for a custom mix. */
    val preset: Preset?
        get() = Preset.entries.firstOrNull {
            abs(it.voiceFocus - voiceFocus) < 0.005f &&
                abs(it.noiseReduction - noiseReduction) < 0.005f &&
                abs(it.compression - compression) < 0.005f
        }

    fun withPreset(p: Preset) = copy(
        voiceFocus = p.voiceFocus,
        noiseReduction = p.noiseReduction,
        compression = p.compression,
    )

    fun eq(ear: Ear) = if (ear == Ear.LEFT) eqLeft else eqRight

    companion object {
        /** Equalizer band centres in Hz; also the hearing-test frequencies. */
        val BANDS = listOf(250, 500, 1000, 2000, 4000, 8000)
        val FLAT_EQ: List<Float> = List(BANDS.size) { 0f }

        const val MAX_VOLUME_DB = 36f
        const val EQ_MIN_DB = -12f
        const val EQ_MAX_DB = 24f
        const val MIN_CEILING_DB = -24f
        const val MAX_CEILING_DB = 0f
    }
}
