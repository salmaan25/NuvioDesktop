package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamQualityOrder
import com.nuvio.app.features.streams.StreamQualityParser
import com.nuvio.app.features.streams.StreamQualityResolution
import com.nuvio.app.features.streams.acceptedAudioLanguages
import com.nuvio.app.features.streams.isForeignOnly
import com.nuvio.app.features.streams.moveToTop
import com.nuvio.app.features.streams.normalized
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/*
 * salmaan25 fork: in-player Quality and "Other languages" choices.
 *
 * Both switch to another source of the same episode at the current position. A quality choice
 * becomes this device's first quality; a language choice is remembered for the show (in the
 * player's own per-show track preference, and for the rest of this session so a file switch
 * mid-episode keeps it).
 */

/** At most this many other languages are offered: the original and preferred ones, then the most common. */
internal const val MaxLanguageChoices = 6

/** Languages the in-player menu can offer; the index is the id sent by the player controls. */
internal val PlayerVersionLanguageIds: List<String> = listOf(
    "en", "ja", "ko", "hi", "es", "fr", "de", "it", "pt", "ru", "zh", "ta", "te", "ml", "tr", "ar",
    "pl", "uk", "cs", "hu", "nl", "sv", "da", "no", "fi", "el", "he", "th", "vi", "id", "ro", "bg",
)

internal fun baseLanguageCode(code: String?): String? =
    normalizeLanguageCode(code)?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotBlank() }

/** Audio language chosen for a show during this run of the app, by show id. */
internal object PlayerAudioLanguageSession {
    private val lock = SynchronizedObject()
    private val languages = HashMap<String, String>()

    fun get(metaId: String?): String? {
        if (metaId.isNullOrBlank()) return null
        return synchronized(lock) { languages[metaId] }
    }

    fun set(metaId: String?, language: String?) {
        if (metaId.isNullOrBlank()) return
        val code = baseLanguageCode(language)
        synchronized(lock) {
            if (code == null) languages.remove(metaId) else languages[metaId] = code
        }
    }
}

/** [targets] with the show's chosen language first, when there is one. */
internal fun withSessionAudioLanguage(sessionLanguage: String?, targets: List<String>): List<String> {
    val session = baseLanguageCode(sessionLanguage) ?: return targets
    return listOf(session) + targets.filterNot { baseLanguageCode(it) == session }
}

internal data class QualityChoice(
    val resolution: StreamQualityResolution,
    val fileCount: Int,
    val best: StreamItem,
    val isCurrent: Boolean,
)

internal data class LanguageChoice(
    val code: String,
    val fileCount: Int,
    val best: StreamItem,
)

internal data class PlayerVersionChoices(
    val currentResolution: StreamQualityResolution? = null,
    val qualities: List<QualityChoice> = emptyList(),
    val languages: List<LanguageChoice> = emptyList(),
)

internal fun StreamItem.qualityResolution(): StreamQualityResolution? =
    when (StreamQualityParser.facts(this).resolutionLines) {
        2160 -> StreamQualityResolution.P2160
        1440 -> StreamQualityResolution.P1440
        1080 -> StreamQualityResolution.P1080
        720 -> StreamQualityResolution.P720
        else -> null
    }

/**
 * The choices for the in-player menus.
 *
 * @param streams every source found for the episode playing now.
 * @param current the stream playing now (left out of the choices).
 * @param isSameStream whether a source is the one playing now.
 * @param currentAudioLanguages the audio languages inside the file playing now; those are picked
 *   from the Audio list itself, so they are not offered again as other versions.
 * @param contentLanguage the title's original language, when known. Sources that state no
 *   language are taken to be in it.
 * @param order this device's quality order, with the accepted audio languages for the title.
 * @param pick picks the best of some sources in a given order (auto-play's own selection, so the
 *   allowed addons and playability rules apply), or null when none can be played.
 */
internal fun buildPlayerVersionChoices(
    streams: List<StreamItem>,
    current: StreamItem?,
    isSameStream: (StreamItem) -> Boolean,
    currentAudioLanguages: Set<String>,
    contentLanguage: String?,
    order: StreamQualityOrder,
    pick: (List<StreamItem>, StreamQualityOrder) -> StreamItem?,
): PlayerVersionChoices {
    val others = streams.filterNot(isSameStream)
    val currentResolution = current?.qualityResolution()
    val normalized = order.normalized().copy(enabled = true)
    val accepted = normalized.preferredAudioLanguages

    // Quality: sources the show's accepted languages allow, grouped by resolution.
    val acceptable = others.filterNot { normalized.isForeignOnly(it) }
    val qualities = StreamQualityResolution.entries.mapNotNull { resolution ->
        val withResolution = acceptable.filter { it.qualityResolution() == resolution }
        if (withResolution.isEmpty()) return@mapNotNull null
        val best = pick(withResolution, normalized.moveToTop(resolution)) ?: return@mapNotNull null
        QualityChoice(resolution, withResolution.size, best, isCurrent = resolution == currentResolution)
    }

    // Other languages: what each source states, or the original language when it states none.
    val original = baseLanguageCode(contentLanguage)
    val inFile = currentAudioLanguages.mapNotNullTo(mutableSetOf()) { baseLanguageCode(it) }
    val byLanguage = LinkedHashMap<String, MutableList<StreamItem>>()
    others.forEach { stream ->
        val facts = StreamQualityParser.facts(stream)
        val languages = facts.languages.ifEmpty { setOfNotNull(original) }
        languages.forEach { code ->
            if (code in PlayerVersionLanguageIds && code !in inFile) {
                byLanguage.getOrPut(code) { mutableListOf() }.add(stream)
            }
        }
    }
    val languages = byLanguage.mapNotNull { (code, candidates) ->
        val languageOrder = normalized.copy(preferredAudioLanguages = acceptedAudioLanguages(listOf(code), null))
        // Sources that state the language beat ones that are only assumed to be in it.
        val stated = candidates.filter { code in StreamQualityParser.facts(it).languages }
        val best = (stated.takeIf { it.isNotEmpty() }?.let { pick(it, languageOrder) })
            ?: pick(candidates, languageOrder)
            ?: return@mapNotNull null
        LanguageChoice(code, candidates.size, best)
    }.sortedWith(
        compareBy<LanguageChoice>(
            { if (it.code == original) 0 else if (it.code in accepted) 1 else 2 },
            { -it.fileCount },
        ),
    ).take(MaxLanguageChoices)

    return PlayerVersionChoices(
        currentResolution = currentResolution,
        qualities = qualities,
        languages = languages,
    )
}
