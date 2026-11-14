package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Quality order (salmaan25 fork). The parser cases use labels exactly as AIOStreams, Comet,
 * MediaFusion, StremThru and Torrentio sent them on 2026-11-14.
 */
class StreamQualityOrderTest {
    private fun stream(
        id: String,
        name: String? = null,
        description: String? = null,
        filename: String? = null,
        videoSize: Long? = null,
        addonName: String = "Addon",
        bingeGroup: String? = null,
    ) = StreamItem(
        name = name,
        description = description,
        url = "https://example.com/$id.mkv",
        addonName = addonName,
        addonId = addonName.lowercase(),
        behaviorHints = StreamBehaviorHints(filename = filename, videoSize = videoSize, bingeGroup = bingeGroup),
    )

    private fun facts(stream: StreamItem) = StreamQualityParser.facts(stream)

    // --- Reading addon labels -------------------------------------------------------------

    @Test
    fun readsAioStreamsLabels() {
        val f = facts(
            stream(
                "aio", name = "1080p",
                description = "S05E14\nTB | ⬇️ 12 | 2.2 GB\nEnglish, Hindi\n★★★★",
                filename = "Breaking Bad - S05E14 - Ozymandias 1080p SDR [hin, eng].mkv",
                videoSize = 2_201_170_739,
            ),
        )
        assertEquals(1080, f.resolutionLines)
        assertEquals(2_201_170_739, f.sizeBytes)
        assertNull(f.cached, "AIOStreams' ⬇️ is a seeder count, not 'not cached'")
        assertFalse(f.isCinemaRecording)
    }

    @Test
    fun readsCometMediaFusionAndStremThruLabels() {
        val comet = facts(
            stream(
                "comet", name = "[TB⚡] Comet 2160p",
                description = "📄 Breaking.Bad.S05E14.Ozymandias.2160p.REPACK.NF.WEB-DL.DDP5.1.H.265-XEBEC.mkv\n💾 5.6 GB 🔎 StremThru",
                filename = "Breaking.Bad.S05E14.Ozymandias.2160p.REPACK.NF.WEB-DL.DDP5.1.H.265-XEBEC.mkv",
                videoSize = 6_030_789_613,
            ),
        )
        assertEquals(2160, comet.resolutionLines)
        assertEquals(true, comet.cached)

        val mediaFusion = facts(
            stream(
                "mf", name = "MediaFusion | ElfHosted 🧲 TRB ⚡️ 1080p",
                description = "📺 BluRay 🎞️ hevc \n📦 11.95 GB 👤 173 ▶️ 70\n🔗 Torlock",
                filename = "Breaking.Bad.S05E14.1080p.BluRay.x265-RARBG.mp4",
                videoSize = 12_830_318_753,
            ),
        )
        assertEquals(1080, mediaFusion.resolutionLines)
        assertEquals(true, mediaFusion.cached)
        assertEquals(12_830_318_753, mediaFusion.sizeBytes)

        val stremThruSd = facts(
            stream("st1", name = "⚡️ [TB]\nTorz", filename = "breaking.bad.s05e14.480p.hdtv.x264-encodeking.mkv", videoSize = 178_516_102),
        )
        assertEquals(480, stremThruSd.resolutionLines)
        assertEquals(true, stremThruSd.cached)

        val stremThruUnknown = facts(
            stream("st2", name = "⚡️ [TB]\nTorz", filename = "Breaking Bad S05E14 Ozymandias.mp4", videoSize = 147_771_247),
        )
        assertNull(stremThruUnknown.resolutionLines)
    }

    @Test
    fun readsCachedAndNotCachedMarkers() {
        assertEquals(true, facts(stream("t1", name = "[TB+] Torrentio\n4k")).cached)
        assertEquals(2160, facts(stream("t1", name = "[TB+] Torrentio\n4k")).resolutionLines)
        assertEquals(false, facts(stream("t2", name = "[TB download] Torrentio\n1080p")).cached)
        assertEquals(false, facts(stream("c", name = "[TB⬇️] Comet 1080p")).cached)
        assertEquals(false, facts(stream("m", name = "MediaFusion ⏳ 1080p")).cached)
    }

