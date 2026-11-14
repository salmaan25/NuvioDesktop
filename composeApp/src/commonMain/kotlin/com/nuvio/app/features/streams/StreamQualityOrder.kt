package com.nuvio.app.features.streams

/**
 * salmaan25 fork: the order in which auto-play (and falling back to the next source) prefers
 * streams, kept per device. With the defaults: 1080p up to 5 GB, then other 1080p, 1440p, 720p,
 * 4K, then lower or unknown resolution. Streams only in languages other than the preferred audio
 * languages go after all of those, and cinema recordings (CAM, telesync...) go last.
 *
 * Within each step: known cached first, then files without burned-in subtitles, then proper
 * releases (BluRay / WEB-DL / WEBRip) before the rest, then files without extra dub languages, then size: within the "up to N GB" step the
 * largest (best) file first, in later steps the smallest first. Ties keep the original order
 * (installed addon order, then each addon's own order).
 */
enum class StreamQualityResolution(val label: String, val storageId: String) {
    P2160("4K", "2160"),
    P1440("1440p", "1440"),
    P1080("1080p", "1080"),
    P720("720p", "720"),
    ;

    companion object {
        fun fromStorageId(id: String): StreamQualityResolution? = entries.firstOrNull { it.storageId == id.trim() }
    }
}

data class StreamQualityOrder(
    val enabled: Boolean = true,
    /** Every resolution exactly once, most preferred first. */
    val resolutions: List<StreamQualityResolution> = DefaultResolutions,
    /** Files of the first resolution up to this size go before its larger files; null means off. */
    val smallFilesFirstMaxGb: Int? = DefaultSmallFilesFirstMaxGb,
    /**
     * Preferred audio languages (ISO 639-1, e.g. "en") from the player settings, filled in when the
     * order is used. Not stored. Empty means no language preference.
     */
    val preferredAudioLanguages: Set<String> = emptySet(),
) {
    companion object {
        val DefaultResolutions = listOf(
            StreamQualityResolution.P1080,
            StreamQualityResolution.P1440,
            StreamQualityResolution.P720,
            StreamQualityResolution.P2160,
        )
        const val DefaultSmallFilesFirstMaxGb = 5
        val SmallFilesFirstOptionsGb = listOf(2, 3, 5, 8, 10, 15, 20)
    }
}

/** A copy with every resolution exactly once and a supported size limit, whatever was stored. */
internal fun StreamQualityOrder.normalized(): StreamQualityOrder {
    val distinct = resolutions.distinct()
    val complete = distinct + StreamQualityResolution.entries.filterNot { it in distinct }
    val cap = smallFilesFirstMaxGb?.takeIf { it in StreamQualityOrder.SmallFilesFirstOptionsGb }
    return copy(
        resolutions = complete,
        smallFilesFirstMaxGb = if (smallFilesFirstMaxGb == null) null else cap ?: StreamQualityOrder.DefaultSmallFilesFirstMaxGb,
    )
}

/**
 * How much larger a file of this resolution may be than a 1080p one for similar picture quality,
 * by pixel count: the "smaller files first" size is set for 1080p and scaled with this.
 */
internal val StreamQualityResolution.sizeScale: Double
    get() = when (this) {
        StreamQualityResolution.P2160 -> 4.0
        StreamQualityResolution.P1440 -> 16.0 / 9.0
        StreamQualityResolution.P1080 -> 1.0
        StreamQualityResolution.P720 -> 4.0 / 9.0
    }

/** The "smaller files first" limit for the first resolution in bytes, or null when it is off. */
internal fun StreamQualityOrder.smallFilesFirstMaxBytes(): Long? {
    val order = normalized()
    val gigabytes = order.smallFilesFirstMaxGb ?: return null
    return (gigabytes * 1_000_000_000.0 * order.resolutions.first().sizeScale).toLong()
}

