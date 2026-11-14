package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The engine switch on error exists only on Android. Its setting (on by default) is hidden on iOS and
 * desktop, so it must not lock out falling back to the next source there. Android and iOS must keep
 * exactly the behaviour they had before.
 */
class SourceFallbackEngineGateTest {
    private val booleans = listOf(false, true)

    // The gates as they were before the fix: only iOS was exempt from the engine switch.
    private fun originalFallbackAllowed(fallbackActive: Boolean, autoSwitch: Boolean, isIos: Boolean) =
        fallbackActive && !(!isIos && autoSwitch)

    private fun originalSettingBlocked(autoSwitch: Boolean, fallbackOn: Boolean, isIos: Boolean) =
        !isIos && autoSwitch && !fallbackOn

    private fun fallbackAllowed(fallbackActive: Boolean, autoSwitch: Boolean, isIos: Boolean, isDesktop: Boolean) =
        fallbackActive && !engineSwitchOnErrorApplies(autoSwitch, isIos = isIos, isDesktop = isDesktop)

    private fun settingBlocked(autoSwitch: Boolean, fallbackOn: Boolean, isIos: Boolean, isDesktop: Boolean) =
        engineSwitchOnErrorApplies(autoSwitch, isIos = isIos, isDesktop = isDesktop) && !fallbackOn

    @Test
    fun engineSwitchAppliesOnlyOnAndroid() {
        assertTrue(engineSwitchOnErrorApplies(true, isIos = false, isDesktop = false))
        assertFalse(engineSwitchOnErrorApplies(false, isIos = false, isDesktop = false))
        assertFalse(engineSwitchOnErrorApplies(true, isIos = true, isDesktop = false))
        assertFalse(engineSwitchOnErrorApplies(true, isIos = false, isDesktop = true))
    }

    @Test
    fun androidAndIosBehaveExactlyAsBefore() {
        for (isIos in booleans) for (fallbackActive in booleans) for (autoSwitch in booleans) for (fallbackOn in booleans) {
            val case = "isIos=$isIos fallbackActive=$fallbackActive autoSwitch=$autoSwitch fallbackOn=$fallbackOn"
            assertEquals(
                originalFallbackAllowed(fallbackActive, autoSwitch, isIos),
                fallbackAllowed(fallbackActive, autoSwitch, isIos, isDesktop = false),
                case,
            )
            assertEquals(
                originalSettingBlocked(autoSwitch, fallbackOn, isIos),
                settingBlocked(autoSwitch, fallbackOn, isIos, isDesktop = false),
                case,
            )
        }
    }

    @Test
    fun onDesktopOnlyTheFallbackSettingDecides() {
        for (fallbackActive in booleans) for (autoSwitch in booleans) for (fallbackOn in booleans) {
            val case = "fallbackActive=$fallbackActive autoSwitch=$autoSwitch fallbackOn=$fallbackOn"
            assertEquals(fallbackActive, fallbackAllowed(fallbackActive, autoSwitch, isIos = false, isDesktop = true), case)
            assertFalse(settingBlocked(autoSwitch, fallbackOn, isIos = false, isDesktop = true), case)
        }
    }
}
