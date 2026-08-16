package se.roadcast.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.roadcast.core.database.SettingsRepository
import se.roadcast.core.network.api.ApiEndpointConfig
import javax.inject.Inject
import javax.inject.Singleton

enum class ContentSource {
    SIMULATION,
    REMOTE,
}

interface ContentSourceController {
    val source: StateFlow<ContentSource>
    val lastError: StateFlow<String?>
    val remoteConfigured: Boolean
    fun setSource(source: ContentSource)
    fun shouldUseRemote(): Boolean
    fun reportError(message: String?)
}

@Singleton
class DefaultContentSourceController @Inject constructor(
    private val endpointConfig: ApiEndpointConfig,
    private val settingsStore: SettingsRepository,
) : ContentSourceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _source = MutableStateFlow(ContentSource.SIMULATION)
    private val _lastError = MutableStateFlow<String?>(null)

    override val source: StateFlow<ContentSource> = _source.asStateFlow()
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()
    override val remoteConfigured: Boolean
        get() = endpointConfig.isConfigured

    init {
        scope.launch {
            val saved = settingsStore.settings.first()
            applySource(saved.contentSource, persist = false)
        }
    }

    override fun setSource(source: ContentSource) {
        applySource(source, persist = true)
    }

    private fun applySource(source: ContentSource, persist: Boolean) {
        if (source == ContentSource.REMOTE && !remoteConfigured) {
            _lastError.value = "Set roadcast.api.baseUrl in local.properties to enable remote APIs."
            _source.value = ContentSource.SIMULATION
            if (persist) {
                scope.launch { settingsStore.setContentSource(ContentSource.SIMULATION) }
            }
            return
        }
        _lastError.value = null
        _source.value = source
        if (persist) {
            scope.launch { settingsStore.setContentSource(source) }
        }
    }

    override fun shouldUseRemote(): Boolean =
        _source.value == ContentSource.REMOTE && remoteConfigured

    override fun reportError(message: String?) {
        _lastError.value = message
    }
}
