package com.pvolkov.imsforpixel

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.RemoteInput
import androidx.lifecycle.lifecycleScope
import com.flyfishxu.kadb.Kadb
import com.pvolkov.imsforpixel.ui.MainScreen
import com.pvolkov.imsforpixel.ui.theme.ImsForPixelTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var pairingReceiver: BroadcastReceiver? = null
    private var pendingPairingNotification = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (pendingPairingNotification) {
            pendingPairingNotification = false
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                result[Manifest.permission.POST_NOTIFICATIONS] == true ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showPairingNotification()
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
            }
        }
        PairingSession.onPermissionsChanged?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        VolteSettings.migrateDisplaySettings(getSharedPreferences(VolteSettings.PREFS_NAME, Context.MODE_PRIVATE))
        registerPairingReceiver()
        requestMissingRuntimePermissions()

        setContent {
            ImsForPixelTheme {
                val recheckSignal = remember { mutableStateOf(System.currentTimeMillis()) }
                MainScreen(recheckSignal = recheckSignal)
            }
        }
    }

    fun requestNotificationPermissionAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPairingNotification = true
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            showPairingNotification()
        }
    }

    private fun requestMissingRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.READ_PHONE_STATE
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun registerPairingReceiver() {
        val pairAction = "$packageName.ACTION_PAIR"
        val filter = IntentFilter(pairAction)
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != pairAction) return
                val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
                val code = remoteInput.getCharSequence("extra_pairing_code")?.toString()?.trim()
                if (!code.isNullOrEmpty()) {
                    handleNotificationPairing(code)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pairingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pairingReceiver, filter)
        }
    }

    private fun showPairingNotification() {
        val intent = Intent("$packageName.ACTION_PAIR").apply {
            `package` = packageName
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        AppNotifications.showPairingInput(this, replyPendingIntent)
    }

    private fun handleNotificationPairing(code: String) {
        val port = PairingSession.pairingPort
        if (port == null) {
            Toast.makeText(this, getString(R.string.pairing_port_not_found_toast), Toast.LENGTH_LONG).show()
            AppNotifications.showPairingStatus(this, getString(R.string.pairing_failed_no_port))
            return
        }

        Toast.makeText(this, getString(R.string.pairing_in_background), Toast.LENGTH_SHORT).show()
        AppNotifications.showPairingStatus(this, getString(R.string.pairing_port_progress, port))

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
                        VolteSettings.setAdbPaired(this@MainActivity, true)
                        Toast.makeText(this@MainActivity, getString(R.string.pairing_success_toast), Toast.LENGTH_LONG).show()
                        AppNotifications.showPairingStatus(this@MainActivity, getString(R.string.pairing_success_return))
                        PairingSession.onAuthStatusChanged?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.pairing_failed, error.message ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                        AppNotifications.showPairingStatus(
                            this@MainActivity,
                            getString(R.string.pairing_failed, error.message ?: ""),
                        )
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(AppNotifications.PAIRING_ID)
    }
}