    @Test
    fun readsSizeFromTextWhenTheAddonGivesNoNumber() {
        assertEquals(4_200_000_000, facts(stream("a", name = "1080p", description = "💾 4.2 GB")).sizeBytes)
        assertEquals(850_000_000, facts(stream("b", name = "720p", description = "850 MB")).sizeBytes)
        assertEquals(1_500_000_000, facts(stream("c", name = "720p", description = "1,5 GB")).sizeBytes)
        assertNull(facts(stream("d", name = "720p", description = "no size here")).sizeBytes)
    }

    @Test
    fun theAddonsOwnResolutionLabelWinsOverTheDescription() {
        val f = facts(stream("x", name = "1080p", description = "Also on 4K HDR"))
        assertEquals(1080, f.resolutionLines)
    }

    @Test
    fun recognisesCinemaRecordingsOnly() {
        assertTrue(facts(stream("a", name = "1080p", filename = "Movie.2026.1080p.HDTS.x264.mkv")).isCinemaRecording)
        assertTrue(facts(stream("b", name = "CAM 720p")).isCinemaRecording)
        assertTrue(facts(stream("c", name = "1080p", filename = "Movie.2026.TELESYNC.mkv")).isCinemaRecording)
        assertFalse(facts(stream("d", name = "1080p", filename = "Movie.2026.1080p.WEB-DL.DDP5.1.mkv")).isCinemaRecording)
        assertFalse(facts(stream("e", name = "1080p", filename = "Movie.2020.1080p.BluRay.DTS-HD.MA.5.1.mkv")).isCinemaRecording)
        assertFalse(facts(stream("f", name = "1080p", filename = "The.Cameraman.1928.1080p.mkv")).isCinemaRecording)
    }

    // --- Ordering -----------------------------------------------------------------------------

    private val big1080 = stream("big1080", name = "1080p", videoSize = 20_000_000_000)
    private val small4k = stream("small4k", name = "2160p", videoSize = 4_000_000_000)
    private val small1080 = stream("small1080", name = "1080p", videoSize = 3_000_000_000)
    private val p720 = stream("p720", name = "720p", videoSize = 1_000_000_000)
    private val p1440 = stream("p1440", name = "1440p", videoSize = 6_000_000_000)
    private val unknown = stream("unknown", name = "Torz", videoSize = 500_000_000)
    private val cam = stream("cam", name = "1080p", filename = "New.Movie.2026.1080p.HDCAM.mkv", videoSize = 1_000_000_000)
    private val mixed = listOf(big1080, small4k, small1080, p720, p1440, unknown, cam)

    @Test
    fun defaultOrderIsSmall1080pThen1080p1440p720p4kThenTheRest() {
        assertEquals(
            listOf(small1080, big1080, p1440, p720, small4k, unknown, cam).map { it.url },
            StreamQualityOrder().sort(mixed).map { it.url },
        )
    }

    @Test
    fun exactlyFiveGigabytesStillCountsAsSmall() {
        val five = stream("five", name = "1080p", videoSize = 5_000_000_000)
        val justOver = stream("over", name = "1080p", videoSize = 5_000_000_001)
        assertEquals(listOf(five, justOver), StreamQualityOrder().sort(listOf(justOver, five)))
    }

    @Test
    fun unknownSizeIsNotTreatedAsSmall() {
        val noSize = stream("nosize", name = "1080p")
        assertEquals(listOf(small1080, noSize), StreamQualityOrder().sort(listOf(noSize, small1080)))
    }

