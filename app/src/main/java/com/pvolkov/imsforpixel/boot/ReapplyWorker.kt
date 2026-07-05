package com.pvolkov.imsforpixel.boot

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flyfishxu.kadb.Kadb
import com.pvolkov.imsforpixel.BootReapplyNotification
import com.pvolkov.imsforpixel.ImsStatusNotification
import com.pvolkov.imsforpixel.InstrumentationHelper
import com.pvolkov.imsforpixel.VolteSettings
import com.pvolkov.imsforpixel.adb.AdbDiscovery
import com.pvolkov.imsforpixel.adb.AdbEndpoint
import com.pvolkov.imsforpixel.system.ConnectivityMonitor
import com.pvolkov.imsforpixel.R
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class ReapplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!VolteSettings.hasBootApply(applicationContext)) return Result.success()

        VolteSettings.setBootReapplyStatus(
            applicationContext,
            VolteSettings.BOOT_STATUS_PENDING,
        )

        if (!ConnectivityMonitor(applicationContext).isWifiConnected()) {
            Log.d(TAG, "Not on Wi-Fi yet; will retry")
            return retryOrFail(applicationContext.getString(R.string.boot_fail_no_wifi))
        }

        val port = resolveAdbPort()
        if (port == null) {
            Log.d(TAG, "No ADB connect endpoint; will retry")
            return retryOrFail(applicationContext.getString(R.string.boot_fail_no_adb))
        }

        val authorized = runCatching {
            Kadb.create("127.0.0.1", port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                kadb.shell("echo 1").exitCode == 0
            }
        }.getOrDefault(false)
        if (!authorized) {
            Log.d(TAG, "ADB not authorized on port $port; will retry")
            return retryOrFail(applicationContext.getString(R.string.boot_fail_not_authorized))
        }

        VolteSettings.setLastAdbPort(applicationContext, port)

        val prefs = VolteSettings.prefs(applicationContext)
        val editor = prefs.edit()
        for (slot in 0..1) {
            if (VolteSettings.isApplyOnBoot(prefs, slot)) {
                editor.putBoolean("clear_slot_$slot", false)
            }
        }
        editor.commit()

        return runCatching {
            Kadb.create("127.0.0.1", port, APPLY_TIMEOUT_MS, APPLY_TIMEOUT_MS).use { kadb ->
                val cmd = InstrumentationHelper.instrumentCommand(
                    clear = false,
                    bootReapply = true,
                )
                val response = kadb.shell(cmd)
                check(response.exitCode == 0) { "Exit code ${response.exitCode}: ${response.output}" }
            }
            Log.d(TAG, "Boot reapply succeeded on port $port")
            VolteSettings.setBootReapplyStatus(
                applicationContext,
                VolteSettings.BOOT_STATUS_SUCCESS,
            )
            BootReapplyNotification.cancel(applicationContext)
            ImsStatusNotification.show(applicationContext, isActivate = true)
            Result.success()
        }.getOrElse {
            Log.e(TAG, "Boot reapply failed", it)
            retryOrFail(it.message ?: applicationContext.getString(R.string.boot_fail_unknown))
        }
    }

    private suspend fun resolveAdbPort(): Int? {
        VolteSettings.getLastAdbPort(applicationContext)?.let { cached ->
            val ok = runCatching {
                Kadb.create("127.0.0.1", cached, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                    kadb.shell("echo 1").exitCode == 0
                }
            }.getOrDefault(false)
            if (ok) {
                Log.d(TAG, "Using cached ADB port $cached")
                return cached
            }
        }

        return withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
            AdbDiscovery(applicationContext).discover()
                .filterIsInstance<AdbEndpoint.Connect>()
                .first()
                .port
        }
    }

    private fun retryOrFail(message: String): Result {
        if (runAttemptCount >= MAX_ATTEMPTS) {
            VolteSettings.setBootReapplyStatus(
                applicationContext,
                VolteSettings.BOOT_STATUS_FAILED,
                message,
            )
            BootReapplyNotification.showNeedsAttention(
                applicationContext,
                applicationContext.getString(R.string.boot_reapply_notification_body, message),
            )
            return Result.failure()
        }
        return Result.retry()
    }

    companion object {
        const val WORK_NAME = "reapply_on_boot"
        private const val TAG = "ReapplyWorker"
        private const val DISCOVERY_WINDOW_MS = 90_000L
        private const val AUTH_TIMEOUT_MS = 5_000
        private const val APPLY_TIMEOUT_MS = 90_000
        private const val MAX_ATTEMPTS = 12
    }
}
