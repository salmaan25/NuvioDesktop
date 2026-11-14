package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import com.nuvio.app.features.streams.StreamAutoPlaySource
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamQualityOrder
import com.nuvio.app.features.streams.StreamQualityOrderRepository
import com.nuvio.app.features.streams.StreamQualityResolution
import com.nuvio.app.features.streams.sort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-player Quality and Other languages (salmaan25 fork). Never touches stored settings. */
class PlayerVersionChoicesTest {
    private fun stream(
        id: String,
        name: String,
        description: String? = null,
        filename: String? = null,
        videoSize: Long? = null,
        bingeGroup: String? = null,
    ) = StreamItem(
        name = name,
        description = description,
        url = "https://example.com/$id.mkv",
        addonName = "Addon",
        addonId = "addon",
        behaviorHints = StreamBehaviorHints(filename = filename, videoSize = videoSize, bingeGroup = bingeGroup),
    )

    // The real selection uses auto-play's selector; for these tests the order alone is enough.
    private val firstInOrder: (List<StreamItem>, StreamQualityOrder) -> StreamItem? = { streams, order ->
        order.sort(streams).firstOrNull()
    }

    private fun choices(
        streams: List<StreamItem>,
        current: StreamItem?,
        inFile: Set<String> = emptySet(),
        contentLanguage: String? = null,
        accepted: Set<String> = setOf("en"),
    ) = buildPlayerVersionChoices(
        streams = streams + listOfNotNull(current),
        current = current,
        isSameStream = { it.url == current?.url },
        currentAudioLanguages = inFile,
        contentLanguage = contentLanguage,
        order = StreamQualityOrder(preferredAudioLanguages = accepted),
        pick = firstInOrder,
    )

    // --- Remembering the language for the show --------------------------------------------------

    @Test
    fun sessionLanguageGoesFirstInTheAudioTargets() {
        assertEquals(listOf("en", "hi"), withSessionAudioLanguage(null, listOf("en", "hi")))
        assertEquals(listOf("ja", "en"), withSessionAudioLanguage("jpn", listOf("en")))
        assertEquals(listOf("ja", "en"), withSessionAudioLanguage("ja", listOf("ja", "en")))
    }

    @Test
    fun sessionRemembersPerShowAndCanForget() {
        PlayerAudioLanguageSession.set("test:show-a", "jpn")
        PlayerAudioLanguageSession.set("test:show-b", "ko")
        assertEquals("ja", PlayerAudioLanguageSession.get("test:show-a"))
        assertEquals("ko", PlayerAudioLanguageSession.get("test:show-b"))
        PlayerAudioLanguageSession.set("test:show-a", null)
        assertNull(PlayerAudioLanguageSession.get("test:show-a"))
        assertNull(PlayerAudioLanguageSession.get(""))
        PlayerAudioLanguageSession.set("test:show-b", null)
    }

    // --- Quality ------------------------------------------------------------------------------

    private val playing1080 = stream("playing", "1080p", filename = "Show.S01E01.1080p.WEB-DL.mkv", videoSize = 2_000_000_000)
    private val other1080 = stream("other1080", "1080p", filename = "Show.S01E01.1080p.BluRay.mkv", videoSize = 4_000_000_000)
    private val big4k = stream("big4k", "2160p", filename = "Show.S01E01.2160p.WEB-DL.mkv", videoSize = 12_000_000_000)
    private val small4k = stream("small4k", "2160p", filename = "Show.S01E01.2160p.WEB-DL.x265.mkv", videoSize = 4_500_000_000)
    private val latino4k = stream("latino4k", "2160p", description = "🌐 🇪🇸", filename = "Show.S01E01.2160p.Latino.mkv", videoSize = 3_000_000_000)
    private val p720 = stream("p720", "720p", filename = "Show.S01E01.720p.WEB-DL.mkv", videoSize = 900_000_000)

    @Test
    fun offersEachOtherResolutionWithItsBestFile() {
        val result = choices(listOf(big4k, small4k, other1080, p720), current = playing1080)
        assertEquals(StreamQualityResolution.P1080, result.currentResolution)
        assertEquals(
            listOf(StreamQualityResolution.P2160, StreamQualityResolution.P1080, StreamQualityResolution.P720),
            result.qualities.map { it.resolution },
        )
        val fourK = result.qualities.first { it.resolution == StreamQualityResolution.P2160 }
        assertEquals(big4k, fourK.best, "for 4K the 5 GB (1080p) budget is 20 GB, so the better 12 GB file wins")
        assertEquals(2, fourK.fileCount)
        assertTrue(result.qualities.first { it.resolution == StreamQualityResolution.P1080 }.isCurrent)
        assertEquals(other1080, result.qualities.first { it.resolution == StreamQualityResolution.P1080 }.best)
    }