/** [smallFilesFirstMaxBytes] in GB as text: "5", "20", "2.2", "8.9". */
internal fun StreamQualityOrder.smallFilesFirstMaxGbText(): String? {
    val bytes = smallFilesFirstMaxBytes() ?: return null
    val tenths = kotlin.math.round(bytes / 100_000_000.0).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** Moves [resolution] one step towards the top ([up]) or the bottom of the order. */
internal fun StreamQualityOrder.move(resolution: StreamQualityResolution, up: Boolean): StreamQualityOrder {
    val list = normalized().resolutions.toMutableList()
    val index = list.indexOf(resolution)
    val target = if (up) index - 1 else index + 1
    if (index < 0 || target !in list.indices) return this
    list[index] = list[target]
    list[target] = resolution
    return copy(resolutions = list)
}

/** Puts [resolution] first, keeping the order of the others. */
internal fun StreamQualityOrder.moveToTop(resolution: StreamQualityResolution): StreamQualityOrder =
    copy(resolutions = listOf(resolution) + normalized().resolutions.filterNot { it == resolution })

/** Plain text for storage, e.g. `v1;on=1;order=1080,1440,720,2160;small=5`. */
internal fun StreamQualityOrder.encode(): String {
    val order = normalized()
    return listOf(
        "v1",
        "on=${if (order.enabled) 1 else 0}",
        "order=${order.resolutions.joinToString(",") { it.storageId }}",
        "small=${order.smallFilesFirstMaxGb ?: 0}",
    ).joinToString(";")
}

/** Reads [encode]'s text back. Anything missing or unreadable falls back to the default. */
internal fun decodeStreamQualityOrder(text: String?): StreamQualityOrder {
    val default = StreamQualityOrder()
    if (text.isNullOrBlank()) return default
    val fields = text.split(';').mapNotNull { part ->
        val key = part.substringBefore('=', missingDelimiterValue = "").trim()
        if (key.isEmpty()) null else key to part.substringAfter('=').trim()
    }.toMap()
    val enabled = when (fields["on"]) {
        "1" -> true
        "0" -> false
        else -> default.enabled
    }
    val resolutions = fields["order"]
        ?.split(',')
        ?.mapNotNull { StreamQualityResolution.fromStorageId(it) }
        ?.takeIf { it.isNotEmpty() }
        ?: default.resolutions
    val small = when (val value = fields["small"]?.toIntOrNull()) {
        null -> default.smallFilesFirstMaxGb
        0 -> null
        else -> value
    }
    return StreamQualityOrder(enabled = enabled, resolutions = resolutions, smallFilesFirstMaxGb = small).normalized()
}

/** What quality ordering needs to know about one stream, read from what the addon sent. */
internal data class StreamQualityFacts(
    /** Lines (in pixels) for 2160/1440/1080/720, a smaller value for SD, null when unknown. */
    val resolutionLines: Int?,
    val sizeBytes: Long?,
    /** true: known cached, false: known not cached, null: not stated. */
    val cached: Boolean?,
    /** CAM, telesync, telecine or screener: a recording, not a proper release. */
    val isCinemaRecording: Boolean = false,
    /** Audio languages the addon or file name states (ISO 639-1); empty when none are stated. */
    val languages: Set<String> = emptySet(),
    /** Marked MULTi or dual audio: has several audio tracks, usually including the original. */
    val multiAudio: Boolean = false,
    /** Marked as a BluRay, WEB-DL, WEB or WEBRip release (or from a known streaming service). */
    val properRelease: Boolean = false,
    /** Subtitles burned into the picture (KORSUB, HC, HARDSUB...), which cannot be turned off. */
    val hardcodedSubtitles: Boolean = false,
)

internal object StreamQualityParser {
    private val resolutionTokens: List<Pair<Regex, Int>> = listOf(
        token("2160p?", "4k", "uhd") to 2160,
        token("1440p?", "2k", "qhd") to 1440,
        token("1080p?", "1080i", "fhd") to 1080,
        token("720p?") to 720,
        token("576p?") to 576,
        token("480p?", "sd") to 480,
        token("360p?", "240p?") to 360,
    )
    private val cinemaRecordingRegex = token(
        "cam", "camrip", "hdcam", "ts", "hdts", "telesync", "tc", "hdtc", "telecine", "scr", "screener", "dvdscr",
    )
    private val sizeRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*(tb|tib|gb|gib|mb|mib)(?![a-z])")
    private val cachedRegex = Regex("⚡|\\+\\]")
    // ⏳ (Comet/StremThru/MediaFusion), "[TB download]" (Torrentio), "[TB⬇️]" (Comet).
    // A bare ⬇️ is a seeder count in AIOStreams, so only the bracketed form counts.
    private val notCachedRegex = Regex("⏳|download\\]|\\[[a-z]{2,4}\\s?⬇")

    // Short codes that are also common title words (mal, tel, ara, por...) are left out.
    private val languageWords: List<Pair<Regex, String>> = listOf(
        token("eng", "english") to "en",
        token("hin", "hindi") to "hi",
        token("ita", "italian", "italiano") to "it",
        token("spa", "esp", "spanish", "espanol", "español", "castellano", "latino", "lat") to "es",
        token("fre", "french", "truefrench", "vf", "vff", "vf2", "vfq", "vfi") to "fr",
        token("ger", "deu", "german", "deutsch") to "de",
        token("portuguese", "portugues", "português", "dublado") to "pt",
        token("rus", "russian") to "ru",
        token("ukr", "ukrainian") to "uk",
        token("polish", "lektor") to "pl",
        token("jpn", "japanese") to "ja",
        token("kor", "korean") to "ko",
        token("chinese", "mandarin", "cantonese") to "zh",
        token("tamil") to "ta",
        token("telugu") to "te",
        token("malayalam") to "ml",
        token("turkish") to "tr",
        token("arabic") to "ar",
        token("cze", "czech") to "cs",
        token("hungarian") to "hu",
    )
    private val hardcodedSubtitlesRegex = token(
        "hc", "hcsub", "hcsubs", "hardsub", "hardsubs", "hardcoded", "korsub", "chisub", "chs", "cht",
    )
    private val subtitleMarkerRegex = token(
        "sub", "subs", "subbed", "softsub", "hardsub", "korsub", "engsub", "chisub", "multisub", "multi-sub", "vostfr",
    )
    private val multiAudioRegex = token("multi", "dual", "dual audio", "multi audio", "multi-audio", "dual-audio")
    private val properReleaseRegex = token(
        "bluray", "blu-ray", "bdrip", "brrip", "bdremux", "remux", "web-dl", "webdl", "web", "webrip", "web-rip",
        "amzn", "nf", "dsnp", "hmax", "atvp", "pcok", "pmtp", "hulu", "itunes",
    )
    private val flagLanguages = mapOf(
        "GB" to "en", "US" to "en", "AU" to "en", "NZ" to "en", "IE" to "en",
        "IN" to "hi", "IT" to "it", "ES" to "es", "MX" to "es", "AR" to "es", "CO" to "es", "CL" to "es",
        "PE" to "es", "VE" to "es", "FR" to "fr", "DE" to "de", "AT" to "de", "PT" to "pt", "BR" to "pt",
        "RU" to "ru", "UA" to "uk", "PL" to "pl", "JP" to "ja", "KR" to "ko", "CN" to "zh", "TW" to "zh",
        "HK" to "zh", "TR" to "tr", "SA" to "ar", "AE" to "ar", "EG" to "ar", "CZ" to "cs", "HU" to "hu",
        "NL" to "nl", "SE" to "sv", "DK" to "da", "NO" to "no", "FI" to "fi", "GR" to "el", "IL" to "he",
        "TH" to "th", "VN" to "vi", "ID" to "id", "RO" to "ro", "BG" to "bg",
    )

    fun facts(stream: StreamItem): StreamQualityFacts {
        val name = stream.name.orEmpty().lowercase()
        val filename = (stream.behaviorHints.filename ?: stream.clientResolve?.filename).orEmpty().lowercase()
        val details = listOfNotNull(stream.title, stream.description).joinToString("\n").lowercase()
        // The addon's own label comes first: descriptions can mention other resolutions.
        val resolution = resolutionIn(name) ?: resolutionIn(filename) ?: resolutionIn(details)
        val size = stream.behaviorHints.videoSize?.takeIf { it > 0 }
            ?: stream.debridCacheStatus?.cachedSize?.takeIf { it > 0 }
            ?: sizeIn(details)
        val cached = when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CACHED -> true
            StreamDebridCacheState.NOT_CACHED -> false
            else -> when {
                cachedRegex.containsMatchIn(name) -> true
                notCachedRegex.containsMatchIn(name) -> false
                else -> null
            }
        }
        val recordingText = "$name $filename"
        // Subtitle lines (💬) list subtitle languages, not audio, so they are left out.
        val audioDetails = details.lines().filterNot { it.contains("💬") }.joinToString("\n")
        val languageText = "$name\n$filename\n$audioDetails"
        return StreamQualityFacts(
            resolutionLines = resolution,
            sizeBytes = size,
            cached = cached,
            isCinemaRecording = cinemaRecordingRegex.containsMatchIn(recordingText),
            languages = languagesIn(languageText) + flagLanguagesIn(languageText) + scriptLanguagesIn(filename),
            multiAudio = multiAudioRegex.containsMatchIn(languageText),
            properRelease = properReleaseRegex.containsMatchIn("$name\n$filename\n$details"),
            hardcodedSubtitles = hardcodedSubtitlesRegex.containsMatchIn("$name\n$filename"),
        )
    }

    private fun languagesIn(text: String): Set<String> =
        languageWords.filter { (regex, _) -> regex.containsMatchIn(text) }.mapTo(mutableSetOf()) { it.second }

    /** Flag emoji (pairs of regional indicator letters) mapped to the country's main language. */
    private fun flagLanguagesIn(text: String): Set<String> {
        val letters = mutableListOf<Char?>()
        var index = 0
        while (index < text.length) {
            val high = text[index]
            val low = text.getOrNull(index + 1)
            if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
                val codePoint = ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
                letters += if (codePoint in 0x1F1E6..0x1F1FF) 'A' + (codePoint - 0x1F1E6) else null
                index += 2
            } else {
                letters += null
                index += 1
            }
        }
        val languages = mutableSetOf<String>()
        var i = 0
        while (i < letters.size - 1) {
            val first = letters[i]
            val second = letters[i + 1]
            if (first != null && second != null) {
                flagLanguages["$first$second"]?.let(languages::add)
                i += 2
            } else {
                i += 1
            }
        }
        return languages
    }

    /**
     * A file name written in Cyrillic, Devanagari, Arabic, Hebrew, Thai or Greek is usually a dub for
     * that language's audience. Chinese, Japanese and Korean titles usually mean subtitles, so they
     * are not read as audio, and neither is any name that says it carries subtitles.
     */
    private fun scriptLanguagesIn(filename: String): Set<String> {
        if (subtitleMarkerRegex.containsMatchIn(filename)) return emptySet()
        val languages = mutableSetOf<String>()
        filename.forEach { char ->
            when (char.code) {
                in 0x0400..0x04FF -> languages += "ru"
                in 0x0900..0x097F -> languages += "hi"
                in 0x0600..0x06FF -> languages += "ar"
                in 0x0590..0x05FF -> languages += "he"
                in 0x0E00..0x0E7F -> languages += "th"
                in 0x0370..0x03FF -> languages += "el"
            }
        }
        return languages
    }

    private fun resolutionIn(text: String): Int? =
        if (text.isBlank()) null else resolutionTokens.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second

    private fun sizeIn(text: String): Long? {
        val match = sizeRegex.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "tb" -> 1e12
            "tib" -> 1_099_511_627_776.0
            "gb" -> 1e9
            "gib" -> 1_073_741_824.0
            "mb" -> 1e6
            else -> 1_048_576.0
        }
        return (value * multiplier).toLong().takeIf { it > 0 }
    }

    private fun token(vararg tokens: String): Regex =
        Regex("(^|[^a-z0-9])(${tokens.joinToString("|")})([^a-z0-9]|\$)")
}