    @Test
    fun withinAStepKnownCachedFirstThenUnstatedThenNotCached() {
        val notCached = stream("nc", name = "[TB download] 1080p", videoSize = 2_000_000_000)
        val unstated = stream("un", name = "1080p", videoSize = 2_000_000_000)
        val cached = stream("ca", name = "[TB⚡] 1080p", videoSize = 2_000_000_000)
        assertEquals(listOf(cached, unstated, notCached), StreamQualityOrder().sort(listOf(notCached, unstated, cached)))
    }

    @Test
    fun equalStreamsKeepTheirOriginalOrder() {
        val a = stream("a", name = "1080p", videoSize = 2_000_000_000, addonName = "AIOStreams")
        val b = stream("b", name = "1080p", videoSize = 2_000_000_000, addonName = "Comet")
        val c = stream("c", name = "1080p", videoSize = 2_000_000_000, addonName = "MediaFusion")
        assertEquals(listOf(a, b, c), StreamQualityOrder().sort(listOf(a, b, c)))
        assertEquals(listOf(c, b, a), StreamQualityOrder().sort(listOf(c, b, a)))
    }

    @Test
    fun upToTheLimitTheLargestComesFirstAboveItTheSmallest() {
        val s1 = stream("s1", name = "1080p", videoSize = 1_000_000_000)
        val s4 = stream("s4", name = "1080p", videoSize = 4_600_000_000)
        val s2 = stream("s2", name = "1080p", videoSize = 2_300_000_000)
        val b9 = stream("b9", name = "1080p", videoSize = 9_000_000_000)
        val b30 = stream("b30", name = "1080p", videoSize = 30_000_000_000)
        val b12 = stream("b12", name = "1080p", videoSize = 12_000_000_000)
        assertEquals(
            listOf(s4, s2, s1, b9, b12, b30).map { it.url },
            StreamQualityOrder().sort(listOf(s1, b30, s4, b12, s2, b9)).map { it.url },
        )
    }

    // --- Languages and release quality (cases from the live check on 2026-11-14) ----------------

    private val english = setOf("en")

    @Test
    fun readsStatedAudioLanguages() {
        assertEquals(setOf("en", "ru", "uk"), facts(stream("a", name = "1080p", description = "TB | ⬇️ 179 | 26.04 GB\nEnglish, Russian, Ukrainian\n★★★★")).languages)
        assertEquals(setOf("es"), facts(stream("b", name = "⚡️ [TB]\nTorz\n1080p", description = "📦 2.2 GB\n🌐 🇪🇸\n📄 Sinners (2025) [1080p][Latino].mp4")).languages)
        assertEquals(
            setOf("es", "en"),
            facts(stream("c", name = "⚡️ [TB]\nTorz\n1080p", description = "🎙️ 🇪🇸 🇬🇧\n💬 🇪🇸 🇬🇧\n⚙️ Lat", filename = "Pecadores.2025.1080P-Dual-Lat.mkv")).languages,
        )
        assertEquals(setOf("it"), facts(stream("d", name = "1080p", description = "🎙️ 🇮🇹\n💬 🇬🇧")).languages, "subtitle flags are not audio")
        assertEquals(setOf("ru"), facts(stream("e", name = "1080p", filename = "Начало.2010.UHD.BDRip.1080p.mkv")).languages)
        // Chinese, Japanese and Korean titles usually mean subtitles, not a dub.
        assertEquals(emptySet(), facts(stream("e2", name = "1080p", filename = "오펜하이머 Oppenheimer,2023.1080p.KORSUB.WEBRip.mkv")).languages)
        assertEquals(emptySet(), facts(stream("e3", name = "4K", filename = "[47BT][鱿鱼游戏 第一季]Squid.Game.S01E01.2160p.mkv")).languages)
        assertEquals(emptySet(), facts(stream("e4", name = "1080p", filename = "Начало.2010.1080p.SUB.mkv")).languages)
        assertEquals(setOf("it", "en", "es"), facts(stream("f", name = "1080p", filename = "Breaking.Bad.S05e14.Ita.Eng.Spa.1080p.h265.mkv")).languages)
        assertTrue(facts(stream("g", name = "1080p", filename = "Inception (2010) MULTi VF2 [1080p] BluRay x264-PopHD.mkv")).multiAudio)
        assertTrue(facts(stream("h", name = "1080p", filename = "Pecadores.2025.1080P-Dual-Lat.mkv")).multiAudio)
        assertEquals(emptySet(), facts(stream("i", name = "1080p", filename = "Breaking.Bad.S05E14.1080p.BluRay.x265-RARBG.mp4")).languages)
    }

