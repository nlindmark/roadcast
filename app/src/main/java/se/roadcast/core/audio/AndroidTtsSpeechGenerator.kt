package se.roadcast.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) : SpeechGenerator {
    private val ready = CompletableDeferred<Boolean>()
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
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
    }

    override suspend fun synthesize(text: String, speaker: HostId): GeneratedSpeech {
        if (!ready.await()) {
            error("Text-to-speech is unavailable. Install or enable an Android speech engine.")
        }
        val key = cache.key(text, speaker)
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

    private fun configureHost(host: HostId) {
        engine.language = Locale.UK
        val voices = engine.voices
            ?.filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.ENGLISH.language }
            ?.sortedBy { it.name }
            .orEmpty()
        when (host) {
            HostId.HOST_A -> {
                voices.getOrNull(0)?.let(engine::setVoice)
                engine.setPitch(0.92f)
                engine.setSpeechRate(0.94f)
            }
            HostId.HOST_B -> {
                voices.getOrNull(1)?.let(engine::setVoice)
                engine.setPitch(1.08f)
                engine.setSpeechRate(1.02f)
            }
        }
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
