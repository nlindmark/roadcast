package se.roadcast.core.network.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

class RoadcastApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ApiEndpointConfig(val baseUrl: String) {
    val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')
    val isConfigured: Boolean get() = normalizedBaseUrl.isNotBlank()
}

@Singleton
class RoadcastApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val endpointConfig: ApiEndpointConfig,
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    val baseUrl: String get() = endpointConfig.normalizedBaseUrl
    val isConfigured: Boolean get() = endpointConfig.isConfigured

    suspend fun discoverPlaces(request: DiscoverPlacesRequest): DiscoverPlacesResponse =
        post("/v1/places/discover", request)

    suspend fun buildKnowledge(request: BuildKnowledgeRequest): BuildKnowledgeResponse =
        post("/v1/knowledge/build", request)

    suspend fun generatePodcast(request: GeneratePodcastRequest): GeneratePodcastResponse =
        post("/v1/podcast/generate", request)

    suspend fun answerQuestion(request: AnswerQuestionRequest): AnswerQuestionResponse =
        post("/v1/podcast/question", request)

    suspend fun generateSpeech(request: GenerateSpeechRequest): GenerateSpeechResponse =
        post("/v1/speech/generate", request)

    private suspend inline fun <reified TReq, reified TRes> post(
        path: String,
        body: TReq,
    ): TRes = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw RoadcastApiException("API base URL is not configured.")
        }
        val payload = json.encodeToString(body)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(payload.toRequestBody(mediaType))
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RoadcastApiException(
                    "HTTP ${response.code} for $path: ${responseBody.take(240)}",
                )
            }
            runCatching {
                json.decodeFromString<TRes>(responseBody)
            }.getOrElse { error ->
                throw RoadcastApiException("Could not decode $path response.", error)
            }
        }
    }
}

fun createRoadcastHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .build()

fun createRoadcastJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
