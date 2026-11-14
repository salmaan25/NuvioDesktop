package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On desktop, "fall back to the next source" used to be locked off: it was blocked by the Android-only
 * engine switch on error, which is on by default and hidden on desktop. Uses the real platform flags
 * of the desktop build. Only builds settings values; never touches stored settings.
 */
class SourceFallbackOnDesktopTest {
    private val autoPlayWithFallback = PlayerSettingsUiState(
        streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
        streamAutoPlayFallbackOnFailure = true,
    )

    @Test
    fun engineSwitchDefaultIsOnButDoesNotApplyOnDesktop() {
        assertTrue(PlayerSettingsUiState().autoSwitchPlaybackEngineOnError)
        assertFalse(PlayerSettingsUiState().engineSwitchOnErrorActive)
    }

    @Test
    fun fallbackRunsOnDesktopWithDefaultEngineSetting() {
        assertTrue(autoPlayWithFallback.sourceFallbackOnErrorEnabled)
    }

    @Test
    fun fallbackSwitchCanBeTurnedOnOnDesktop() {
        val off = autoPlayWithFallback.copy(streamAutoPlayFallbackOnFailure = false)
        assertFalse(off.sourceFallbackSettingBlockedByEngineSwitch)
        assertFalse(autoPlayWithFallback.sourceFallbackSettingBlockedByEngineSwitch)
    }

    @Test
    fun fallbackStillNeedsItsSettingAutoPlayAndTheInternalPlayer() {
        assertFalse(autoPlayWithFallback.copy(streamAutoPlayFallbackOnFailure = false).sourceFallbackOnErrorEnabled)
        assertFalse(autoPlayWithFallback.copy(streamAutoPlayMode = StreamAutoPlayMode.MANUAL).sourceFallbackOnErrorEnabled)
        assertFalse(autoPlayWithFallback.copy(externalPlayerEnabled = true).sourceFallbackOnErrorEnabled)
    }
}
