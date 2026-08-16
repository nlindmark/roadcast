package se.roadcast.core.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import se.roadcast.core.model.HostId
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsAudioCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root: File = File(context.cacheDir, "tts-audio").also { it.mkdirs() }

    fun key(text: String, speaker: HostId, voiceFingerprint: String = "default"): String {
        val payload = "v1|$voiceFingerprint|${speaker.name}|$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun fileFor(key: String): File = File(root, "$key.wav")

    fun existing(key: String): File? =
        fileFor(key).takeIf { it.exists() && it.length() > 44L }

    fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }
}