/**
 * Step of [facts] in [order], lower first: the first resolution's small files (when that is on),
 * then each resolution in order, then lower or unknown resolutions, then cinema recordings.
 */
internal fun StreamQualityOrder.stepOf(facts: StreamQualityFacts): Int {
    val order = normalized()
    val smallStep = if (order.smallFilesFirstMaxGb != null) 1 else 0
    val restStep = smallStep + order.resolutions.size
    if (facts.isCinemaRecording) return restStep + 1
    val resolution = when (facts.resolutionLines) {
        2160 -> StreamQualityResolution.P2160
        1440 -> StreamQualityResolution.P1440
        1080 -> StreamQualityResolution.P1080
        720 -> StreamQualityResolution.P720
        else -> return restStep
    }
    val index = order.resolutions.indexOf(resolution)
    if (index == 0 && order.smallFilesFirstMaxGb != null) {
        val size = facts.sizeBytes
        val limit = order.smallFilesFirstMaxBytes()
        if (size != null && limit != null && size <= limit) return 0
    }
    return smallStep + index
}

/**
 * [streams] in this order's preference. The sort is stable, so streams that are equal for it keep
 * their original order. Returns [streams] itself when the order is turned off.
 */
internal fun StreamQualityOrder.sort(streams: List<StreamItem>): List<StreamItem> {
    if (!enabled || streams.size < 2) return streams
    val order = normalized()
    val preferred = preferredAudioLanguages.mapNotNullTo(mutableSetOf()) { it.baseLanguage() }
    return streams
        .map { stream -> stream to order.sortKey(StreamQualityParser.facts(stream), preferred) }
        .sortedWith(compareBy { it.second })
        .map { it.first }
}

