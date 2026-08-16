package se.roadcast.core.audio

import se.roadcast.core.model.HostId

data class VoiceOption(
    val id: String,
    val name: String,
    val language: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
)

/**
 * Picks distinct high-quality English voices for the two hosts.
 * Prefers neural / very-high quality voices and keeps Host A and Host B different.
 */
object VoiceSelector {
    const val QualityVeryHigh = 400
    const val QualityHigh = 300
    const val QualityNormal = 200

    fun pick(
        host: HostId,
        voices: List<VoiceOption>,
        allowNetwork: Boolean,
        occupiedVoiceIds: Set<String> = emptySet(),
    ): VoiceOption? {
        val eligible = voices
            .filter { it.language.startsWith("en", ignoreCase = true) }
            .filter { allowNetwork || !it.requiresNetwork }
            .sortedWith(
                compareByDescending<VoiceOption> { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name },
            )
        if (eligible.isEmpty()) return null

        val preferred = when (host) {
            HostId.HOST_A -> eligible.filter { looksStoryteller(it.name) }
            HostId.HOST_B -> eligible.filter { looksSpecialist(it.name) }
        }
        val pool = (preferred + eligible).distinctBy { it.id }
        return pool.firstOrNull { it.id !in occupiedVoiceIds } ?: pool.firstOrNull()
    }

    private fun looksStoryteller(name: String): Boolean {
        val n = name.lowercase()
        return listOf("female", "woman", "fiona", "serena", "amy", "emma", "liv", "neural").any { it in n }
    }

    private fun looksSpecialist(name: String): Boolean {
        val n = name.lowercase()
        return listOf("male", "man", "brian", "daniel", "ryan", "nils", "neural", "en-gb").any { it in n }
    }
}
