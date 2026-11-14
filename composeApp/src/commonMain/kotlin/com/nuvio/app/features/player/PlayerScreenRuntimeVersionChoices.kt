package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamQualityOrderRepository
import com.nuvio.app.features.streams.StreamQualityParser
import com.nuvio.app.features.streams.StreamQualityResolution
import com.nuvio.app.features.streams.StreamsUiState
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/*
 * salmaan25 fork: the player side of the in-player Quality menu and the "Other languages" part of
 * the Audio menu. The desktop controls page (player-ui/salmaan-versions.js) draws them from the
 * JSON made here and sends back "salmaanVersions", "salmaanQuality" and "salmaanLanguage".
 */

internal data class VersionChoiceLabels(
    val quality: String = "Quality",
    val qualityHint: String = "",
    val otherLanguages: String = "Other languages",
    val otherLanguagesHint: String = "",
    val searching: String = "Searching sources…",
    val noOtherQualities: String = "No other qualities found",
    val noOtherLanguages: String = "No other languages found",
    val playing: String = "Playing",
    val oneSource: String = "1 source",
    /** Contains "{count}". */
    val sourcesCount: String = "{count} sources",
    val cached: String = "cached",
)

@Composable
private fun versionChoiceLabels(): VersionChoiceLabels = VersionChoiceLabels(
    quality = stringResource(Res.string.salmaan_player_quality),
    qualityHint = stringResource(Res.string.salmaan_player_quality_hint),
    otherLanguages = stringResource(Res.string.salmaan_player_other_languages),
    otherLanguagesHint = stringResource(Res.string.salmaan_player_other_languages_hint),
    searching = stringResource(Res.string.salmaan_player_searching),
    noOtherQualities = stringResource(Res.string.salmaan_player_no_other_qualities),
    noOtherLanguages = stringResource(Res.string.salmaan_player_no_other_languages),
    playing = stringResource(Res.string.salmaan_player_playing),
    oneSource = stringResource(Res.string.salmaan_player_one_source),
    sourcesCount = stringResource(Res.string.salmaan_player_sources_count, -1).replace("-1", "{count}"),
    cached = stringResource(Res.string.salmaan_player_cached),
)

/** The JSON the controls page draws the Quality and Other languages menus from. */
@Composable
internal fun PlayerScreenRuntime.rememberVersionChoicesJson(): String {
    val sources by PlayerStreamsRepository.sourceState.collectAsStateWithLifecycle()
    val order by StreamQualityOrderRepository.state.collectAsStateWithLifecycle()
    val labels = versionChoiceLabels()
    val current = activeStream
    val identityKey = activeSourceIdentityKey
    val sourceUrl = activeSourceUrl
    val audioLanguages = audioTracks.mapNotNullTo(mutableSetOf()) { baseLanguageCode(it.language) }
    val language = contentLanguage
    return remember(sources.groups, sources.isAnyLoading, current, identityKey, sourceUrl, audioLanguages, order, language, labels) {
        runCatching {
            encodeVersionChoices(
                choices = versionChoicesNow(sources),
                loading = sources.isAnyLoading,
                labels = labels,
            )
        }.getOrDefault("")
    }
}

/** Handles the menus' commands; false for any other command. */
internal fun PlayerScreenRuntime.handleVersionChoiceEvent(type: String, value: Double): Boolean {
    when (type) {
        "salmaanVersions" -> loadVersionSources()
        "salmaanQuality" -> chooseQuality(value.toInt())
        "salmaanLanguage" -> chooseLanguage(value.toInt())
        else -> return false
    }
    return true
}

private fun PlayerScreenRuntime.loadVersionSources() {
    val videoId = activeVideoId ?: return
    PlayerStreamsRepository.loadSources(
        type = contentType ?: parentMetaType,
        videoId = videoId,
        season = activeSeasonNumber,
        episode = activeEpisodeNumber,
    )
}

private fun PlayerScreenRuntime.chooseQuality(lines: Int) {
    val resolution = StreamQualityResolution.entries.firstOrNull { it.storageId == lines.toString() } ?: return
    val choice = versionChoicesNow(PlayerStreamsRepository.sourceState.value)
        .qualities.firstOrNull { it.resolution == resolution && !it.isCurrent } ?: return
    // Keep the language playing now in the new file.
    audioTracks.firstOrNull { it.index == selectedAudioIndex }?.language
        ?.let { PlayerAudioLanguageSession.set(parentMetaId, it) }
    StreamQualityOrderRepository.moveToTop(resolution)
    switchToSource(choice.best)
}

