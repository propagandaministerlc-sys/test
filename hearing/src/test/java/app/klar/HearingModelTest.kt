package app.klar

import app.klar.model.Audiogram
import app.klar.model.HearingSettings
import app.klar.model.HearingTest
import app.klar.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HearingModelTest {

    @Test
    fun normalHearingNeedsNoBoost() {
        val normal = HearingTest.NORMAL_OFFSET_DB.map { it - 60f }
        val (l, r) = HearingTest.suggestedEq(Audiogram(normal, normal, 0))
        assertEquals(HearingSettings.FLAT_EQ, l)
        assertEquals(HearingSettings.FLAT_EQ, r)
    }

    @Test
    fun highFrequencyLossGetsHalfGain() {
        val base = HearingTest.NORMAL_OFFSET_DB.map { it - 60f }
        val right = base.toMutableList().apply {
            this[4] += 30f // 4 kHz 30 dB worse
            this[5] += 60f // 8 kHz 60 dB worse
        }
        val (l, r) = HearingTest.suggestedEq(Audiogram(base, right, 0))
        assertEquals(HearingSettings.FLAT_EQ, l)
        assertEquals(listOf(0f, 0f, 0f, 0f, 15f, 20f), r)
    }

    @Test
    fun testCoversBothEarsAndAllBands() {
        assertEquals(12, HearingTest.STEPS.size)
        assertEquals(HearingSettings.BANDS.indices.toSet(), HearingTest.FREQUENCY_ORDER.toSet())
    }

    @Test
    fun presetIsRecognisedUntilASliderMoves() {
        val s = HearingSettings().withPreset(Preset.RESTAURANT)
        assertEquals(Preset.RESTAURANT, s.preset)
        assertNull(s.copy(voiceFocus = 0.3f).preset)
    }
}