private fun String.baseLanguage(): String? = trim().lowercase().substringBefore('-').substringBefore('_').takeIf { it.isNotEmpty() }

/**
 * Whether [stream] states only audio languages this order does not accept (and is not marked
 * multi-audio). False when the order has no accepted languages or the stream states none.
 */
internal fun StreamQualityOrder.isForeignOnly(stream: StreamItem): Boolean {
    val accepted = preferredAudioLanguages.mapNotNullTo(mutableSetOf()) { it.baseLanguage() }
    if (accepted.isEmpty()) return false
    val facts = StreamQualityParser.facts(stream)
    return facts.languages.isNotEmpty() && !facts.multiAudio && facts.languages.none { it in accepted }
}

/** Audio languages that are not "foreign" for a title: the preferred ones plus its original language. */
internal fun acceptedAudioLanguages(preferredTargets: List<String>, contentLanguage: String?): Set<String> =
    (preferredTargets + listOfNotNull(contentLanguage)).mapNotNullTo(linkedSetOf()) { it.baseLanguage() }

/** Ranks of one stream, compared in order; lower goes first. */
private data class StreamQualitySortKey(
    val cinemaRecording: Int,
    val foreignOnly: Int,
    val step: Int,
    val cache: Int,
    val hardcodedSubtitles: Int,
    val notProper: Int,
    val extraLanguages: Int,
    val size: Long,
) : Comparable<StreamQualitySortKey> {
    override fun compareTo(other: StreamQualitySortKey): Int = compareValuesBy(
        this, other,
        { it.cinemaRecording }, { it.foreignOnly }, { it.step }, { it.cache }, { it.hardcodedSubtitles }, { it.notProper },
        { it.extraLanguages }, { it.size },
    )
}