    @Test
    fun neverOffersTheFilePlayingNowOrForeignOnlyFiles() {
        val result = choices(listOf(latino4k), current = playing1080)
        assertTrue(result.qualities.none { it.best == playing1080 })
        assertTrue(result.qualities.isEmpty(), "the only 4K is Spanish-only, so no 4K choice")
    }

    @Test
    fun nothingToOfferWithoutSources() {
        val result = choices(emptyList(), current = playing1080)
        assertTrue(result.qualities.isEmpty())
        assertTrue(result.languages.isEmpty())
    }

    // --- Other languages ------------------------------------------------------------------------

    private val englishDubPlaying = stream("dub", "1080p", description = "🎙️ 🇬🇧", filename = "Frieren.S01E05.1080p.WEB-DL.ENG.mkv", videoSize = 1_400_000_000)
    private val japaneseStated = stream("jp", "1080p", description = "🎙️ 🇯🇵", filename = "Frieren.S01E05.1080p.WEB-DL.mkv", videoSize = 1_300_000_000)
    private val unlabelled = stream("sp", "1080p", filename = "[SubsPlease] Frieren - 05 (1080p).mkv", videoSize = 1_400_000_000)
    private val koreanOnly = stream("ko", "1080p", description = "🎙️ 🇰🇷", filename = "Frieren.S01E05.1080p.KOR.mkv", videoSize = 1_400_000_000)
    private val anotherEnglish = stream("en2", "1080p", description = "🎙️ 🇬🇧", filename = "Frieren.S01E05.1080p.BluRay.ENG.mkv", videoSize = 2_000_000_000)

    @Test
    fun offersLanguagesTheFilePlayingNowDoesNotHave() {
        val result = choices(
            listOf(japaneseStated, unlabelled, koreanOnly, anotherEnglish),
            current = englishDubPlaying,
            inFile = setOf("eng"),
            contentLanguage = "ja",
        )
        assertEquals(listOf("ja", "ko"), result.languages.map { it.code }, "the original language first; English is already playing")
        val japanese = result.languages.first { it.code == "ja" }
        assertEquals(2, japanese.fileCount, "the unlabelled release counts as the original language")
        assertEquals(japaneseStated, japanese.best, "a file that states Japanese beats one only assumed to be")
    }

    @Test
    fun offersAtMostSixLanguagesOriginalAndPreferredFirst() {
        val codes = listOf("fr", "es", "it", "de", "ru", "pl", "tr", "hi")
        val dubs = codes.flatMapIndexed { index, code ->
            // More files for the earlier languages, so the count order is fr, es, it, ...
            (0..(codes.size - index)).map { n -> stream("$code$n", "1080p", description = "🎙️ $code", filename = "Show.S01E01.1080p.${code.uppercase()}.$n.mkv", videoSize = 1_000_000_000) }
        }
        val languageNames = mapOf("fr" to "French", "es" to "Spanish", "it" to "Italian", "de" to "German", "ru" to "Russian", "pl" to "Polish", "tr" to "Turkish", "hi" to "Hindi")
        val named = dubs.map { it.copy(description = "🎙️ " + languageNames.getValue(it.url!!.substringAfterLast('/').take(2))) }
        val result = choices(named + japaneseStated, current = englishDubPlaying, inFile = setOf("en"), contentLanguage = "ja", accepted = setOf("en", "hi"))
        assertEquals(MaxLanguageChoices, result.languages.size)
        assertEquals("ja", result.languages[0].code, "original first")
        assertEquals("hi", result.languages[1].code, "then a preferred language, though it has the fewest files")
        assertEquals(listOf("fr", "es", "it", "de"), result.languages.drop(2).map { it.code })
    }

    @Test
    fun unlabelledFilesAreOnlyAssumedToBeInTheOriginalLanguageWhenItIsKnown() {
        val result = choices(listOf(unlabelled), current = englishDubPlaying, inFile = setOf("en"), contentLanguage = null)
        assertTrue(result.languages.isEmpty())
    }

    @Test
    fun languagesTheMenuCannotNameAreLeftOut() {
        val swahili = stream("sw", "1080p", filename = "Film.2024.1080p.mkv", videoSize = 1_000_000_000)
        val result = choices(listOf(swahili), current = englishDubPlaying, inFile = setOf("en"), contentLanguage = "sw")
        assertTrue(result.languages.isEmpty())
    }

