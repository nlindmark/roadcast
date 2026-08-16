package se.roadcast.core.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureStarted() {
        val intent = Intent(context, RoadcastPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        context.stopService(Intent(context, RoadcastPlaybackService::class.java))
    }
}
