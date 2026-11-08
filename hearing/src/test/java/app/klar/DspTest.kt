package app.klar

import app.klar.dsp.Equalizer
import app.klar.dsp.Fft
import app.klar.dsp.HearingDsp
import app.klar.dsp.SpectralProcessor
import app.klar.dsp.VoiceFocus
import app.klar.model.HearingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class DspTest {

    @Test
    fun fftRoundTripRestoresSignal() {
        val n = 512
        val fft = Fft(n)
        val rnd = Random(1)
        val orig = FloatArray(n) { rnd.nextGaussian().toFloat() }
        val re = orig.copyOf()
        val im = FloatArray(n)
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in 0 until n) {
            assertEquals(orig[i], re[i], 1e-4f)
            assertEquals(0f, im[i], 1e-4f)
        }
    }

    @Test
    fun fftFindsSineInRightBin() {
        val n = 256
        val fft = Fft(n)
        val re = FloatArray(n) { sin(2 * PI * 10 * it / n).toFloat() }
        val im = FloatArray(n)
        fft.forward(re, im)
        val mags = FloatArray(n / 2) { re[it] * re[it] + im[it] * im[it] }
        assertEquals(10, mags.indices.maxByOrNull { mags[it] })
        assertEquals((n / 2f) * (n / 2f), mags[10], 1f)
    }

    @Test
    fun spectralProcessorIsTransparentWithNeutralSettings() {
        val sp = SpectralProcessor(48000, 512)
        sp.configure(0f, 0f, HearingSettings.FLAT_EQ, HearingSettings.FLAT_EQ, HearingSettings.BANDS)
        val rnd = Random(2)
        val hop = sp.hop
        val blocks = 40
        val input = FloatArray(hop * blocks) { rnd.nextGaussian().toFloat() * 0.1f }
        val outL = FloatArray(hop * blocks)
        val outR = FloatArray(hop * blocks)
        val l = FloatArray(hop)
        val r = FloatArray(hop)
        for (b in 0 until blocks) {
            sp.process(input.copyOfRange(b * hop, (b + 1) * hop), l, r)
            l.copyInto(outL, b * hop)
            r.copyInto(outR, b * hop)
        }
        // Output is the input delayed by one hop.
        for (i in 2 * hop until hop * blocks) {
            assertEquals(input[i - hop], outL[i], 1e-4f)
            assertEquals(input[i - hop], outR[i], 1e-4f)
        }
    }

    @Test
    fun noiseReductionAttenuatesSteadyNoiseButKeepsTone() {
        val fs = 48000
        val sp = SpectralProcessor(fs, 512)
        sp.configure(1f, 0f, HearingSettings.FLAT_EQ, HearingSettings.FLAT_EQ, HearingSettings.BANDS)
        val rnd = Random(3)
        val hop = sp.hop
        val seconds = 4
        val blocks = fs * seconds / hop
        val l = FloatArray(hop)
        val r = FloatArray(hop)
        var noiseIn = 0.0
        var noiseOut = 0.0
        var toneIn = 0.0
        var toneOut = 0.0
        var t = 0
        for (b in 0 until blocks) {
            val noise = FloatArray(hop) { rnd.nextGaussian().toFloat() * 0.01f }
            // A loud 1 kHz tone during the second half only.
            val withTone = b >= blocks / 2
            val block = FloatArray(hop) {
                noise[it] + if (withTone) 0.2f * sin(2 * PI * 1000 * (t + it) / fs).toFloat() else 0f
            }
            t += hop
            sp.process(block, l, r)
            val e = l.sumOf { (it * it).toDouble() }
            val ein = block.sumOf { (it * it).toDouble() }
            if (b in blocks / 4 until blocks / 2) {
                noiseIn += ein
                noiseOut += e
            }
            if (b >= blocks / 2 + 20) {
                toneIn += ein
                toneOut += e
            }
        }
        val noiseDropDb = 10 * log10(noiseIn / noiseOut)
        val toneDropDb = 10 * log10(toneIn / toneOut)
        assertTrue("noise reduced by $noiseDropDb dB", noiseDropDb > 10)
        assertTrue("tone kept within $toneDropDb dB", abs(toneDropDb) < 1.5)
    }

    @Test
    fun speechLikeSignalIsDetected() {
        val fs = 48000
        val sp = SpectralProcessor(fs, 512)
        sp.configure(0.5f, 1f, HearingSettings.FLAT_EQ, HearingSettings.FLAT_EQ, HearingSettings.BANDS)
        val rnd = Random(4)
        val hop = sp.hop
        val l = FloatArray(hop)
        val r = FloatArray(hop)
        var t = 0
        repeat(fs * 2 / hop) {
            sp.process(FloatArray(hop) { rnd.nextGaussian().toFloat() * 0.005f }, l, r)
            t += hop
        }
        assertTrue(sp.speechProbability < 0.2f)
        repeat(fs / 2 / hop) {
            val block = FloatArray(hop) {
                val n = t + it
                rnd.nextGaussian().toFloat() * 0.005f +
                    0.1f * sin(2 * PI * 220 * n / fs).toFloat() +
                    0.08f * sin(2 * PI * 660 * n / fs).toFloat() +
                    0.05f * sin(2 * PI * 1500 * n / fs).toFloat()
            }
            sp.process(block, l, r)
            t += hop
        }
        assertTrue(sp.speechProbability > 0.8f)
    }

    @Test
    fun limiterNeverExceedsCeiling() {
        val dsp = HearingDsp(48000)
        val settings = HearingSettings(volumeDb = 36f, maxOutputDb = -12f, eqLeft = List(6) { 24f })
        dsp.configure(settings)
        val ceiling = Math.pow(10.0, -12.0 / 20).toFloat()
        val rnd = Random(5)
        val out = FloatArray(dsp.blockSize * 2)
        repeat(400) {
            val block = FloatArray(dsp.blockSize) { (rnd.nextFloat() * 2 - 1) * 0.9f }
            dsp.process(block, out)
            for (x in out) assertTrue("sample $x above ceiling", abs(x) <= ceiling + 1e-6f)
        }
    }

    @Test
    fun volumeAmplifiesQuietSignal() {
        val fs = 48000
        val dsp = HearingDsp(fs)
        dsp.configure(
            HearingSettings(volumeDb = 20f, voiceFocus = 0f, noiseReduction = 0f, compression = 0f, maxOutputDb = 0f),
        )
        val out = FloatArray(dsp.blockSize * 2)
        var t = 0
        repeat(fs / dsp.blockSize) {
            val block = FloatArray(dsp.blockSize) { 0.01f * sin(2 * PI * 1000 * (t + it) / fs).toFloat() }
            t += dsp.blockSize
            dsp.process(block, out)
        }
        // 0.01 amplitude sine = -43 dBFS RMS; +20 dB gives about -23 dBFS.
        assertEquals(-23f, dsp.outputDb, 1f)
    }

    @Test
    fun compressionReducesLoudLevelsOnly() {
        val dsp = HearingDsp(48000)
        dsp.configure(HearingSettings(compression = 1f)) // ratio 3
        assertEquals(0f, dsp.compressionDb(-70f), 1e-4f)
        assertEquals(20f * (2f / 3f), dsp.compressionDb(-25f), 1e-3f)
    }

    @Test
    fun equalizerInterpolatesOnLogFrequency() {
        val bands = HearingSettings.BANDS
        val gains = listOf(0f, 0f, 0f, 12f, 12f, 12f)
        assertEquals(0f, Equalizer.gainDb(100f, bands, gains), 1e-4f)
        assertEquals(12f, Equalizer.gainDb(10000f, bands, gains), 1e-4f)
        assertEquals(6f, Equalizer.gainDb(1414.2f, bands, gains), 0.05f)
    }

    @Test
    fun voiceFocusBoostsPresenceAndCutsRumble() {
        assertEquals(0f, VoiceFocus.weightDb(100f, 0f), 0f)
        assertTrue(VoiceFocus.weightDb(100f, 1f) < -10f)
        assertTrue(VoiceFocus.weightDb(2500f, 1f) > 4f)
        assertEquals(0f, VoiceFocus.weightDb(500f, 1f), 0f)
    }
}
