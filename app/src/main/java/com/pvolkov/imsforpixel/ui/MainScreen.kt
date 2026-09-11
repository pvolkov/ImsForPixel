package com.pvolkov.imsforpixel.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flyfishxu.kadb.Kadb
import com.pvolkov.imsforpixel.AdbImsQuery
import com.pvolkov.imsforpixel.CarrierInfo
import com.pvolkov.imsforpixel.DiagnosticsPanel
import com.pvolkov.imsforpixel.InstrumentationHelper
import com.pvolkov.imsforpixel.PairingSession
import com.pvolkov.imsforpixel.R
import com.pvolkov.imsforpixel.SlotStatus
import com.pvolkov.imsforpixel.VolteSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(recheckSignal: MutableState<Long> = remember { mutableStateOf(System.currentTimeMillis()) }) {
    val context = LocalContext.current
    var selectedSimSlot by remember { mutableStateOf(0) }
    var portInput by remember {
        mutableStateOf(VolteSettings.getLastAdbPort(context)?.toString() ?: "")
    }
    var portLiveThisSession by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var isRefreshingIms by remember { mutableStateOf(false) }
    val isWifiConnected = remember { mutableStateOf(false) }
    val isAuthorized = remember { mutableStateOf(VolteSettings.isAdbPaired(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isForeground = true
                Lifecycle.Event.ON_PAUSE -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isForeground) {
        if (isForeground) {
            recheckSignal.value = System.currentTimeMillis()
        }
    }

    DisposableEffect(Unit) {
        PairingSession.onAuthStatusChanged = {
            isAuthorized.value = VolteSettings.isAdbPaired(context)
            recheckSignal.value = System.currentTimeMillis()
        }
        PairingSession.onPermissionsChanged = {
            recheckSignal.value = System.currentTimeMillis()
        }
        onDispose {
            PairingSession.onAuthStatusChanged = null
            PairingSession.onPermissionsChanged = null
        }
    }

    val scope = rememberCoroutineScope()
    var showWirelessDebugSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    suspend fun runInstrument(port: Int, clear: Boolean, slot: Int?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Kadb.create("127.0.0.1", port, 120000, 120000).use { kadb ->
                    val cmd = InstrumentationHelper.instrumentCommand(clear = clear, slot = slot)
                    val response = kadb.shell(cmd)
                    if (response.exitCode == 0) {
                        VolteSettings.setLastAdbPort(context, port)
                        VolteSettings.setAdbPaired(context, true)
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Exit code ${response.exitCode}: ${response.output}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun startInstrument(clear: Boolean, slot: Int?) {
        val port = portInput.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(context, context.getString(R.string.enable_wireless_debugging_first), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences(VolteSettings.PREFS_NAME, Context.MODE_PRIVATE)
        if (clear) {
            if (slot != null) {
                VolteSettings.resetSlotToDefaults(prefs, slot)
            } else {
                VolteSettings.resetBothSlotsToDefaults(prefs)
            }
        } else {
            VolteSettings.markSlotsForApply(prefs, slot)
        }

        isApplying = true
        scope.launch {
            val result = runInstrument(port, clear, slot)
            isApplying = false
            result.fold(
                onSuccess = {
                    isAuthorized.value = true
                    recheckSignal.value = System.currentTimeMillis()
                },
                onFailure = { error ->
                    val msgRes = if (clear) R.string.restore_failed else R.string.activate_failed
                    Toast.makeText(context, context.getString(msgRes, error.message ?: ""), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    fun refreshImsStatus() {
        val port = portInput.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(context, context.getString(R.string.enable_wireless_debugging_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (isApplying || isRefreshingIms) return
        isRefreshingIms = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AdbImsQuery.query(context, port) }
            isRefreshingIms = false
            result.fold(
                onSuccess = {
                    isAuthorized.value = true
                    recheckSignal.value = System.currentTimeMillis()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.refresh_ims_failed, error.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    WirelessDebugEffects(
        onPortDiscovered = { port ->
            portInput = port.toString()
            portLiveThisSession = true
        },
        isWifiConnected = isWifiConnected,
        isForeground = isForeground,
    )

    val hasAdbReady = isAuthorized.value && portInput.isNotEmpty() && portLiveThisSession

    if (showWirelessDebugSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWirelessDebugSheet = false },
            sheetState = sheetState,
        ) {
            WirelessDebugSetupPanel(
                portInput = portInput,
                onPortInputChange = {
                    portInput = it
                    portLiveThisSession = it.toIntOrNull()?.let { port -> port in 1..65535 } == true
                },
                isWifiConnected = isWifiConnected.value,
                isAuthorized = isAuthorized.value,
                adbReady = hasAdbReady,
                isApplying = isApplying,
                dualSim = SlotStatus.presentSlots(context).size > 1,
                onRestoreSlot = { startInstrument(clear = true, slot = selectedSimSlot) },
                onRestoreBoth = { startInstrument(clear = true, slot = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
            )
        }
    }

    if (showDiagnosticsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDiagnosticsSheet = false },
            sheetState = diagnosticsSheetState,
        ) {
            DiagnosticsPanel(
                isWifiConnected = isWifiConnected.value,
                isAdbAuthorized = hasAdbReady,
                adbPort = if (portLiveThisSession) portInput else "",
                refreshKey = recheckSignal.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            )
        }
    }

    val visibleSlots = remember(recheckSignal.value) {
        SlotStatus.presentSlots(context)
    }
    LaunchedEffect(visibleSlots) {
        if (visibleSlots.isNotEmpty() && selectedSimSlot !in visibleSlots) {
            selectedSimSlot = visibleSlots.first()
        }
    }
    val dualSim = visibleSlots.size > 1
    val selectedLabel = CarrierInfo.getCarrierLabel(context, selectedSimSlot)
    val setupReady = isWifiConnected.value && hasAdbReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { showDiagnosticsSheet = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.diagnostics_cd),
                        )
                    }
                    Box {
                        if (setupReady) {
                            IconButton(onClick = { showWirelessDebugSheet = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.wireless_debug_settings_cd),
                                )
                            }
                        } else {
                            FilledTonalIconButton(onClick = { showWirelessDebugSheet = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.wireless_debug_settings_cd),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ApplyBottomBar(
                hasAdbReady = hasAdbReady,
                isApplying = isApplying,
                dualSim = dualSim,
                selectedLabel = selectedLabel,
                onSetup = { showWirelessDebugSheet = true },
                onApplySlot = { startInstrument(clear = false, slot = selectedSimSlot) },
                onApplyAll = { startInstrument(clear = false, slot = null) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                SimStatusOverview(
                    recheckSignal = recheckSignal,
                    visibleSlots = visibleSlots,
                    isRefreshing = isRefreshingIms,
                    onRefresh = { refreshImsStatus() },
                )
            }

            item {
                SimSelectorTabs(
                    slots = visibleSlots,
                    selectedSlot = selectedSimSlot,
                    onSlotSelected = { selectedSimSlot = it },
                )
            }

            item {
                ConfigPanel(
                    slotIndex = selectedSimSlot,
                    onConfigChanged = {},
                )
            }

            item {
                DisplaySettingsPanel()
            }
        }
    }
}