    // --- What the controls page receives ---------------------------------------------------------

    @Test
    fun jsonCarriesRowsIdsAndTexts() {
        val result = choices(listOf(big4k, small4k, japaneseStated), current = playing1080, inFile = setOf("en"), contentLanguage = "ja")
        val json = Json.parseToJsonElement(encodeVersionChoices(result, loading = true, labels = VersionChoiceLabels())).jsonObject
        assertTrue(json.getValue("loading").jsonPrimitive.boolean)
        assertEquals("1080p", json.getValue("currentQuality").jsonPrimitive.content)
        val qualities = json.getValue("qualities").jsonArray.map { it.jsonObject }
        assertEquals(listOf(2160, 1080), qualities.map { it.getValue("id").jsonPrimitive.int })
        assertEquals(listOf(false, true), qualities.map { it.getValue("current").jsonPrimitive.boolean })
        assertEquals("Playing", qualities[1].getValue("detail").jsonPrimitive.content, "the playing quality is listed even with no other 1080p file")
        assertEquals("12.0 GB · 2 sources", qualities[0].getValue("detail").jsonPrimitive.content)
        val languages = json.getValue("languages").jsonArray.map { it.jsonObject }
        assertEquals("ja", languages.single().getValue("code").jsonPrimitive.content)
        assertEquals(PlayerVersionLanguageIds.indexOf("ja"), languages.single().getValue("id").jsonPrimitive.int)
        // The two unlabelled 4K files count as the original language (Japanese) too.
        assertEquals("1080p · 1.3 GB · 3 sources", languages.single().getValue("detail").jsonPrimitive.content)
    }

    @Test
    fun streamDetailMentionsCachedFiles() {
        val cached = stream("c", "[TB⚡] Comet 1080p", videoSize = 3_400_000_000)
        assertEquals("1080p · 3.4 GB · cached · 3 sources", streamDetail(cached, 3, VersionChoiceLabels(), withResolution = true))
    }

    @Test
    fun formatsGigabytes() {
        assertEquals("4.2 GB", formatGigabytes(4_200_000_000))
        assertEquals("0.9 GB", formatGigabytes(900_000_000))
        assertEquals("12.0 GB", formatGigabytes(12_000_000_000))
    }

    // --- The chosen language beats "same release" and "Reuse Last Link" ---------------------------

    @Test
    fun nextEpisodeSkipsTheSameReleaseWhenItIsOnlyInAnotherLanguage() {
        val sameReleaseEnglish = stream("dub-e6", "1080p", description = "🎙️ 🇬🇧", filename = "Frieren.S01E06.1080p.ENG.mkv", videoSize = 1_400_000_000, bingeGroup = "dub-group")
        val japanese = stream("jp-e6", "1080p", description = "🎙️ 🇯🇵", filename = "Frieren.S01E06.1080p.mkv", videoSize = 1_300_000_000)
        fun pick(order: StreamQualityOrder?) = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(sameReleaseEnglish, japanese),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = "dub-group",
            preferBingeGroupInSelection = true,
            qualityOrder = order,
        )
        assertEquals(japanese, pick(StreamQualityOrder(preferredAudioLanguages = setOf("ja"))))
        // Without a language choice the same release still wins, as before.
        assertEquals(sameReleaseEnglish, pick(null))
        assertEquals(sameReleaseEnglish, pick(StreamQualityOrder(preferredAudioLanguages = setOf("en", "ja"))))
    }

    @Test
    fun aRememberedLinkInAnotherLanguageIsNotReused() {
        val show = "test:reuse-show"
        PlayerAudioLanguageSession.set(show, "ja")
        try {
            assertTrue(StreamQualityOrderRepository.reusedLinkConflictsWithShowLanguage(show, "[TB+] Torrentio", "🇬🇧", "Frieren.S01E05.ENG.mkv"))
            assertFalse(StreamQualityOrderRepository.reusedLinkConflictsWithShowLanguage(show, "1080p", null, "[SubsPlease] Frieren - 05 (1080p).mkv"))
            assertFalse(StreamQualityOrderRepository.reusedLinkConflictsWithShowLanguage(show, "1080p", "🎙️ 🇯🇵 🇬🇧", "Frieren.S01E05.Dual.mkv"))
        } finally {
            PlayerAudioLanguageSession.set(show, null)
        }
    }
}
