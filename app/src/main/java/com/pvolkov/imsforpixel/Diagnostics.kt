package com.pvolkov.imsforpixel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

data class DiagnosticItem(
    val label: String,
    val passed: Boolean,
    val detail: String? = null,
    val warning: Boolean = false,
)

object DiagnosticsCollector {

    fun collect(
        context: Context,
        isWifiConnected: Boolean,
        isAdbAuthorized: Boolean,
        adbPort: String,
    ): List<DiagnosticItem> {
        val prefs = VolteSettings.prefs(context)
        val items = mutableListOf<DiagnosticItem>()

        items += DiagnosticItem(
            label = context.getString(R.string.diag_wifi),
            passed = isWifiConnected,
            detail = if (isWifiConnected) null else context.getString(R.string.diag_wifi_fail),
        )

        val portValid = adbPort.toIntOrNull()?.let { it in 1..65535 } == true
        items += DiagnosticItem(
            label = context.getString(R.string.diag_adb_port),
            passed = portValid,
            detail = if (portValid) adbPort else context.getString(R.string.diag_adb_port_fail),
        )

        items += DiagnosticItem(
            label = context.getString(R.string.diag_adb_auth),
            passed = isAdbAuthorized,
            detail = if (isAdbAuthorized) null else context.getString(R.string.diag_adb_auth_fail),
        )

        for (slot in 0..1) {
            val carrier = CarrierInfo.getCarrierLabel(context, slot)
            val configApplied = readFlag(context, "config_applied_${slot}.txt")
            val imsRegistered = readFlag(context, "ims_status_${slot}.txt")
            val volte = prefs.getBoolean("volte_slot_$slot", true)

            items += DiagnosticItem(
                label = context.getString(R.string.diag_config_slot, carrier),
                passed = configApplied,
                detail = if (configApplied) {
                    context.getString(R.string.diag_config_applied)
                } else {
                    context.getString(R.string.diag_config_default)
                },
            )
            items += DiagnosticItem(
                label = context.getString(R.string.diag_ims_slot, carrier),
                passed = imsRegistered,
                detail = if (imsRegistered) {
                    context.getString(R.string.diag_ims_ok)
                } else {
                    context.getString(R.string.diag_ims_fail)
                },
                warning = configApplied && !imsRegistered,
            )
            items += DiagnosticItem(
                label = context.getString(R.string.diag_volte_pref_slot, carrier),
                passed = volte,
                detail = if (volte) null else context.getString(R.string.diag_volte_pref_off),
            )
        }

        if (VolteSettings.hasBootApply(context)) {
            val status = VolteSettings.getBootReapplyStatus(context)
            val passed = status == VolteSettings.BOOT_STATUS_SUCCESS
            items += DiagnosticItem(
                label = context.getString(R.string.diag_boot_reapply),
                passed = passed,
                detail = VolteSettings.getBootReapplyStatusLabel(context),
                warning = status == VolteSettings.BOOT_STATUS_FAILED ||
                    status == VolteSettings.BOOT_STATUS_PENDING,
            )
        }

        return items
    }

    fun buildReport(
        context: Context,
        isWifiConnected: Boolean,
        isAdbAuthorized: Boolean,
        adbPort: String,
    ): String {
        val items = collect(context, isWifiConnected, isAdbAuthorized, adbPort)
        return buildString {
            appendLine("IMS for Pixel — diagnostic report")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            for (item in items) {
                val mark = when {
                    item.passed -> "OK"
                    item.warning -> "WARN"
                    else -> "FAIL"
                }
                append("$mark — ${item.label}")
                item.detail?.let { append(": $it") }
                appendLine()
            }
        }
    }

    private fun readFlag(context: Context, fileName: String): Boolean {
        return try {
            java.io.File(context.filesDir, fileName).readText().trim().toBoolean()
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun DiagnosticsPanel(
    isWifiConnected: Boolean,
    isAdbAuthorized: Boolean,
    adbPort: String,
    onRetryBootReapply: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items = remember(isWifiConnected, isAdbAuthorized, adbPort) {
        DiagnosticsCollector.collect(context, isWifiConnected, isAdbAuthorized, adbPort)
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
    ) {
        Text(
            stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.diagnostics_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        items.forEach { item ->
            DiagnosticRow(item)
            Spacer(modifier = Modifier.height(8.dp))
        }

        val bootFailed = VolteSettings.getBootReapplyStatus(context) == VolteSettings.BOOT_STATUS_FAILED
        if (bootFailed && onRetryBootReapply != null) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.diag_boot_retry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetryBootReapply,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diag_boot_retry))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val report = DiagnosticsCollector.buildReport(
                    context,
                    isWifiConnected,
                    isAdbAuthorized,
                    adbPort,
                )
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("IMS diagnostic report", report))
                Toast.makeText(context, context.getString(R.string.diag_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.diag_copy_report))
        }
    }
}

@Composable
private fun DiagnosticRow(item: DiagnosticItem) {
    val icon = when {
        item.passed -> Icons.Default.CheckCircle
        item.warning -> Icons.Default.Warning
        else -> Icons.Default.Error
    }
    val tint = when {
        item.passed -> MaterialTheme.colorScheme.primary
        item.warning -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            item.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
