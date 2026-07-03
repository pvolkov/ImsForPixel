package com.svenuks.imsforpixel.boot

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flyfishxu.kadb.Kadb
import com.svenuks.imsforpixel.VolteSettings
import com.svenuks.imsforpixel.adb.AdbDiscovery
import com.svenuks.imsforpixel.adb.AdbEndpoint
import com.svenuks.imsforpixel.system.ConnectivityMonitor
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class ReapplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!VolteSettings.hasBootApply(applicationContext)) return Result.success()

        if (!ConnectivityMonitor(applicationContext).isWifiConnected()) {
            Log.d(TAG, "Not on Wi-Fi yet; will retry")
            return Result.retry()
        }

        val port = withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
            AdbDiscovery(applicationContext).discover()
                .filterIsInstance<AdbEndpoint.Connect>()
                .first()
                .port
        }
        if (port == null) {
            Log.d(TAG, "No ADB connect endpoint within window; will retry")
            return Result.retry()
        }

        val authorized = runCatching {
            Kadb.create("127.0.0.1", port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                kadb.shell("echo 1").exitCode == 0
            }
        }.getOrDefault(false)
        if (!authorized) {
            Log.d(TAG, "ADB not authorized on port $port; will retry")
            return Result.retry()
        }

        VolteSettings.prefs(applicationContext).edit()
            .putBoolean("clear_slot_0", false)
            .putBoolean("clear_slot_1", false)
            .commit()

        return runCatching {
            Kadb.create("127.0.0.1", port, APPLY_TIMEOUT_MS, APPLY_TIMEOUT_MS).use { kadb ->
                val cmd = "nohup am instrument -w -e clear false com.svenuks.imsforpixel/com.svenuks.imsforpixel.BrokerInstrumentation > /dev/null 2>&1 &"
                val response = kadb.shell(cmd)
                check(response.exitCode == 0) { "Exit code ${response.exitCode}: ${response.output}" }
            }
            Log.d(TAG, "Boot reapply succeeded on port $port")
            Result.success()
        }.getOrElse {
            Log.e(TAG, "Boot reapply failed", it)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "reapply_on_boot"
        private const val TAG = "ReapplyWorker"
        private const val DISCOVERY_WINDOW_MS = 60_000L
        private const val AUTH_TIMEOUT_MS = 3_000
        private const val APPLY_TIMEOUT_MS = 90_000
    }
}
