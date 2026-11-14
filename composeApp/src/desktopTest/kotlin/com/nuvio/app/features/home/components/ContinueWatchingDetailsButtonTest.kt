package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PosterZoomAnchorHolder
import com.nuvio.app.core.ui.PosterZoomOverlayCoordinator
import com.nuvio.app.core.ui.posterCardClickable
import org.junit.After
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ⓘ on Continue Watching cards must open details without also resuming playback, and must
 * not change what clicking / right-clicking the rest of the card does. The card here is wired the
 * same way as the real cards: posterCardClickable + hoverable + ContinueWatchingDetailsButton.
 */
class ContinueWatchingDetailsButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private var resumes = 0
    private var menus = 0
    private var details = 0

    @After
    fun clearAnchor() {
        PosterZoomAnchorHolder.consume()
        PosterZoomOverlayCoordinator.hide()
    }

    @Test
    fun hiddenUntilTheCardIsHovered() {
        setContent()
        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).assertDoesNotExist()

        compose.onNodeWithTag(CARD).performMouseInput { moveTo(center) }

        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).assertExists()
    }

    @Test
    fun hiddenAgainWhenThePointerLeaves() {
        setContent()
        compose.onNodeWithTag(CARD).performMouseInput { moveTo(center) }
        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).assertExists()

        compose.onNodeWithTag(CARD).performMouseInput { moveTo(Offset(-50f, -50f)) }
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).assertDoesNotExist()
    }

    @Test
    fun clickingTheButtonOpensDetailsWithoutResuming() {
        setContent()
        compose.onNodeWithTag(CARD).performMouseInput { moveTo(center) }

        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).performMouseInput { click() }

        compose.runOnIdle {
            assertEquals(1, details)
            assertEquals(0, resumes)
            assertEquals(0, menus)
        }
    }

    @Test
    fun clickingElsewhereOnTheCardStillResumes() {
        setContent()

        compose.onNodeWithTag(CARD).performMouseInput { click(Offset(20f, 20f)) }

        compose.runOnIdle {
            assertEquals(1, resumes)
            assertEquals(0, details)
            assertEquals(0, menus)
        }
    }

    @Test
    fun rightClickStillOpensTheActionsMenu() {
        setContent()

        compose.onNodeWithTag(CARD).performMouseInput { click(Offset(20f, 20f), button = MouseButton.Secondary) }

        compose.runOnIdle {
            assertEquals(1, menus)
            assertEquals(0, resumes)
            assertEquals(0, details)
        }
    }

    @Test
    fun noButtonWhenThereIsNoDetailsAction() {
        setContent(detailsAvailable = false)

        compose.onNodeWithTag(CARD).performMouseInput { moveTo(center) }

        compose.onNodeWithTag(ContinueWatchingDetailsButtonTag).assertDoesNotExist()
    }

    private fun setContent(detailsAvailable: Boolean = true) {
        compose.setContent {
            val hoverSource = remember { MutableInteractionSource() }
            val hovered by hoverSource.collectIsHoveredAsState()
            Box(
                Modifier
                    .size(240.dp, 135.dp)
                    .background(Color.DarkGray)
                    .testTag(CARD)
                    .posterCardClickable(
                        onClick = { resumes++ },
                        onLongClick = { menus++ },
                        zoomImageUrl = "card.jpg",
                        zoomCornerRadius = 12.dp,
                    )
                    .hoverable(hoverSource),
            ) {
                ContinueWatchingDetailsButton(
                    cardHovered = hovered,
                    onClick = if (detailsAvailable) ({ details++ }) else null,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    private companion object {
        const val CARD = "cw_card"
    }
}
