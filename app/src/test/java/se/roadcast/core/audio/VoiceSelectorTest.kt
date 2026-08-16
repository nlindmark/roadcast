package se.roadcast.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.HostId

class VoiceSelectorTest {
    private val voices = listOf(
        VoiceOption("low", "en-us-x-low", "en_US", VoiceSelector.QualityNormal, 300, false),
        VoiceOption("a", "en-gb-x-amy-neural", "en_GB", VoiceSelector.QualityVeryHigh, 200, false),
        VoiceOption("b", "en-gb-x-brian-neural", "en_GB", VoiceSelector.QualityVeryHigh, 200, false),
        VoiceOption("net", "en-us-x-cloud", "en_US", VoiceSelector.QualityVeryHigh, 100, true),
    )

    @Test
    fun `prefers very high quality offline voices`() {
        val picked = VoiceSelector.pick(HostId.HOST_A, voices, allowNetwork = false)
        assertEquals("a", picked!!.id)
        assertTrue(picked.quality >= VoiceSelector.QualityVeryHigh)
    }

    @Test
    fun `hosts get different voices when possible`() {
        val a = VoiceSelector.pick(HostId.HOST_A, voices, allowNetwork = false)!!
        val b = VoiceSelector.pick(HostId.HOST_B, voices, allowNetwork = false, occupiedVoiceIds = setOf(a.id))!!
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `network voices excluded unless allowed`() {
        val offline = VoiceSelector.pick(HostId.HOST_A, voices.filter { it.requiresNetwork }, allowNetwork = false)
        assertEquals(null, offline)
        val online = VoiceSelector.pick(HostId.HOST_A, voices.filter { it.requiresNetwork }, allowNetwork = true)
        assertEquals("net", online!!.id)
    }
}