private fun PlayerScreenRuntime.chooseLanguage(id: Int) {
    val code = PlayerVersionLanguageIds.getOrNull(id) ?: return
    val choice = versionChoicesNow(PlayerStreamsRepository.sourceState.value)
        .languages.firstOrNull { it.code == code } ?: return
    // Remembered for the show: the player's own per-show preference (later episodes, reopening)
    // and this session (so the new file starts on that language).
    updateTrackPreference { it.copy(audioLanguage = code, audioName = null, audioTrackId = null) }
    PlayerAudioLanguageSession.set(parentMetaId, code)
    switchToSource(choice.best)
}

private fun PlayerScreenRuntime.versionChoicesNow(sources: StreamsUiState): PlayerVersionChoices {
    val settings = playerSettingsUiState
    val installedAddonNames = AddonRepository.uiState.value.addons.enabledAddons().map { it.displayTitle }.toSet()
    val debrid = DebridSettingsRepository.snapshot()
    val identityKey = activeSourceIdentityKey
    val sourceUrl = activeSourceUrl
    return buildPlayerVersionChoices(
        streams = sources.groups.flatMap { it.streams },
        current = activeStream,
        isSameStream = { stream ->
            (identityKey != null && stream.playerSourceIdentityKey() == identityKey) ||
                (sourceUrl.isNotBlank() && stream.playableDirectUrl == sourceUrl)
        },
        currentAudioLanguages = audioTracks.mapNotNullTo(mutableSetOf()) { baseLanguageCode(it.language) },
        contentLanguage = contentLanguage,
        order = StreamQualityOrderRepository.current(contentLanguage, parentMetaId),
        pick = { streams, order ->
            StreamAutoPlaySelector.evaluateAutoPlayStream(
                streams = streams,
                mode = StreamAutoPlayMode.FIRST_STREAM,
                regexPattern = "",
                source = settings.streamAutoPlaySource,
                installedAddonNames = installedAddonNames,
                selectedAddons = settings.streamAutoPlaySelectedAddons,
                selectedPlugins = settings.streamAutoPlaySelectedPlugins,
                debridEnabled = debrid.canResolvePlayableLinks,
                activeResolverProviderId = debrid.activeResolverProviderId,
                qualityOrder = order,
            ).readyStreams.firstOrNull()
        },
    )
}

/** The menus' JSON: plain data and ready-made texts, so the page only lays them out. */
internal fun encodeVersionChoices(
    choices: PlayerVersionChoices,
    loading: Boolean,
    labels: VersionChoiceLabels,
): String = buildJsonObject {
    put("loading", loading)
    put("qualityLabel", labels.quality)
    put("currentQuality", choices.currentResolution?.label ?: "")
    put("qualityHint", labels.qualityHint)
    put("otherLanguagesTitle", labels.otherLanguages)
    put("otherLanguagesHint", labels.otherLanguagesHint)
    put("searchingLabel", labels.searching)
    put("noOtherQualitiesLabel", labels.noOtherQualities)
    put("noOtherLanguagesLabel", labels.noOtherLanguages)
    putJsonArray("qualities") {
        // Highest first, with the quality playing now even when no other file has it.
        StreamQualityResolution.entries
            .sortedByDescending { it.storageId.toInt() }
            .forEach { resolution ->
                val choice = choices.qualities.firstOrNull { it.resolution == resolution }
                val isCurrent = resolution == choices.currentResolution
                if (choice == null && !isCurrent) return@forEach
                addJsonObject {
                    put("id", resolution.storageId.toInt())
                    put("label", resolution.label)
                    put("current", isCurrent)
                    put(
                        "detail",
                        when {
                            isCurrent -> labels.playing
                            choice != null -> streamDetail(choice.best, choice.fileCount, labels, withResolution = false)
                            else -> ""
                        },
                    )
                }
            }
    }
    putJsonArray("languages") {
        choices.languages.forEach { choice ->
            addJsonObject {
                put("id", PlayerVersionLanguageIds.indexOf(choice.code))
                put("code", choice.code)
                put("detail", streamDetail(choice.best, choice.fileCount, labels, withResolution = true))
            }
        }
    }
}.toString()

/** E.g. "1080p · 4.2 GB · cached · 3 sources". */
internal fun streamDetail(
    stream: StreamItem,
    fileCount: Int,
    labels: VersionChoiceLabels,
    withResolution: Boolean,
): String {
    val facts = StreamQualityParser.facts(stream)
    return listOfNotNull(
        stream.qualityResolution()?.label?.takeIf { withResolution },
        facts.sizeBytes?.let(::formatGigabytes),
        labels.cached.takeIf { facts.cached == true },
        if (fileCount == 1) labels.oneSource else labels.sourcesCount.replace("{count}", fileCount.toString()),
    ).joinToString(" · ")
}

internal fun formatGigabytes(bytes: Long): String {
    val tenths = (bytes / 100_000_000.0).roundToLong()
    return "${tenths / 10}.${tenths % 10} GB"
}
