package se.roadcast.core.network.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.TravelState

class RoadcastApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RoadcastApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RoadcastApiClient(
            httpClient = createRoadcastHttpClient(),
            json = createRoadcastJson(),
            endpointConfig = ApiEndpointConfig(server.url("/").toString().trimEnd('/')),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `discover posts travel state and decodes places`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "places": [
                        {
                          "id": "ullevi",
                          "name": "Ullevi",
                          "position": {"latitude": 57.705, "longitude": 11.987},
                          "distanceMeters": 400.0,
                          "bearingFromUser": 90.0,
                          "category": "CULTURE",
                          "importanceScore": 0.8,
                          "shortDescription": "Arena"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val response = client.discoverPlaces(
            DiscoverPlacesRequest(
                travelState = TravelState(
                    currentPosition = GeoPoint(57.70, 11.97),
                    timestampEpochMillis = 1L,
                ),
            ),
        )

        assertEquals(1, response.places.size)
        assertEquals("ullevi", response.places.first().id)
        assertEquals(PlaceCategory.CULTURE, response.places.first().category)
        val recorded = server.takeRequest()
        assertEquals("/v1/places/discover", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("57.7"))
    }

    @Test
    fun `speech endpoint decodes generated speech`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "speech": {
                        "uri": "https://cdn.example/audio.wav",
                        "durationMillis": 1200,
                        "text": "Hello",
                        "speaker": "HOST_A"
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val response = client.generateSpeech(
            GenerateSpeechRequest(text = "Hello", speaker = HostId.HOST_A),
        )

        assertEquals("https://cdn.example/audio.wav", response.speech.uri)
        assertEquals(HostId.HOST_A, response.speech.speaker)
        assertEquals("/v1/speech/generate", server.takeRequest().path)
    }

    @Test
    fun `http errors become RoadcastApiException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"busy"}"""))
        val result = runCatching {
            client.discoverPlaces(
                DiscoverPlacesRequest(
                    travelState = TravelState(
                        currentPosition = GeoPoint(57.70, 11.97),
                        timestampEpochMillis = 1L,
                    ),
                ),
            )
        }
        assertTrue(result.exceptionOrNull() is RoadcastApiException)
    }
}
