package com.pvolkov.imsforpixel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pvolkov.imsforpixel.CarrierInfo
import com.pvolkov.imsforpixel.R
import com.pvolkov.imsforpixel.SlotStatus
import com.pvolkov.imsforpixel.ui.components.StatusChip
import com.pvolkov.imsforpixel.ui.components.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSelectorTabs(
    slots: List<Int>,
    selectedSlot: Int,
    onSlotSelected: (Int) -> Unit,
) {
    if (slots.size <= 1) return
    val context = LocalContext.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        slots.forEachIndexed { index, slot ->
            SegmentedButton(
                selected = selectedSlot == slot,
                onClick = { onSlotSelected(slot) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = slots.size),
                label = {
                    Text(
                        CarrierInfo.getCarrierLabel(context, slot),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
fun SimStatusOverview(
    recheckSignal: MutableState<Long>,
    visibleSlots: List<Int>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var slot0Ims by remember { mutableStateOf(SlotStatus.ImsState.Unknown) }
    var slot1Ims by remember { mutableStateOf(SlotStatus.ImsState.Unknown) }
    var slot0Applied by remember { mutableStateOf(false) }
    var slot1Applied by remember { mutableStateOf(false) }
    var slot0Carrier by remember { mutableStateOf(CarrierInfo.getCarrierLabel(context, 0)) }
    var slot1Carrier by remember { mutableStateOf(CarrierInfo.getCarrierLabel(context, 1)) }

    val signalValue = recheckSignal.value
    LaunchedEffect(signalValue) {
        slot0Ims = SlotStatus.imsState(context, 0)
        slot1Ims = SlotStatus.imsState(context, 1)
        slot0Applied = SlotStatus.isConfigApplied(context, 0)
        slot1Applied = SlotStatus.isConfigApplied(context, 1)
        slot0Carrier = CarrierInfo.getCarrierLabel(context, 0)
        slot1Carrier = CarrierInfo.getCarrierLabel(context, 1)
    }

    fun imsOf(slot: Int) = if (slot == 0) slot0Ims else slot1Ims
    fun appliedOf(slot: Int) = if (slot == 0) slot0Applied else slot1Applied
    fun carrierOf(slot: Int) = if (slot == 0) slot0Carrier else slot1Carrier

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sim_status_overview),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh_ims_cd),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (visibleSlots.size <= 1) {
                val slot = visibleSlots.firstOrNull() ?: 0
                SimStatusTile(
                    modifier = Modifier.fillMaxWidth(),
                    title = carrierOf(slot),
                    configLabel = if (appliedOf(slot)) stringResource(R.string.app_optimized) else null,
                    imsLabel = imsStateLabel(imsOf(slot)),
                    imsTone = imsStateTone(imsOf(slot)),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleSlots.forEach { slot ->
                        SimStatusTile(
                            modifier = Modifier.weight(1f),
                            title = carrierOf(slot),
                            configLabel = if (appliedOf(slot)) stringResource(R.string.app_optimized) else null,
                            imsLabel = imsStateLabel(imsOf(slot)),
                            imsTone = imsStateTone(imsOf(slot)),
                        )
                    }
                }
            }

            val visibleIms = visibleSlots.map { imsOf(it) }
            val anyUnknown = visibleIms.any { it == SlotStatus.ImsState.Unknown }
            val registeredCount = visibleIms.count { it == SlotStatus.ImsState.Registered }
            if (anyUnknown) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.ims_status_stale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visibleSlots.size > 1 && registeredCount == 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.sim_status_dual_sim_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun imsStateLabel(state: SlotStatus.ImsState): String = when (state) {
    SlotStatus.ImsState.Registered -> stringResource(R.string.ims_registered_short)
    SlotStatus.ImsState.NotRegistered -> stringResource(R.string.ims_not_registered_short)
    SlotStatus.ImsState.Unknown -> stringResource(R.string.ims_unknown_short)
}

private fun imsStateTone(state: SlotStatus.ImsState): StatusTone = when (state) {
    SlotStatus.ImsState.Registered -> StatusTone.Success
    SlotStatus.ImsState.NotRegistered -> StatusTone.Error
    SlotStatus.ImsState.Unknown -> StatusTone.Neutral
}

@Composable
private fun SimStatusTile(
    title: String,
    configLabel: String?,
    imsLabel: String,
    imsTone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (configLabel != null) {
                StatusChip(label = configLabel, tone = StatusTone.Success)
                Spacer(modifier = Modifier.height(4.dp))
            }
            StatusChip(label = imsLabel, tone = imsTone)
        }
    }
}
