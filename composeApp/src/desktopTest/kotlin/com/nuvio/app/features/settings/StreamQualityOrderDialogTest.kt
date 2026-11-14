package com.nuvio.app.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.nuvio.app.features.streams.StreamQualityOrder
import com.nuvio.app.features.streams.StreamQualityResolution
import com.nuvio.app.features.streams.move
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The Quality order editor (salmaan25 fork). It is stateless, so the test keeps the order itself and
 * never touches stored settings.
 */
class StreamQualityOrderDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var order by mutableStateOf(StreamQualityOrder())
    private var dismissed = 0

    private fun show() {
        compose.setContent {
            StreamQualityOrderDialog(
                order = order,
                onEnabledChange = { order = order.copy(enabled = it) },
                onMove = { resolution, up -> order = order.move(resolution, up) },
                onSmallFilesFirstChange = { order = order.copy(smallFilesFirstMaxGb = it) },
                onDismiss = { dismissed++ },
            )
        }
    }

    @Test
    fun arrowsReorderAndAreDisabledAtTheEnds() {
        show()
        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1080)).assertIsNotEnabled()
        compose.onNodeWithTag(streamQualityOrderDownTag(StreamQualityResolution.P2160)).assertIsNotEnabled()

        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1440)).performClick()
        assertEquals(
            listOf(StreamQualityResolution.P1440, StreamQualityResolution.P1080, StreamQualityResolution.P720, StreamQualityResolution.P2160),
            order.resolutions,
        )
        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1440)).assertIsNotEnabled()
        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1080)).assertIsEnabled()

        compose.onNodeWithTag(streamQualityOrderDownTag(StreamQualityResolution.P720)).performClick()
        assertEquals(
            listOf(StreamQualityResolution.P1440, StreamQualityResolution.P1080, StreamQualityResolution.P2160, StreamQualityResolution.P720),
            order.resolutions,
        )
    }

    @Test
    fun smallFilesFirstChoices() {
        show()
        compose.onNodeWithTag(streamQualityOrderSmallTag(2)).performClick()
        assertEquals(2, order.smallFilesFirstMaxGb)
        compose.onNodeWithTag(streamQualityOrderSmallTag(null)).performClick()
        assertNull(order.smallFilesFirstMaxGb)
        compose.onNodeWithTag(streamQualityOrderSmallTag(20)).performClick()
        assertEquals(20, order.smallFilesFirstMaxGb)
    }

    @Test
    fun turningOffLocksTheOrder() {
        show()
        compose.onNodeWithTag(StreamQualityOrderEnableTag).performClick()
        assertFalse(order.enabled)
        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1440)).assertIsNotEnabled()
        compose.onNodeWithTag(streamQualityOrderDownTag(StreamQualityResolution.P1080)).assertIsNotEnabled()
        compose.onNodeWithTag(streamQualityOrderSmallTag(2)).assertIsNotEnabled()
        compose.onNodeWithTag(StreamQualityOrderEnableTag).performClick()
        compose.onNodeWithTag(streamQualityOrderUpTag(StreamQualityResolution.P1440)).assertIsEnabled()
    }
}
