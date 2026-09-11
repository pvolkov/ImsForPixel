package com.pvolkov.imsforpixel

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.pvolkov.imsforpixel.ui.components.StatusChip
import com.pvolkov.imsforpixel.ui.components.StatusTone
import com.pvolkov.imsforpixel.ui.theme.ImsForPixelTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import com.flyfishxu.kadb.Kadb
import com.pvolkov.imsforpixel.adb.AdbDiscovery
import com.pvolkov.imsforpixel.adb.AdbEndpoint
import com.pvolkov.imsforpixel.system.ConnectivityMonitor

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    companion object {
        @JvmStatic
        var pairingPort: Int? = null
        var onAuthStatusChanged: (() -> Unit)? = null
        var onPermissionsChanged: (() -> Unit)? = null
    }

    private var pairingReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        VolteSettings.migrateDisplaySettings(getSharedPreferences("volte_settings", Context.MODE_PRIVATE))

        // Setup dynamic broadcast receiver for notification pairing code input
        val pairAction = "$packageName.ACTION_PAIR"
        val filter = IntentFilter(pairAction)
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == pairAction) {
                    val remoteInput = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
                    if (remoteInput != null) {
                        val code = remoteInput.getCharSequence("extra_pairing_code")?.toString()?.trim()
                        if (!code.isNullOrEmpty()) {
                            handleNotificationPairing(code)
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pairingReceiver, filter, 2) // RECEIVER_NOTEXPORTED is 2
        } else {
            registerReceiver(pairingReceiver, filter)
        }

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.READ_PHONE_STATE
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 102)
        }

        setContent {
            ImsForPixelTheme {
                val recheckSignal = remember { mutableStateOf(System.currentTimeMillis()) }
                MainScreen(recheckSignal = recheckSignal)
            }
        }
    }

    fun requestNotificationPermissionAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                showPairingNotification()
            }
        } else {
            showPairingNotification()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPairingNotification()
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == 102) {
            onPermissionsChanged?.invoke()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pairing_channel",
                getString(R.string.pairing_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.pairing_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showPairingNotification() {
        createNotificationChannel()
        
        val replyLabel = getString(R.string.pairing_code_hint)
        val remoteInput = androidx.core.app.RemoteInput.Builder("extra_pairing_code")
            .setLabel(replyLabel)
            .build()
            
        val intent = Intent("$packageName.ACTION_PAIR").apply {
            `package` = packageName
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            flags
        )
        
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            getString(R.string.send_pairing_code),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
            
        val notification = NotificationCompat.Builder(this, "pairing_channel")
            .setSmallIcon(R.drawable.ic_stat_ims)
            .setContentTitle(getString(R.string.pairing_channel))
            .setContentText(getString(R.string.pairing_notification_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(202, notification)
    }

    private fun showPairingStatusNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "pairing_channel")
            .setSmallIcon(R.drawable.ic_stat_ims)
            .setContentTitle(getString(R.string.pairing_channel))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(202, notification)
    }

    private fun handleNotificationPairing(code: String) {
        val port = pairingPort
        if (port == null) {
            Toast.makeText(this, getString(R.string.pairing_port_not_found_toast), Toast.LENGTH_LONG).show()
            showPairingStatusNotification(getString(R.string.pairing_failed_no_port))
            return
        }
        
        Toast.makeText(this, getString(R.string.pairing_in_background), Toast.LENGTH_SHORT).show()
        showPairingStatusNotification(getString(R.string.pairing_port_progress, port))

        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                Kadb.pair("127.0.0.1", port, code, filesDir.absolutePath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
            
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@MainActivity, getString(R.string.pairing_success_toast), Toast.LENGTH_LONG).show()
                        showPairingStatusNotification(getString(R.string.pairing_success_return))
                        onAuthStatusChanged?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, getString(R.string.pairing_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
                        showPairingStatusNotification(getString(R.string.pairing_failed, error.message ?: ""))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(202)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(recheckSignal: MutableState<Long> = remember { mutableStateOf(System.currentTimeMillis()) }) {
    val context = LocalContext.current
    var selectedSimSlot by remember { mutableStateOf(0) }
    var portInput by remember {
        mutableStateOf(VolteSettings.getLastAdbPort(context)?.toString() ?: "")
    }
    var isApplying by remember { mutableStateOf(false) }
    val isInstrumenting = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val isWifiConnected = remember { mutableStateOf(false) }
    val isAuthorized = remember { mutableStateOf(false) }
    var authTick by remember { mutableStateOf(0L) }

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
        MainActivity.onAuthStatusChanged = {
            authTick = System.currentTimeMillis()
            recheckSignal.value = System.currentTimeMillis()
        }
        MainActivity.onPermissionsChanged = {
            recheckSignal.value = System.currentTimeMillis()
        }
        onDispose {
            MainActivity.onAuthStatusChanged = null
            MainActivity.onPermissionsChanged = null
        }
    }

    LaunchedEffect(portInput, isForeground, isAuthorized.value) {
        if (!isForeground || !isAuthorized.value) return@LaunchedEffect
        val port = portInput.toIntOrNull() ?: return@LaunchedEffect
        if (port !in 1..65535) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            var activeKadb: Kadb? = null
            try {
                while (true) {
                    if (!isInstrumenting.get()) {
                        try {
                            val kadb = activeKadb ?: Kadb.create("127.0.0.1", port, 5000, 5000).also { activeKadb = it }
                            val appId = BuildConfig.APPLICATION_ID
                            val pathRes = kadb.shell("pm path $appId")
                            if (pathRes.exitCode == 0) {
                                val path = pathRes.output.trim().substringAfter("package:")
                                if (path.isNotEmpty()) {
                                    val queryCmd = "export CLASSPATH=$path; app_process /system/bin ${appId}.ImsQueryTool"
                                    val queryRes = kadb.shell(queryCmd)
                                    if (queryRes.exitCode == 0) {
                                        for (line in queryRes.output.lines()) {
                                            if (!line.startsWith("RESULT:")) continue
                                            val parts = line.split(":")
                                            if (parts.size != 3) continue
                                            val slot = parts[1].toIntOrNull() ?: continue
                                            val isImsRegistered = parts[2].trim().toBoolean()
                                            SlotStatus.writeImsRegistered(context, slot, isImsRegistered)
                                            Log.d("LocalAdb", "Updated slot $slot IMS status: $isImsRegistered")
                                        }
                                        withContext(Dispatchers.Main) {
                                            recheckSignal.value = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("LocalAdb", "Error in background IMS check: ${e.message}")
                            try { activeKadb?.close() } catch (_: Exception) {}
                            activeKadb = null
                        }
                    }
                    delay(8_000)
                }
            } finally {
                try { activeKadb?.close() } catch (_: Exception) {}
            }
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
        if (port == null || port <= 0 || port > 65535) {
            Toast.makeText(context, context.getString(R.string.enable_wireless_debugging_first), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        if (clear) {
            if (slot != null) {
                resetSlotPrefsToDefaults(prefs, slot)
                prefs.edit().putBoolean("clear_slot_$slot", true).commit()
            } else {
                prefs.edit()
                    .putBoolean("clear_slot_0", true)
                    .putBoolean("clear_slot_1", true)
                    .putBoolean("volte_slot_0", true)
                    .putBoolean("volte_slot_1", true)
                    .putBoolean("vonr_slot_0", true)
                    .putBoolean("vonr_slot_1", true)
                    .putBoolean("vowifi_slot_0", true)
                    .putBoolean("vowifi_slot_1", true)
                    .putBoolean("wfc_roaming_slot_0", true)
                    .putBoolean("wfc_roaming_slot_1", true)
                    .putBoolean("ss_ut_slot_0", true)
                    .putBoolean("ss_ut_slot_1", true)
                    .putBoolean("show_ims_slot_0", true)
                    .putBoolean("show_ims_slot_1", true)
                    .putBoolean("allow_apn_slot_0", false)
                    .putBoolean("allow_apn_slot_1", false)
                    .putBoolean("cross_sim_slot_0", false)
                    .putBoolean("cross_sim_slot_1", false)
                    .commit()
            }
        } else {
            if (slot != null) {
                prefs.edit().putBoolean("clear_slot_$slot", false).commit()
            } else {
                prefs.edit()
                    .putBoolean("clear_slot_0", false)
                    .putBoolean("clear_slot_1", false)
                    .commit()
            }
        }

        isApplying = true
        isInstrumenting.set(true)
        scope.launch {
            val result = runInstrument(port, clear, slot)
            isApplying = false
            isInstrumenting.set(false)
            result.fold(
                onSuccess = {
                    recheckSignal.value = System.currentTimeMillis()
                },
                onFailure = { error ->
                    val msgRes = if (clear) R.string.restore_failed else R.string.activate_failed
                    Toast.makeText(context, context.getString(msgRes, error.message ?: ""), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    WirelessDebugEffects(
        portInput = portInput,
        onPortInputChange = { portInput = it },
        isWifiConnected = isWifiConnected,
        isAuthorized = isAuthorized,
        isForeground = isForeground,
        authTick = authTick,
    )

    if (showWirelessDebugSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWirelessDebugSheet = false },
            sheetState = sheetState,
        ) {
            WirelessDebugSetupPanel(
                portInput = portInput,
                onPortInputChange = { portInput = it },
                isWifiConnected = isWifiConnected.value,
                isAuthorized = isAuthorized.value,
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
                isAdbAuthorized = isAuthorized.value && portInput.isNotEmpty(),
                adbPort = portInput,
                refreshKey = recheckSignal.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            )
        }
    }

    val hasAdbReady = isAuthorized.value && portInput.isNotEmpty()
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val setupReady = isWifiConnected.value && hasAdbReady

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
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
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                SimStatusOverview(
                    recheckSignal = recheckSignal,
                    visibleSlots = visibleSlots,
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


private fun resetSlotPrefsToDefaults(prefs: android.content.SharedPreferences, slot: Int) {
    prefs.edit()
        .putBoolean("clear_slot_$slot", true)
        .putBoolean("volte_slot_$slot", true)
        .putBoolean("vonr_slot_$slot", true)
        .putBoolean("vowifi_slot_$slot", true)
        .putBoolean("wfc_roaming_slot_$slot", true)
        .putBoolean("ss_ut_slot_$slot", true)
        .putBoolean("show_ims_slot_$slot", true)
        .putBoolean("allow_apn_slot_$slot", false)
        .putBoolean("cross_sim_slot_$slot", false)
        .commit()
}

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
            Text(
                text = stringResource(R.string.sim_status_overview),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (visibleSlots.size <= 1) {
                val slot = visibleSlots.firstOrNull() ?: 0
                SimStatusTile(
                    modifier = Modifier.fillMaxWidth(),
                    title = carrierOf(slot),
                    configLabel = if (appliedOf(slot)) {
                        stringResource(R.string.app_optimized)
                    } else {
                        stringResource(R.string.system_default)
                    },
                    configTone = if (appliedOf(slot)) StatusTone.Success else StatusTone.Neutral,
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
                            configLabel = if (appliedOf(slot)) {
                                stringResource(R.string.app_optimized)
                            } else {
                                stringResource(R.string.system_default)
                            },
                            configTone = if (appliedOf(slot)) StatusTone.Success else StatusTone.Neutral,
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
fun ConfigPanel(
    slotIndex: Int,
    onConfigChanged: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(slotIndex) {
        context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
    }

    var voLteEnabled by remember(slotIndex) { mutableStateOf(prefs.getBoolean("volte_slot_$slotIndex", true)) }
    var voNrEnabled by remember(slotIndex) { mutableStateOf(prefs.getBoolean("vonr_slot_$slotIndex", true)) }
    var voWifiEnabled by remember(slotIndex) { mutableStateOf(prefs.getBoolean("vowifi_slot_$slotIndex", true)) }
    var wfcRoamingEnabled by remember(slotIndex) { mutableStateOf(prefs.getBoolean("wfc_roaming_slot_$slotIndex", true)) }
    var ssUtEnabled by remember(slotIndex) { mutableStateOf(prefs.getBoolean("ss_ut_slot_$slotIndex", true)) }
    var carrierLabel by remember(slotIndex) { mutableStateOf(CarrierInfo.getCarrierLabel(context, slotIndex)) }

    LaunchedEffect(slotIndex) {
        carrierLabel = CarrierInfo.getCarrierLabel(context, slotIndex)
    }

    SettingsSectionCard(title = stringResource(R.string.carrier_settings_named, carrierLabel)) {
        ToggleRow(stringResource(R.string.volte_title), stringResource(R.string.volte_desc), voLteEnabled) {
            voLteEnabled = it
            prefs.edit().putBoolean("volte_slot_$slotIndex", it).putBoolean("clear_slot_$slotIndex", false).commit()
            onConfigChanged()
        }
        ToggleRow(stringResource(R.string.vonr_title), stringResource(R.string.vonr_desc), voNrEnabled) {
            voNrEnabled = it
            prefs.edit().putBoolean("vonr_slot_$slotIndex", it).putBoolean("clear_slot_$slotIndex", false).commit()
            onConfigChanged()
        }
        ToggleRow(stringResource(R.string.vowifi_title), stringResource(R.string.vowifi_desc), voWifiEnabled) {
            voWifiEnabled = it
            prefs.edit().putBoolean("vowifi_slot_$slotIndex", it).putBoolean("clear_slot_$slotIndex", false).commit()
            onConfigChanged()
        }
        ToggleRow(stringResource(R.string.wfc_roaming_title), stringResource(R.string.wfc_roaming_desc), wfcRoamingEnabled) {
            wfcRoamingEnabled = it
            prefs.edit().putBoolean("wfc_roaming_slot_$slotIndex", it).putBoolean("clear_slot_$slotIndex", false).commit()
            onConfigChanged()
        }
        ToggleRow(stringResource(R.string.ss_ut_title), stringResource(R.string.ss_ut_desc), ssUtEnabled) {
            ssUtEnabled = it
            prefs.edit().putBoolean("ss_ut_slot_$slotIndex", it).putBoolean("clear_slot_$slotIndex", false).commit()
            onConfigChanged()
        }
        Text(
            text = stringResource(R.string.override_persists_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun DisplaySettingsPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE) }
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
private fun SettingsSectionCard(
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

@Composable
fun WirelessDebugEffects(
    portInput: String,
    onPortInputChange: (String) -> Unit,
    isWifiConnected: MutableState<Boolean>,
    isAuthorized: MutableState<Boolean>,
    isForeground: Boolean,
    authTick: Long,
) {
    val context = LocalContext.current

    LaunchedEffect(portInput, isForeground, authTick) {
        if (!isForeground) return@LaunchedEffect
        val port = portInput.toIntOrNull()
        if (port == null || port !in 1..65535) {
            isAuthorized.value = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                Kadb.create("127.0.0.1", port, 3000, 3000).use { kadb ->
                    val response = kadb.shell("echo 1")
                    val authorized = response.exitCode == 0
                    withContext(Dispatchers.Main) {
                        isAuthorized.value = authorized
                    }
                    if (authorized) {
                        VolteSettings.setLastAdbPort(context, port)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isAuthorized.value = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
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

    LaunchedEffect(isForeground, isAuthorized.value) {
        if (!isForeground || isAuthorized.value) return@LaunchedEffect
        AdbDiscovery(context).discover().collect { endpoint ->
            when (endpoint) {
                is AdbEndpoint.Connect -> onPortInputChange(endpoint.port.toString())
                is AdbEndpoint.Pairing -> MainActivity.pairingPort = endpoint.port
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
    isApplying: Boolean,
    dualSim: Boolean,
    onRestoreSlot: () -> Unit,
    onRestoreBoth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showManualPorts by remember { mutableStateOf(false) }
    val hasResolvedPort = portInput.isNotEmpty() && isAuthorized
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState),
    ) {
        Text(
            stringResource(R.string.wireless_debug_activation),
            style = MaterialTheme.typography.titleLarge,
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
                } catch (e: Exception) {
                    try {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (e2: Exception) {
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
                label = if (hasResolvedPort) {
                    stringResource(R.string.adb_status_ready)
                } else {
                    stringResource(R.string.adb_status_not_ready)
                },
                tone = if (hasResolvedPort) StatusTone.Success else StatusTone.Warning,
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
                enabled = !isApplying && hasResolvedPort,
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
                    enabled = !isApplying && hasResolvedPort,
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
private fun ApplyBottomBar(
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
                if (dualSim) {
                    TextButton(
                        onClick = onApplyAll,
                        enabled = !isApplying,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.apply_all_sims))
                    }
                }
            }
        }
    }
}

@Composable
private fun SimStatusTile(
    title: String,
    configLabel: String,
    configTone: StatusTone,
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusChip(label = configLabel, tone = configTone)
            Spacer(modifier = Modifier.height(4.dp))
            StatusChip(label = imsLabel, tone = imsTone)
        }
    }
}
