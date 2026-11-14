package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.streams.StreamQualityOrder
import com.nuvio.app.features.streams.StreamQualityOrderRepository
import com.nuvio.app.features.streams.StreamQualityResolution
import com.nuvio.app.features.streams.normalized
import com.nuvio.app.features.streams.smallFilesFirstMaxGbText
import com.nuvio.app.features.streams.summary
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** salmaan25 fork: the "Quality order" row in stream selection settings, with its editor. */
@Composable
internal fun StreamQualityOrderSettingsRow(isTablet: Boolean) {
    val order by StreamQualityOrderRepository.state.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    SettingsNavigationRow(
        title = stringResource(Res.string.salmaan_quality_order_title),
        description = streamQualityOrderSummary(order),
        isTablet = isTablet,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        StreamQualityOrderDialog(
            order = order,
            onEnabledChange = StreamQualityOrderRepository::setEnabled,
            onMove = StreamQualityOrderRepository::move,
            onSmallFilesFirstChange = StreamQualityOrderRepository::setSmallFilesFirstMaxGb,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun streamQualityOrderSummary(order: StreamQualityOrder): String {
    if (!order.enabled) return stringResource(Res.string.salmaan_quality_order_off)
    val upTo = order.smallFilesFirstMaxGbText()?.let { stringResource(Res.string.salmaan_quality_order_up_to, it) }
    return order.summary { upTo.orEmpty() }
}

internal const val StreamQualityOrderEnableTag = "quality_order_enable"
internal fun streamQualityOrderRowTag(resolution: StreamQualityResolution) = "quality_order_row_${resolution.storageId}"
internal fun streamQualityOrderUpTag(resolution: StreamQualityResolution) = "quality_order_up_${resolution.storageId}"
internal fun streamQualityOrderDownTag(resolution: StreamQualityResolution) = "quality_order_down_${resolution.storageId}"
internal fun streamQualityOrderSmallTag(gigabytes: Int?) = "quality_order_small_${gigabytes ?: 0}"

/** Stateless editor: shows [order] and reports changes; the caller stores them. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StreamQualityOrderDialog(
    order: StreamQualityOrder,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (resolution: StreamQualityResolution, up: Boolean) -> Unit,
    onSmallFilesFirstChange: (gigabytes: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalized = order.normalized()
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.salmaan_quality_order_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DialogOption(
                text = stringResource(Res.string.salmaan_quality_order_enable),
                description = stringResource(Res.string.salmaan_quality_order_enable_description),
                selected = normalized.enabled,
                role = Role.Switch,
                onClick = { onEnabledChange(!normalized.enabled) },
                modifier = Modifier.testTag(StreamQualityOrderEnableTag),
            )

            Column(
                modifier = Modifier.fillMaxWidth().alpha(if (normalized.enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                normalized.resolutions.forEachIndexed { index, resolution ->
                    QualityOrderRow(
                        position = index + 1,
                        resolution = resolution,
                        canMoveUp = normalized.enabled && index > 0,
                        canMoveDown = normalized.enabled && index < normalized.resolutions.lastIndex,
                        onMove = onMove,
                    )
                }
                Text(
                    text = stringResource(Res.string.salmaan_quality_order_then_rest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )

                Text(
                    text = stringResource(Res.string.salmaan_quality_order_small_first),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(Res.string.salmaan_quality_order_small_first_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val options: List<Int?> = listOf(null) + StreamQualityOrder.SmallFilesFirstOptionsGb
                    options.forEach { gigabytes ->
                        val label = if (gigabytes == null) {
                            stringResource(Res.string.salmaan_quality_order_small_first_off)
                        } else {
                            stringResource(Res.string.salmaan_quality_order_gigabytes, gigabytes)
                        }
                        QualityOrderChip(
                            label = label,
                            selected = normalized.smallFilesFirstMaxGb == gigabytes,
                            enabled = normalized.enabled,
                            onClick = { onSmallFilesFirstChange(gigabytes) },
                            modifier = Modifier.testTag(streamQualityOrderSmallTag(gigabytes)),
                        )
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun QualityOrderRow(
    position: Int,
    resolution: StreamQualityResolution,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (StreamQualityResolution, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(streamQualityOrderRowTag(resolution)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position.  ${resolution.label}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        val upLabel = stringResource(Res.string.salmaan_quality_order_move_up, resolution.label)
        IconButton(
            onClick = { onMove(resolution, true) },
            enabled = canMoveUp,
            modifier = Modifier.testTag(streamQualityOrderUpTag(resolution)).semantics { contentDescription = upLabel },
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
        }
        val downLabel = stringResource(Res.string.salmaan_quality_order_move_down, resolution.label)
        IconButton(
            onClick = { onMove(resolution, false) },
            enabled = canMoveDown,
            modifier = Modifier.testTag(streamQualityOrderDownTag(resolution)).semantics { contentDescription = downLabel },
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun QualityOrderChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary.copy(alpha = 0.18f) else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outline.copy(alpha = 0.5f)),
        modifier = modifier
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
