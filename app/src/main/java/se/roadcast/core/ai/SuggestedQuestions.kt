package se.roadcast.core.ai

import se.roadcast.core.model.PlaceKnowledgePackage

object SuggestedQuestions {
    fun from(knowledge: PlaceKnowledgePackage): List<String> {
        val fromFacts = knowledge.facts.take(3).map { fact ->
            "What about ${fact.statement.trimEnd('.').lowercase()}?"
        }
        val fromStories = knowledge.stories.take(1).map { "Tell me more about ${it.title}." }
        val fallback = listOf(
            "What is the strongest verified fact here?",
            "Is anything about this place uncertain?",
        )
        return (fromFacts + fromStories + fallback).distinct().take(4)
    }
}