    @Test
    fun recognisesProperReleases() {
        assertTrue(facts(stream("a", name = "1080p", filename = "Show.S01E01.1080p.AMZN.WEB-DL.DDP5.1.H.264.mkv")).properRelease)
        assertTrue(facts(stream("b", name = "⚡️ [TB]\nTorz", description = "💿 WEB 🎞️ HEVC")).properRelease)
        assertTrue(facts(stream("c", name = "1080p", filename = "Breaking.Bad.S05E14.Ozymandias.1080p.WEBRip.10Bit.HEVC-d3g.mkv")).properRelease)
        assertTrue(facts(stream("d", name = "1080p", filename = "Inception.2010.1080p.BluRay.x264.mkv")).properRelease)
        assertFalse(facts(stream("e", name = "⚡️ [TB]\nTorz", filename = "A Origem (2010) 1080p - 210GJI.mp4")).properRelease)
        assertFalse(facts(stream("f", name = "1080p", filename = "Oppenheimer (2023) [FHD - AVC]_[EAC3 - 5.1].mkv")).properRelease)
    }

    @Test
    fun burnedInSubtitlesGoAfterCleanFilesInTheSameStep() {
        // Live case, Oppenheimer 2026-11-14: a 4.4 GB KORSUB WEBRip must not beat a clean 4.2 GB BluRay.
        val korsub = stream("korsub", name = "⚡️ [TB]\nTorz", filename = "오펜하이머 Oppenheimer,2023.1080p.KORSUB.WEBRip.H264.AAC.mkv", videoSize = 4_400_000_000)
        val clean = stream("clean", name = "⚡️ [TB]\nTorz", filename = "Oppenheimer.2023.bluray.sdr.movie.1080p.av1.5.1.opus.4500mb.mkv", videoSize = 4_200_000_000)
        assertTrue(facts(korsub).hardcodedSubtitles)
        assertFalse(facts(clean).hardcodedSubtitles)
        assertTrue(facts(stream("hc", name = "1080p", filename = "Movie.2024.1080p.HC.HDRip.x264.mkv")).hardcodedSubtitles)
        assertFalse(facts(stream("subs", name = "1080p", description = "💬 🇬🇧", filename = "Movie.2024.1080p.WEB-DL.mkv")).hardcodedSubtitles)
        assertEquals(listOf(clean, korsub), StreamQualityOrder(preferredAudioLanguages = english).sort(listOf(korsub, clean)))
    }

    @Test
    fun foreignOnlyStreamsGoAfterEveryStreamInThePreferredLanguage() {
        val latinoSmall = stream("latino", name = "1080p", description = "🌐 🇪🇸", filename = "Sinners (2025) [1080p][Latino].mp4", videoSize = 2_200_000_000)
        val englishBig = stream("en-big", name = "1080p", filename = "Sinners.2025.1080p.WEB-DL.mkv", videoSize = 9_000_000_000)
        val english720 = stream("en-720", name = "720p", filename = "Sinners.2025.720p.WEB-DL.mkv", videoSize = 1_500_000_000)
        val order = StreamQualityOrder(preferredAudioLanguages = english)
        assertEquals(listOf(englishBig, english720, latinoSmall), order.sort(listOf(latinoSmall, english720, englishBig)))
    }

