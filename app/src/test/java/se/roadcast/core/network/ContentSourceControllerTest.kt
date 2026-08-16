package se.roadcast.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.network.api.ApiEndpointConfig

class ContentSourceControllerTest {
    @Test
    fun `remote remains disabled without base url`() {
        val content = DefaultContentSourceController(ApiEndpointConfig(""))
        content.setSource(ContentSource.REMOTE)
        assertEquals(ContentSource.SIMULATION, content.source.value)
        assertFalse(content.shouldUseRemote())
        assertTrue(content.lastError.value!!.contains("local.properties"))
    }

    @Test
    fun `remote can be enabled when base url is configured`() {
        val content = DefaultContentSourceController(ApiEndpointConfig("https://api.example"))
        content.setSource(ContentSource.REMOTE)
        assertEquals(ContentSource.REMOTE, content.source.value)
        assertTrue(content.shouldUseRemote())
        assertEquals(null, content.lastError.value)
    }
}
