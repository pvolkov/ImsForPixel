package com.svenuks.imsforpixel.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.svenuks.imsforpixel.VolteSettings

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!VolteSettings.hasBootApply(context)) return

        VolteSettings.setBootReapplyStatus(context, VolteSettings.BOOT_STATUS_PENDING)

        val request = OneTimeWorkRequestBuilder<ReapplyWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ReapplyWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