    @Test
    fun dualAudioWithThePreferredLanguageIsNotForeign() {
        val dual = stream("dual", name = "1080p", description = "🎙️ 🇪🇸 🇬🇧", filename = "Pecadores.2025.1080P-Dual-Lat.mkv", videoSize = 2_200_000_000)
        val englishBig = stream("en-big", name = "1080p", filename = "Sinners.2025.1080p.WEB-DL.mkv", videoSize = 9_000_000_000)
        assertEquals(dual, StreamQualityOrder(preferredAudioLanguages = english).sort(listOf(englishBig, dual)).first())
    }

    @Test
    fun withinAStepProperReleasesThenCleanLanguageThenSize() {
        val unlabelled = stream("unlabelled", name = "1080p", filename = "A Origem (2010) 1080p - 210GJI.mp4", description = "🌐 🇵🇹 🇬🇧", videoSize = 4_900_000_000)
        val multiBluRay = stream("multi", name = "1080p", filename = "Inception (2010) MULTi VF2 [1080p] BluRay x264-PopHD.mkv", videoSize = 4_600_000_000)
        val englishBluRay = stream("english", name = "1080p", filename = "Inception.2010.1080p.BluRay.x264.mkv", videoSize = 3_100_000_000)
        val order = StreamQualityOrder(preferredAudioLanguages = english)
        assertEquals(listOf(englishBluRay, multiBluRay, unlabelled), order.sort(listOf(unlabelled, multiBluRay, englishBluRay)))
    }

    @Test
    fun withoutAPreferredLanguageLanguagesDoNotMatter() {
        val latino = stream("latino", name = "1080p", description = "🌐 🇪🇸", filename = "Sinners.2025.1080p.WEB-DL.Latino.mkv", videoSize = 4_000_000_000)
        val english = stream("english", name = "1080p", filename = "Sinners.2025.1080p.WEB-DL.mkv", videoSize = 3_000_000_000)
        assertEquals(listOf(latino, english), StreamQualityOrder().sort(listOf(english, latino)))
    }

    @Test
    fun theTitlesOriginalLanguageIsNeverForeign() {
        assertEquals(setOf("en", "ja"), acceptedAudioLanguages(listOf("en"), "ja"))
        assertEquals(setOf("en"), acceptedAudioLanguages(listOf("en"), null))
        assertEquals(setOf("ja"), acceptedAudioLanguages(listOf("ja"), "ja"))
        assertEquals(setOf("pt"), acceptedAudioLanguages(listOf("pt-BR"), null))
        assertEquals(emptySet(), acceptedAudioLanguages(emptyList(), null))
    }

    private val japaneseOnly = stream("jp", name = "1080p", description = "🎙️ 🇯🇵", filename = "Frieren.S01E01.1080p.WEB-DL.JPN.mkv", videoSize = 1_400_000_000)
    private val englishDub = stream("dub", name = "1080p", description = "🎙️ 🇬🇧", filename = "Frieren.S01E01.1080p.WEB-DL.ENG.mkv", videoSize = 1_400_000_000)

    @Test
    fun animeWithEnglishPreferenceKeepsTheJapaneseOriginalInPlay() {
        val order = StreamQualityOrder(preferredAudioLanguages = acceptedAudioLanguages(listOf("en"), "ja"))
        assertEquals(listOf(japaneseOnly, englishDub), order.sort(listOf(japaneseOnly, englishDub)))
        // Without knowing the title's language, the Japanese original would have been pushed back.
        val withoutTitleLanguage = StreamQualityOrder(preferredAudioLanguages = acceptedAudioLanguages(listOf("en"), null))
        assertEquals(listOf(englishDub, japaneseOnly), withoutTitleLanguage.sort(listOf(japaneseOnly, englishDub)))
    }

    @Test
    fun originalAudioPreferencePushesDubsBack() {
        // Player setting "Original": the targets are the title's own language.
        val order = StreamQualityOrder(preferredAudioLanguages = acceptedAudioLanguages(listOf("ja"), "ja"))
        assertEquals(listOf(japaneseOnly, englishDub), order.sort(listOf(englishDub, japaneseOnly)))
    }

