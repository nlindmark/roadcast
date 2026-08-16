package se.roadcast.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import se.roadcast.core.model.HostId

class TtsAudioCacheTest {
    @Test
    fun `same text and speaker produce stable keys`() {
        val keyA = hashKey("The station opened in 1858.", HostId.HOST_A)
        val keyB = hashKey("The station opened in 1858.", HostId.HOST_A)
        assertEquals(keyA, keyB)
        assertEquals(64, keyA.length)
    }

    @Test
    fun `different speakers get different keys`() {
        val a = hashKey("Hello there", HostId.HOST_A)
        val b = hashKey("Hello there", HostId.HOST_B)
        assertNotEquals(a, b)
    }

    @Test
    fun `different text gets different keys`() {
        val a = hashKey("One", HostId.HOST_A)
        val b = hashKey("Two", HostId.HOST_A)
        assertNotEquals(a, b)
    }

    private fun hashKey(text: String, speaker: HostId): String {
        val payload = "v1|default|${speaker.name}|$text"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
