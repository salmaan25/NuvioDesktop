package com.nuvio.app.features.streams

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.resolveContentLanguage
import com.nuvio.app.features.player.resolvePreferredAudioLanguageTargets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * salmaan25 fork: where this device keeps its [StreamQualityOrder]. Stored for the whole device
 * (every profile) and never synced, because the right quality depends on the screen.
 */
internal expect object StreamQualityOrderStorage {
    fun load(): String?
    fun save(value: String)
}

object StreamQualityOrderRepository {
    private val _state = MutableStateFlow(StreamQualityOrder())
    private var loaded = false

    val state: StateFlow<StreamQualityOrder>
        get() {
            ensureLoaded()
            return _state.asStateFlow()
        }

    /**
     * The order to use now, with the audio languages that are not "foreign": the preferred audio
     * languages from the player settings plus [contentLanguage], the title's original language
     * when known (so a Japanese or Korean original is never pushed behind a dub).
     */
    fun current(contentLanguage: String? = null): StreamQualityOrder {
        ensureLoaded()
        return _state.value.copy(preferredAudioLanguages = acceptedLanguages(contentLanguage))
    }

    /** [current] for a title, taking its original language from its details when they are loaded. */
    fun currentForTitle(type: String, metaId: String?): StreamQualityOrder {
        val contentLanguage = metaId
            ?.let { id -> runCatching { MetaDetailsRepository.peek(type = type, id = id) }.getOrNull() }
            ?.let { meta -> resolveContentLanguage(meta.language, meta.country) }
        return current(contentLanguage)
    }

    private fun acceptedLanguages(contentLanguage: String?): Set<String> = runCatching {
        val settings = PlayerSettingsRepository.uiState.value
        acceptedAudioLanguages(
            preferredTargets = resolvePreferredAudioLanguageTargets(
                preferredAudioLanguage = settings.preferredAudioLanguage,
                secondaryPreferredAudioLanguage = settings.secondaryPreferredAudioLanguage,
                deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
                contentOriginalLanguage = contentLanguage,
            ),
            contentLanguage = contentLanguage,
        )
    }.getOrDefault(emptySet())

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun move(resolution: StreamQualityResolution, up: Boolean) = update { it.move(resolution, up) }

    fun moveToTop(resolution: StreamQualityResolution) = update { it.moveToTop(resolution) }

    /** [gigabytes] null turns "smaller files first" off. */
    fun setSmallFilesFirstMaxGb(gigabytes: Int?) = update { it.copy(smallFilesFirstMaxGb = gigabytes) }

    private fun update(change: (StreamQualityOrder) -> StreamQualityOrder) {
        ensureLoaded()
        val next = change(_state.value).normalized()
        if (next == _state.value) return
        _state.value = next
        runCatching { StreamQualityOrderStorage.save(next.encode()) }
    }

    // Loading twice at the same moment is harmless: both read the same stored text.
    private fun ensureLoaded() {
        if (loaded) return
        _state.value = decodeStreamQualityOrder(runCatching { StreamQualityOrderStorage.load() }.getOrNull())
        loaded = true
    }
}
