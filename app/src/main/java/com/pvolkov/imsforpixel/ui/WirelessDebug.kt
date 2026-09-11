package com.pvolkov.imsforpixel.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pvolkov.imsforpixel.MainActivity
import com.pvolkov.imsforpixel.PairingSession
import com.pvolkov.imsforpixel.R
import com.pvolkov.imsforpixel.adb.AdbDiscovery
import com.pvolkov.imsforpixel.adb.AdbEndpoint
import com.pvolkov.imsforpixel.system.ConnectivityMonitor
import com.pvolkov.imsforpixel.ui.components.StatusChip
import com.pvolkov.imsforpixel.ui.components.StatusTone

@Composable
fun WirelessDebugEffects(
    onPortDiscovered: (Int) -> Unit,
    isWifiConnected: MutableState<Boolean>,
    isForeground: Boolean,
    keepDiscovering: Boolean,
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                isWifiConnected.value = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            override fun onLost(network: android.net.Network) {
                isWifiConnected.value = false
            }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                isWifiConnected.value = networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {}

        isWifiConnected.value = ConnectivityMonitor(context).isWifiConnected()

        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isForeground, keepDiscovering) {
        if (!isForeground && !keepDiscovering) return@LaunchedEffect
        AdbDiscovery(context).discover().collect { endpoint ->
            when (endpoint) {
                is AdbEndpoint.Connect -> onPortDiscovered(endpoint.port)
                is AdbEndpoint.Pairing -> PairingSession.pairingPort = endpoint.port
            }
        }
    }
}

@Composable
fun WirelessDebugSetupPanel(
    portInput: String,
    onPortInputChange: (String) -> Unit,
    isWifiConnected: Boolean,
    isAuthorized: Boolean,
    adbReady: Boolean,
    isApplying: Boolean,
    dualSim: Boolean,
    onRestoreSlot: () -> Unit,
    onRestoreBoth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showManualPorts by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Text(
            stringResource(R.string.wireless_debug_activation),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_once_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.step1_wifi_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.step1_wifi_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                StatusChip(
                    label = if (isWifiConnected) {
                        stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.not_connected)
                    },
                    tone = if (isWifiConnected) StatusTone.Success else StatusTone.Error,
                )
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.step2_pairing_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = {
                Text(
                    text = when {
                        isAuthorized -> stringResource(R.string.step2_authorized)
                        portInput.isNotEmpty() -> stringResource(R.string.step2_needs_pairing)
                        else -> stringResource(R.string.step2_enable_in_settings)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                StatusChip(
                    label = when {
                        isAuthorized -> stringResource(R.string.authorized)
                        portInput.isNotEmpty() -> stringResource(R.string.not_paired)
                        else -> stringResource(R.string.not_connected)
                    },
                    tone = when {
                        isAuthorized -> StatusTone.Success
                        portInput.isNotEmpty() -> StatusTone.Warning
                        else -> StatusTone.Neutral
                    },
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                (context as? MainActivity)?.requestNotificationPermissionAndShow()
                try {
                    context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.developer_options_not_found), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.open_wireless_debugging))
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { showManualPorts = !showManualPorts },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text = if (showManualPorts) {
                    stringResource(R.string.hide_advanced)
                } else {
                    stringResource(R.string.show_advanced)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (showManualPorts) {
            OutlinedTextField(
                value = portInput,
                onValueChange = onPortInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.adb_port_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.step3_activate_title),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusChip(
                label = if (adbReady) {
                    stringResource(R.string.adb_status_ready)
                } else {
                    stringResource(R.string.adb_status_not_ready)
                },
                tone = if (adbReady) StatusTone.Success else StatusTone.Warning,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.step3_activate_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRestoreSlot,
                enabled = !isApplying && adbReady,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.restore_this_sim),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (dualSim) {
                OutlinedButton(
                    onClick = onRestoreBoth,
                    enabled = !isApplying && adbReady,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        stringResource(R.string.restore_both_sims),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ims_status_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
fun ApplyBottomBar(
    hasAdbReady: Boolean,
    isApplying: Boolean,
    dualSim: Boolean,
    selectedLabel: String,
    onSetup: () -> Unit,
    onApplySlot: () -> Unit,
    onApplyAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!hasAdbReady) {
                Button(
                    onClick = onSetup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_wireless_cta))
                }
            } else if (dualSim) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onApplySlot,
                        enabled = !isApplying,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                stringResource(R.string.apply_slot_named, selectedLabel),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onApplyAll,
                        enabled = !isApplying,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.apply_all_sims),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Button(
                    onClick = onApplySlot,
                    enabled = !isApplying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.apply_slot_named, selectedLabel))
                    }
                }
            }
        }
    }
}
