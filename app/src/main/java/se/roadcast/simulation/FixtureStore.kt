package se.roadcast.simulation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.SourceKind
import se.roadcast.core.model.SourceReference
import se.roadcast.core.model.StoryAngle
import se.roadcast.core.model.VerifiedFact
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FixtureStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val route: List<GeoPoint> by lazy {
        json.decodeFromString(
            ListSerializer(GeoPoint.serializer()),
            readAsset("fixtures/swedish_route.json"),
        )
    }

    val places: List<PlaceCandidate> by lazy {
        json.decodeFromString(
            ListSerializer(PlaceFixture.serializer()),
            readAsset("fixtures/swedish_places.json"),
        ).map(PlaceFixture::toDomain)
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}

@Serializable
private data class PlaceFixture(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val category: PlaceCategory,
    val importance: Double,
    val knowledge: KnowledgeFixture,
) {
    fun toDomain(): PlaceCandidate {
        val references = knowledge.facts.flatMap { it.provenance }.distinctBy { it.sourceId }
        val knowledgePackage = PlaceKnowledgePackage(
            placeId = id,
            overview = knowledge.summary,
            facts = knowledge.facts.map { fact ->
                VerifiedFact(fact.id, fact.statement, fact.provenance.map { it.sourceId }, fact.confidence)
            },
            stories = listOf(
                StoryAngle(
                    id = "overview-$id",
                    title = name,
                    premise = knowledge.summary,
                    relatedFactIds = knowledge.facts.map { it.id },
                ),
            ),
            sources = references.map { it.toDomain() },
            sourceQualityScore = knowledge.sourceQuality,
        )
        return PlaceCandidate(
            id = id,
            name = name,
            position = location,
            distanceMeters = 0.0,
            bearingFromUser = 0.0,
            category = category,
            importanceScore = importance,
            shortDescription = knowledge.summary,
            knowledgePackage = knowledgePackage,
        )
    }
}

@Serializable
private data class KnowledgeFixture(
    val placeId: String,
    val summary: String,
    val facts: List<FactFixture>,
    val sourceQuality: Double,
)

@Serializable
private data class FactFixture(
    val id: String,
    val statement: String,
    val provenance: List<SourceFixture>,
    val confidence: Double,
)

@Serializable
private data class SourceFixture(
    val sourceId: String,
    val title: String,
    val url: String? = null,
    val kind: SourceKind,
) {
    fun toDomain() = SourceReference(sourceId, title, url, kind)
}
