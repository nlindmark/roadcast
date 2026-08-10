# Roadcast Android MVP

Roadcast is a location-aware travel podcast prototype. Phases 1 and 2 run entirely from deterministic Swedish route and place fixtures: no API keys, network calls, microphone access, or production location permissions are required.

## Included

- One `:app` module using Kotlin, Compose Material 3, Navigation, Hilt, coroutines, and serialization.
- Player, History, Settings, and Debug destinations with explicit UI state.
- Serializable domain contracts and implementation-independent location, discovery, knowledge, AI, speech, and orchestration seams.
- Pure Kotlin route geometry and transparent weighted place ranking.
- A controllable simulation route through Gothenburg with six sourced places.
- A grounded two-host preview spoken through Android's installed text-to-speech engine.
- Validated dialogue JSON generation with automatic playback along the simulated route.
- JVM unit tests for geometry and ranking behavior.

## Build

Prerequisites are JDK 17 and Android SDK 36. The checked-in Gradle 8.13 wrapper runs:

```text
./gradlew test
./gradlew assembleDebug
```

On Windows use `gradlew.bat`. The current implementation contains no secrets and does not make network requests at runtime. Development dependency downloads are still required on the first Gradle build.

## Simulation

Open **Debug** to start or pause route playback, reset, jump to the next route-adjacent place, or choose a 0.5×–4× speed. Candidate cards expose each score component, penalties, selection, and rationale. The Player destination reflects the highest-ranked eligible upcoming story.

On **Player**, tap **Start journey** to move along the simulated route and autoplay the next ranked place. Pause, resume, replay, and skip are available. The two hosts use separate installed voices where the device provides them, with pitch and speaking-rate differences as a fallback. No network speech service is required.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for extraction boundaries and the milestone plan.