private fun StreamQualityOrder.sortKey(facts: StreamQualityFacts, preferred: Set<String>): StreamQualitySortKey {
    val stated = facts.languages
    val hasPreferred = stated.any { it in preferred }
    // Only stated languages count: most releases in the original language don't say which it is.
    val foreignOnly = preferred.isNotEmpty() && stated.isNotEmpty() && !hasPreferred && !facts.multiAudio
    val extraLanguages = preferred.isNotEmpty() && (stated.any { it !in preferred } || facts.multiAudio)
    val step = stepOf(facts)
    val largestFirst = smallFilesFirstMaxGb == null || step == 0
    val size = facts.sizeBytes?.let { if (largestFirst) -it else it } ?: Long.MAX_VALUE
    return StreamQualitySortKey(
        cinemaRecording = if (facts.isCinemaRecording) 1 else 0,
        foreignOnly = if (foreignOnly) 1 else 0,
        step = step,
        cache = when (facts.cached) {
            true -> 0
            null -> 1
            false -> 2
        },
        hardcodedSubtitles = if (facts.hardcodedSubtitles) 1 else 0,
        notProper = if (facts.properRelease) 0 else 1,
        extraLanguages = if (extraLanguages) 1 else 0,
        size = size,
    )
}

/** A one-line summary for settings, e.g. "1080p up to 5 GB → 1080p → 1440p → 720p → 4K". */
internal fun StreamQualityOrder.summary(upToLabel: (gigabytesText: String) -> String): String {
    val order = normalized()
    val steps = buildList {
        order.smallFilesFirstMaxGbText()?.let { add("${order.resolutions.first().label} ${upToLabel(it)}") }
        order.resolutions.forEach { add(it.label) }
    }
    return steps.joinToString(" → ")
}
