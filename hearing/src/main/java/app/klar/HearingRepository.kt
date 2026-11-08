package app.klar

import android.content.Context
import android.content.SharedPreferences
import app.klar.model.Audiogram
import app.klar.model.HearingSettings
import app.klar.model.MicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live levels from the audio engine, for the meters in the UI. */
data class Meters(
    val inputDb: Float = -120f,
    val outputDb: Float = -120f,
    val reductionDb: Float = 0f,
    val speech: Float = 0f,
)

/**
 * Single source of truth shared by the UI, the foreground service and the
 * audio thread. Settings are persisted in SharedPreferences.
 */
object HearingRepository {
    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(HearingSettings())
    val settings: StateFlow<HearingSettings> = _settings.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _meters = MutableStateFlow(Meters())
    val meters: StateFlow<Meters> = _meters.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _audiogram = MutableStateFlow<Audiogram?>(null)
    val audiogram: StateFlow<Audiogram?> = _audiogram.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("klar", Context.MODE_PRIVATE)
        _settings.value = loadSettings()
        _audiogram.value = loadAudiogram()
    }

    fun update(transform: (HearingSettings) -> HearingSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        saveSettings(next)
    }

    fun setRunning(running: Boolean) {
        _running.value = running
        if (!running) _meters.value = Meters()
    }

    fun publishMeters(m: Meters) {
        _meters.value = m
    }

    fun reportError(message: String?) {
        _error.value = message
    }

    fun saveAudiogram(a: Audiogram) {
        _audiogram.value = a
        prefs.edit()
            .putString(K_AUD_L, a.left.joinToString(","))
            .putString(K_AUD_R, a.right.joinToString(","))
            .putLong(K_AUD_T, a.timestamp)
            .apply()
    }

    private fun saveSettings(s: HearingSettings) {
        prefs.edit()
            .putFloat(K_VOLUME, s.volumeDb)
            .putFloat(K_FOCUS, s.voiceFocus)
            .putFloat(K_NR, s.noiseReduction)
            .putFloat(K_COMP, s.compression)
            .putFloat(K_BALANCE, s.balance)
            .putString(K_EQ_L, s.eqLeft.joinToString(","))
            .putString(K_EQ_R, s.eqRight.joinToString(","))
            .putString(K_MIC, s.micSource.name)
            .putFloat(K_CEILING, s.maxOutputDb)
            .putBoolean(K_SYS_NS, s.systemNoiseSuppressor)
            .apply()
    }

    private fun loadSettings(): HearingSettings {
        val d = HearingSettings()
        return HearingSettings(
            volumeDb = prefs.getFloat(K_VOLUME, d.volumeDb),
            voiceFocus = prefs.getFloat(K_FOCUS, d.voiceFocus),
            noiseReduction = prefs.getFloat(K_NR, d.noiseReduction),
            compression = prefs.getFloat(K_COMP, d.compression),
            balance = prefs.getFloat(K_BALANCE, d.balance),
            eqLeft = parseList(prefs.getString(K_EQ_L, null)) ?: d.eqLeft,
            eqRight = parseList(prefs.getString(K_EQ_R, null)) ?: d.eqRight,
            micSource = runCatching { MicSource.valueOf(prefs.getString(K_MIC, null)!!) }.getOrDefault(d.micSource),
            maxOutputDb = prefs.getFloat(K_CEILING, d.maxOutputDb),
            systemNoiseSuppressor = prefs.getBoolean(K_SYS_NS, d.systemNoiseSuppressor),
        )
    }

    private fun loadAudiogram(): Audiogram? {
        val l = parseList(prefs.getString(K_AUD_L, null)) ?: return null
        val r = parseList(prefs.getString(K_AUD_R, null)) ?: return null
        return Audiogram(l, r, prefs.getLong(K_AUD_T, 0L))
    }

    private fun parseList(s: String?): List<Float>? {
        val values = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return null
        return values.takeIf { it.size == HearingSettings.BANDS.size }
    }

    private const val K_VOLUME = "volume"
    private const val K_FOCUS = "focus"
    private const val K_NR = "nr"
    private const val K_COMP = "comp"
    private const val K_BALANCE = "balance"
    private const val K_EQ_L = "eq_l"
    private const val K_EQ_R = "eq_r"
    private const val K_MIC = "mic"
    private const val K_CEILING = "ceiling"
    private const val K_SYS_NS = "sys_ns"
    private const val K_AUD_L = "aud_l"
    private const val K_AUD_R = "aud_r"
    private const val K_AUD_T = "aud_t"
}
