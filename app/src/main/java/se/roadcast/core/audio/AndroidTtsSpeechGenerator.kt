package se.roadcast.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.roadcast.core.database.SettingsRepository
import se.roadcast.core.model.GeneratedSpeech
import se.roadcast.core.model.HostId
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTtsSpeechGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: TtsAudioCache,
    private val settingsRepository: SettingsRepository,
) : SpeechGenerator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ready = CompletableDeferred<Boolean>()
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    @Volatile private var allowNetworkVoices = false
    private var hostAVoiceId: String? = null
    private var hostBVoiceId: String? = null

    private val engine = TextToSpeech(context) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    pending.remove(utteranceId)?.complete(true)
                }

                @Deprecated("Required by older Android TTS implementations")
                override fun onError(utteranceId: String) {
                    pending.remove(utteranceId)?.complete(false)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    pending.remove(utteranceId)?.complete(false)
                }
            },
        )
        scope.launch {
            settingsRepository.settings.collect { settings ->
                allowNetworkVoices = settings.allowNetworkVoices
            }
        }
    }

    override suspend fun synthesize(text: String, speaker: HostId): GeneratedSpeech {
        if (!ready.await()) {
            error("Text-to-speech is unavailable. Install or enable an Android speech engine.")
        }
        val voice = configureHost(speaker)
        val fingerprint = voice?.name ?: "default-${if (allowNetworkVoices) "net" else "local"}"
        val key = cache.key(text, speaker, fingerprint)
        cache.existing(key)?.let { file ->
            return GeneratedSpeech(
                uri = Uri.fromFile(file).toString(),
                durationMillis = probeDurationMillis(file),
                text = text,
                speaker = speaker,
            )
        }

        return mutex.withLock {
            cache.existing(key)?.let { file ->
                return@withLock GeneratedSpeech(
                    uri = Uri.fromFile(file).toString(),
                    durationMillis = probeDurationMillis(file),
                    text = text,
                    speaker = speaker,
                )
            }
            val file = cache.fileFor(key)
            if (file.exists()) file.delete()
            configureHost(speaker)
            val done = CompletableDeferred<Boolean>()
            pending[key] = done
            val result = engine.synthesizeToFile(text, Bundle(), file, key)
            if (result == TextToSpeech.ERROR) {
                pending.remove(key)
                error("Android could not synthesize speech for this line.")
            }
            if (!done.await()) {
                file.delete()
                error("Android could not synthesize speech for this line.")
            }
            if (!file.exists() || file.length() <= 44L) {
                error("Synthesized audio file was empty.")
            }
            GeneratedSpeech(
                uri = Uri.fromFile(file).toString(),
                durationMillis = probeDurationMillis(file),
                text = text,
                speaker = speaker,
            )
        }
    }

    private fun configureHost(host: HostId): Voice? {
        engine.language = Locale.UK
        val options = engine.voices.orEmpty().map { voice ->
            VoiceOption(
                id = voice.name,
                name = voice.name,
                language = voice.locale.toLanguageTag(),
                quality = voice.quality,
                latency = voice.latency,
                requiresNetwork = voice.isNetworkConnectionRequired,
            )
        }
        val occupied = when (host) {
            HostId.HOST_A -> setOfNotNull(hostBVoiceId)
            HostId.HOST_B -> setOfNotNull(hostAVoiceId)
        }
        val selected = VoiceSelector.pick(host, options, allowNetworkVoices, occupied)
        val voice = engine.voices?.firstOrNull { it.name == selected?.id }
        if (voice != null) {
            engine.voice = voice
            when (host) {
                HostId.HOST_A -> hostAVoiceId = voice.name
                HostId.HOST_B -> hostBVoiceId = voice.name
            }
        }
        when (host) {
            HostId.HOST_A -> {
                engine.setPitch(0.94f)
                engine.setSpeechRate(0.93f)
            }
            HostId.HOST_B -> {
                engine.setPitch(1.05f)
                engine.setSpeechRate(1.0f)
            }
        }
        return voice
    }

    private fun probeDurationMillis(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(500L)
                ?: estimateDurationMillis(file.length())
        } catch (_: Exception) {
            estimateDurationMillis(file.length())
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun estimateDurationMillis(byteLength: Long): Long =
        ((byteLength / 32L).coerceAtLeast(800L))
}