    @Test
    fun regionalVariantsOfThePreferredLanguageCount() {
        val brazilian = stream("br", name = "1080p", description = "🌐 🇧🇷", filename = "Filme.2024.1080p.WEB-DL.mkv", videoSize = 4_000_000_000)
        val english = stream("en", name = "1080p", description = "🌐 🇬🇧", filename = "Film.2024.1080p.WEB-DL.mkv", videoSize = 3_000_000_000)
        val order = StreamQualityOrder(preferredAudioLanguages = setOf("pt-BR"))
        assertEquals(brazilian, order.sort(listOf(english, brazilian)).first())
    }

    @Test
    fun turnedOffKeepsTheListAsItIs() {
        assertSame(mixed, StreamQualityOrder(enabled = false).sort(mixed))
    }

    @Test
    fun smallFilesFirstOffKeepsAll1080pTogetherInOriginalOrder() {
        val order = StreamQualityOrder(smallFilesFirstMaxGb = null)
        assertEquals(
            listOf(big1080, small1080, p1440, p720, small4k, unknown, cam).map { it.url },
            order.sort(mixed).map { it.url },
        )
    }

    @Test
    fun aTvLikeOrderPrefers4k() {
        val big4k = stream("big4k", name = "4K", videoSize = 40_000_000_000)
        // 5 GB for 1080p is 20 GB for 4K.
        val order = StreamQualityOrder().moveToTop(StreamQualityResolution.P2160)
        assertEquals(
            listOf(small4k, big4k, big1080),
            order.sort(listOf(big1080, big4k, small4k)),
        )
    }

    @Test
    fun theSizeLimitIsFor1080pAndScalesWithPictureSize() {
        assertEquals("5", StreamQualityOrder().smallFilesFirstMaxGbText())
        assertEquals("20", StreamQualityOrder().moveToTop(StreamQualityResolution.P2160).smallFilesFirstMaxGbText())
        assertEquals("8.9", StreamQualityOrder().moveToTop(StreamQualityResolution.P1440).smallFilesFirstMaxGbText())
        assertEquals("2.2", StreamQualityOrder().moveToTop(StreamQualityResolution.P720).smallFilesFirstMaxGbText())
        assertEquals(null, StreamQualityOrder(smallFilesFirstMaxGb = null).smallFilesFirstMaxGbText())
        val order720 = StreamQualityOrder().moveToTop(StreamQualityResolution.P720)
        val light = stream("light", name = "720p", videoSize = 2_000_000_000)
        val heavy = stream("heavy", name = "720p", videoSize = 4_900_000_000)
        assertEquals(listOf(light, heavy), order720.sort(listOf(heavy, light)), "a 4.9 GB 720p file is not 'small'")
        val order4k = StreamQualityOrder().moveToTop(StreamQualityResolution.P2160)
        val fine4k = stream("fine4k", name = "2160p", videoSize = 18_000_000_000)
        val huge4k = stream("huge4k", name = "2160p", videoSize = 60_000_000_000)
        assertEquals(listOf(fine4k, huge4k), order4k.sort(listOf(huge4k, fine4k)))
        assertEquals("4K up to 20 GB → 4K → 1080p → 1440p → 720p", order4k.summary { "up to $it GB" })
    }

    // --- Editing, saving and showing the order ------------------------------------------------------

    @Test
    fun movingUpAndDown() {
        val order = StreamQualityOrder()
        assertEquals(
            listOf(StreamQualityResolution.P1440, StreamQualityResolution.P1080, StreamQualityResolution.P720, StreamQualityResolution.P2160),
            order.move(StreamQualityResolution.P1440, up = true).resolutions,
        )
        assertEquals(order, order.move(StreamQualityResolution.P1080, up = true), "the first cannot move up")
        assertEquals(order, order.move(StreamQualityResolution.P2160, up = false), "the last cannot move down")
        assertEquals(
            listOf(StreamQualityResolution.P720, StreamQualityResolution.P1080, StreamQualityResolution.P1440, StreamQualityResolution.P2160),
            order.moveToTop(StreamQualityResolution.P720).resolutions,
        )
    }

