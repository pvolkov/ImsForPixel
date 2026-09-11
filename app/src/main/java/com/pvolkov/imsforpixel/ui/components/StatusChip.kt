package com.pvolkov.imsforpixel.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pvolkov.imsforpixel.ui.theme.StatusPalette

enum class StatusTone {
    Success,
    Warning,
    Error,
    Neutral,
}

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val containerColor = when (tone) {
        StatusTone.Success -> if (dark) StatusPalette.successContainerDark else StatusPalette.successContainerLight
        StatusTone.Warning -> if (dark) StatusPalette.warningContainerDark else StatusPalette.warningContainerLight
        StatusTone.Error -> if (dark) StatusPalette.errorContainerDark else StatusPalette.errorContainerLight
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when (tone) {
        StatusTone.Success -> if (dark) StatusPalette.onSuccessDark else StatusPalette.onSuccessLight
        StatusTone.Warning -> if (dark) StatusPalette.onWarningDark else StatusPalette.onWarningLight
        StatusTone.Error -> if (dark) StatusPalette.onErrorDark else StatusPalette.onErrorLight
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon: ImageVector = when (tone) {
        StatusTone.Success -> Icons.Filled.Check
        StatusTone.Warning -> Icons.Filled.Warning
        StatusTone.Error -> Icons.Filled.Close
        StatusTone.Neutral -> Icons.Filled.Info
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = labelColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}
