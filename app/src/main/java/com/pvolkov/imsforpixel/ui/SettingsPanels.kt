package com.pvolkov.imsforpixel.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pvolkov.imsforpixel.R
import com.pvolkov.imsforpixel.VolteSettings

@Composable
fun DisplaySettingsPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(VolteSettings.PREFS_NAME, Context.MODE_PRIVATE) }
    var expanded by remember { mutableStateOf(false) }
    var show4gIcon by remember { mutableStateOf(VolteSettings.show4gIcon(prefs)) }
    var showLtePlus by remember { mutableStateOf(VolteSettings.showLtePlus(prefs)) }
    var showVowifiSpn by remember { mutableStateOf(VolteSettings.showVowifiSpn(prefs)) }

    fun persist(key: String, value: Boolean) {
        prefs.edit()
            .putBoolean(key, value)
            .putBoolean("clear_slot_0", false)
            .putBoolean("clear_slot_1", false)
            .commit()
    }

    SettingsSectionCard(
        title = stringResource(R.string.display_settings_header),
        expanded = expanded,
        onHeaderClick = { expanded = !expanded },
    ) {
        Text(
            text = stringResource(R.string.display_settings_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ToggleRow(stringResource(R.string.show_4g_icon_title), stringResource(R.string.show_4g_icon_desc), show4gIcon) {
            show4gIcon = it
            persist(VolteSettings.KEY_SHOW_4G_ICON, it)
        }
        ToggleRow(stringResource(R.string.show_lte_plus_title), stringResource(R.string.show_lte_plus_desc), showLtePlus) {
            showLtePlus = it
            persist(VolteSettings.KEY_SHOW_LTE_PLUS, it)
        }
        ToggleRow(stringResource(R.string.show_vowifi_spn_title), stringResource(R.string.show_vowifi_spn_desc), showVowifiSpn) {
            showVowifiSpn = it
            persist(VolteSettings.KEY_SHOW_VOWIFI_SPN, it)
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onHeaderClick != null) {
                            Modifier.clickable(onClick = onHeaderClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onHeaderClick != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.display_settings_collapse
                            } else {
                                R.string.display_settings_expand
                            },
                        ),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}