    @Test
    fun savesAndReadsBack() {
        val order = StreamQualityOrder(
            enabled = false,
            resolutions = listOf(StreamQualityResolution.P2160, StreamQualityResolution.P1080, StreamQualityResolution.P720, StreamQualityResolution.P1440),
            smallFilesFirstMaxGb = null,
        )
        assertEquals("v1;on=0;order=2160,1080,720,1440;small=0", order.encode())
        assertEquals(order, decodeStreamQualityOrder(order.encode()))
        assertEquals(StreamQualityOrder(), decodeStreamQualityOrder(StreamQualityOrder().encode()))
    }

    @Test
    fun unreadableOrPartialSavedTextFallsBackSafely() {
        assertEquals(StreamQualityOrder(), decodeStreamQualityOrder(null))
        assertEquals(StreamQualityOrder(), decodeStreamQualityOrder(""))
        assertEquals(StreamQualityOrder(), decodeStreamQualityOrder("garbage"))
        assertEquals(StreamQualityOrder(), decodeStreamQualityOrder("v1;order=abc;small=x"))
        val partial = decodeStreamQualityOrder("v1;order=2160,2160")
        assertEquals(
            listOf(StreamQualityResolution.P2160, StreamQualityResolution.P1440, StreamQualityResolution.P1080, StreamQualityResolution.P720),
            partial.resolutions,
        )
        assertEquals(StreamQualityOrder.DefaultSmallFilesFirstMaxGb, decodeStreamQualityOrder("v1;small=7").smallFilesFirstMaxGb)
    }

    @Test
    fun summaryReadsLikeTheOrder() {
        assertEquals(
            "1080p up to 5 GB → 1080p → 1440p → 720p → 4K",
            StreamQualityOrder().summary { "up to $it GB" },
        )
        assertEquals(
            "1080p → 1440p → 720p → 4K",
            StreamQualityOrder(smallFilesFirstMaxGb = null).summary { "up to $it GB" },
        )
    }

    // --- Auto-play selection --------------------------------------------------------------------

    private fun select(streams: List<StreamItem>, order: StreamQualityOrder?, bingeGroup: String? = null) =
        StreamAutoPlaySelector.selectAutoPlayStream(
            streams = streams,
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Addon"),
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            preferredBingeGroup = bingeGroup,
            preferBingeGroupInSelection = bingeGroup != null,
            qualityOrder = order,
        )

    @Test
    fun autoPlayPicksTheFirstStreamInQualityOrder() {
        assertEquals(small1080, select(mixed, StreamQualityOrder()))
    }

    @Test
    fun withoutAnOrderAutoPlayIsUnchanged() {
        assertEquals(big1080, select(mixed, null))
        assertEquals(big1080, select(mixed, StreamQualityOrder(enabled = false)))
    }

    @Test
    fun theNextEpisodesSameReleaseStillComesFirst() {
        val sameRelease = stream("same", name = "720p", videoSize = 900_000_000, bingeGroup = "release-x")
        assertEquals(sameRelease, select(mixed + sameRelease, StreamQualityOrder(), bingeGroup = "release-x"))
    }

    @Test
    fun allowedAddonsStillApplyBeforeOrdering() {
        val fromOtherAddon = stream("other", name = "1080p", videoSize = 1_000_000_000, addonName = "Other")
        val picked = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = listOf(big1080, fromOtherAddon),
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = setOf("Addon", "Other"),
            selectedAddons = setOf("Addon"),
            selectedPlugins = emptySet(),
            qualityOrder = StreamQualityOrder(),
        )
        assertEquals(big1080, picked)
    }
}
