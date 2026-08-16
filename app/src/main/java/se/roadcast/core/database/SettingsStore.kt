package se.roadcast.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import se.roadcast.core.location.LocationMode
import se.roadcast.core.network.ContentSource
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "roadcast_settings")

data class AppSettings(
    val locationMode: LocationMode = LocationMode.SIMULATION,
    val contentSource: ContentSource = ContentSource.SIMULATION,
    val autoPlay: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setLocationMode(mode: LocationMode)
    suspend fun setContentSource(source: ContentSource)
    suspend fun setAutoPlay(enabled: Boolean)
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val dataStore = context.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            locationMode = prefs[Keys.locationMode]
                ?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() }
                ?: LocationMode.SIMULATION,
            contentSource = prefs[Keys.contentSource]
                ?.let { runCatching { ContentSource.valueOf(it) }.getOrNull() }
                ?: ContentSource.SIMULATION,
            autoPlay = prefs[Keys.autoPlay] ?: true,
        )
    }

    override suspend fun setLocationMode(mode: LocationMode) {
        dataStore.edit { it[Keys.locationMode] = mode.name }
    }

    override suspend fun setContentSource(source: ContentSource) {
        dataStore.edit { it[Keys.contentSource] = source.name }
    }

    override suspend fun setAutoPlay(enabled: Boolean) {
        dataStore.edit { it[Keys.autoPlay] = enabled }
    }

    private object Keys {
        val locationMode = stringPreferencesKey("location_mode")
        val contentSource = stringPreferencesKey("content_source")
        val autoPlay = booleanPreferencesKey("auto_play")
    }
}

class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setLocationMode(mode: LocationMode) {
        state.update { it.copy(locationMode = mode) }
    }

    override suspend fun setContentSource(source: ContentSource) {
        state.update { it.copy(contentSource = source) }
    }

    override suspend fun setAutoPlay(enabled: Boolean) {
        state.update { it.copy(autoPlay = enabled) }
    }
}
