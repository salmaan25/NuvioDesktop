package com.nuvio.app.features.player

import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * normalizeLanguageCode()/languageLabelResForCode() are called for every addon subtitle on each
 * player tick, so they precompute their lookup tables and memoize results. These tests pin the
 * results to the original (uncached) implementation, kept below as a reference.
 */
class PlayerLanguagePreferencesTest {

    @Test
    fun normalizeLanguageCodeMatchesOriginalImplementation() {
        // Twice: the first pass fills the cache, the second is answered from it.
        repeat(2) { pass ->
            LanguageTestSamples.forEach { input ->
                assertEquals(
                    OriginalLanguagePreferences.normalizeLanguageCode(input),
                    normalizeLanguageCode(input),
                    "pass=$pass input=<$input>",
                )
            }
        }
    }

    @Test
    fun languageLabelResForCodeMatchesOriginalImplementation() {
        repeat(2) { pass ->
            LanguageTestSamples.forEach { input ->
                assertEquals(
                    OriginalLanguagePreferences.languageLabelResForCode(input),
                    languageLabelResForCode(input),
                    "pass=$pass input=<$input>",
                )
            }
        }
    }

    @Test
    fun resultsStayCorrectWhenTheCacheOverflows() {
        // Far more distinct inputs than the cache holds, so it is cleared several times.
        val unusual = (0 until 2_000).map { i -> if (i % 2 == 0) "xx-$i" else "Track $i English" }
        repeat(2) { pass ->
            (unusual + LanguageTestSamples).forEach { input ->
                assertEquals(
                    OriginalLanguagePreferences.normalizeLanguageCode(input),
                    normalizeLanguageCode(input),
                    "pass=$pass input=<$input>",
                )
            }
        }
    }
}

internal val LanguageTestSamples: List<String?> = buildList {
    add(null); add(""); add("   "); add("und"); add("unknown"); add("xx"); add("klingon")
    addAll(AvailableLanguageOptions.map { it.code })
    addAll(AvailableLanguageOptions.map { it.code.uppercase() })
    addAll(LanguageCodeAliases.keys)
    addAll(LanguageNameAliases.keys)
    addAll(LanguageNameAliases.keys.map { it.replaceFirstChar(Char::uppercaseChar) })
    addAll(LanguageNameAliases.keys.map { "$it (SDH)" })
    addAll(LanguageNameAliases.keys.map { "Forced $it subtitles" })
    addAll(
        listOf(
            "en", "eng", "en_US", "en-GB", "English", "English [SDH]", "english.forced",
            "pt", "pob", "pt-BR", "pt_br", "Portuguese (Brazil)", "Portuguese - Portugal", "Brazilian Portuguese",
            "es", "spa", "es-419", "es_419", "Spanish (Latin America)", "Spanish Castellano", "Español",
            "fr", "fre", "fra", "French Canadian", "fr-CA", "de", "ger", "deu", "German.Forced",
            "zh", "chi", "zho", "zh-CN", "zh_TW", "Chinese (Simplified)", "Chinese Traditional",
            "hi", "hin", "Hindi", "ta", "tam", "te", "tel", "ml", "kn", "ar", "ara", "he", "heb", "iw",
            "ja", "jpn", "ko", "kor", "ru", "rus", "sr", "srp", "sr-Latn", "no", "nob", "nb", "nn",
            "tgl", "fil", "Filipino", "multi", "Dual Audio", "a/b/c", "x.y.z", "  English  ", "EN-us",
            "nld", "dut", "tur", "ita", "dan", "fin", "swe", "nor", "por", "pol", "cze", "hun", "gre", "rum",
        ),
    )
}

/** Reference: the implementation before lookup tables were precomputed and results memoized. */
internal object OriginalLanguagePreferences {

    fun languageLabelResForCode(code: String?): StringResource? {
        val normalized = normalizeLanguageCode(code) ?: return null
        return AvailableLanguageOptions.firstOrNull {
            normalizeLanguageCode(it.code) == normalized
        }?.labelRes
    }

    fun normalizeLanguageCode(language: String?): String? {
        val raw = language
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val tokenized = raw
            .replace('-', ' ')
            .replace('.', ' ')
            .replace('/', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        fun containsAny(vararg values: String): Boolean =
            values.any { value -> tokenized.contains(value) }

        if (containsAny("portuguese", "portugues")) {
            return when {
                containsAny("brazil", "brasil", "brazilian", "brasileiro", "pt br", "ptbr", "pob", "(br)") ->
                    "pt-br"
                containsAny("portugal", "european", "europeu", "iberian", "pt pt", "ptpt") ->
                    "pt"
                else -> "pt"
            }
        }

        if (containsAny("spanish", "espanol", "castellano")) {
            return if (containsAny("latin", "latino", "latinoamerica", "latinoamericano", "lat am", "latam", "es 419", "es419", "(419)")) {
                "es-419"
            } else {
                "es"
            }
        }

        LanguageCodeAliases[raw]?.let { return it.replace('_', '-').lowercase() }
        LanguageNameAliases[tokenized]?.let { return it }
        LanguageNameAliases.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (name, _) ->
                tokenized == name ||
                    tokenized.startsWith("$name ") ||
                    tokenized.endsWith(" $name") ||
                    tokenized.contains(" $name ")
            }
            ?.let { return it.value }

        val primary = raw.substringBefore('-')
        val primaryAlias = LanguageCodeAliases[primary]?.replace('_', '-')?.lowercase()
        val suffix = raw.substringAfter('-', "")
        return if (suffix.isBlank()) {
            primaryAlias ?: primary
        } else if (primaryAlias != null && !primaryAlias.contains('-')) {
            "$primaryAlias-$suffix"
        } else {
            primaryAlias ?: "$primary-$suffix"
        }
    }
}
