package com.pvolkov.imsforpixel.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val containerColor = when (tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.tertiaryContainer
        StatusTone.Warning -> MaterialTheme.colorScheme.secondaryContainer
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when (tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.Error -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
