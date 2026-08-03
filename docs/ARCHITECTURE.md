# Roadcast architecture

## Current shape

The MVP deliberately ships as one `:app` module to minimize build complexity. Package boundaries mirror intended Gradle modules:

- `core.model`: serializable domain language only.
- `core.location`: location contract, geometry, and ranking policy.
- `core.network`: discovery and knowledge provider contracts; it does not prescribe HTTP.
- `core.database`: playback-history persistence contract.
- `core.ai` and `core.audio`: dialogue, question, synthesis, and podcast seams.
- `simulation`: asset loading and deterministic fake implementations.
- `feature.player`, `feature.history`, `feature.settings`, `feature.debug`: ViewModels, explicit UI state, and screens.
- `di`: the composition root that selects simulation implementations.

Dependencies point inward: features depend on core contracts; simulation implements core contracts; core has no feature, Android UI, or provider dependency. Composables receive immutable state and callbacks. ViewModels coordinate flows and repository calls.

The shared domain language follows the production pipeline: `PlaceCandidate` → `PlaceKnowledgePackage` →
`PodcastSegment` → `GeneratedAudioLine` → `PodcastQueueItem`. `DialogueGenerator`, `SpeechGenerator`,
`UserSpeechRecognizer`, and `PodcastOrchestrator` expose those types directly. The simulation JSON format is
translated by private fixture DTOs and is not a public domain contract.

## Target module structure

When build and ownership costs justify extraction, packages become `:core:model`, `:core:location`, `:core:network`, `:core:database`, `:core:ai`, `:core:audio`, `:simulation`, and one module per feature. `:app` remains only the Android entry point, navigation graph, theme, and Hilt composition root. Interfaces stay in core modules; provider SDK adapters live in separate modules such as `:provider:places`, `:provider:knowledge`, `:provider:llm`, and `:provider:tts`.

## Architectural decisions

1. **Rank, then select.** Discovery returns candidates ordered by a deterministic, auditable policy instead of selecting the nearest point.
2. **Finite route corridor.** Route fit is distance to a projected forward segment, avoiding false alignment with the infinite road line.
3. **Source gate before scoring.** Candidates with too few facts, low source quality, or facts without provenance are excluded.
4. **Stable identity.** Place IDs, fact IDs, source IDs, and episode IDs support deduplication and replay history. The simulation repository removes played IDs before ranking; ranking still models a repetition penalty for broader recency policies.
5. **No nullable state machine.** Loading, success, empty, and error are distinct states. Nullable fields only represent domain optionality.
6. **Provider neutrality.** Core interfaces avoid Retrofit, database entities, speech SDK types, and LLM wire formats.
7. **Deterministic fixtures.** Route timestamps, coordinates, ordering, and ranking tie-breakers are repeatable. Runtime-only queue timestamps are not part of selection.
8. **Unknown motion is explicit.** Bearing, speed, and accuracy may be absent. Ranking uses neutral route alignment
   and no ETA until bearing and speed are known; it does not silently invent motion.

## Phased dependencies

- **Phase 1:** Compose shell → feature ViewModels → core interfaces and models; Hilt supplies implementations.
- **Phase 2:** simulation implementations → fixture assets and pure geometry/ranking; Debug observes a read-only pipeline snapshot.
- **Phase 3:** real fused location and persistent history adapters; simulation remains selectable in debug builds.
- **Phase 4:** remote place/knowledge providers with caching, attribution, quotas, retries, and offline behavior.
- **Phase 5:** dialogue/TTS/playback pipeline, interruption handling, user questions, and safety/quality evaluation.

Later phases must not add provider types to core models. Persistence maps domain objects at repository boundaries. Network and AI adapters should use explicit timeouts, typed failures, redacted telemetry, and independently replaceable credentials.

## Assumptions

- The predefined route is a product-development route through central/eastern Gothenburg, not turn-by-turn navigation.
- A place needs at least two independently attributable facts and source quality of 0.45 to be narration-eligible.
- Interest values and normalized quality/importance inputs are clamped to `[0, 1]`.
- “Ahead” means within 90 degrees of current bearing. The default projected corridor is 20 km long and 2.5 km wide.
- In-memory history and placeholder audio are intentional Phase 2 constraints.
- Runtime fixture content is illustrative and should receive editorial/legal review before publication.

## Milestones and exit criteria

1. **MVP simulation (current):** deterministic route controls, transparent selection, polished shell, and geometry/ranking tests.
2. **Device drive:** permission UX, background-aware fused location, persisted history/preferences, route replay comparison, and battery measurements.
3. **Connected discovery:** cached provider adapters, provenance validation, source licenses, offline fallback, and contract tests.
4. **Playable Roadcast:** generated dialogue and audio, media session, queue recovery, latency budgets, and content safety gates.
5. **Pilot:** telemetry consent, accessibility/localization, editorial tooling, operational dashboards, and controlled road testing.
