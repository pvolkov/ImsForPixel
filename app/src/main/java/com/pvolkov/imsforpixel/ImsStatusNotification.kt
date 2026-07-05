package com.pvolkov.imsforpixel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object ImsStatusNotification {

    private const val NOTIFICATION_ID = 203
    private const val CHANNEL_ID = "ims_status_channel"

    fun show(context: Context, isActivate: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val slot0Ims = readFlag(context, "ims_status_0.txt")
        val slot1Ims = readFlag(context, "ims_status_1.txt")
        val slot0Config = readFlag(context, "config_applied_0.txt")
        val slot1Config = readFlag(context, "config_applied_1.txt")
        val carrier0 = CarrierInfo.getCarrierLabel(context, 0)
        val carrier1 = CarrierInfo.getCarrierLabel(context, 1)

        val registeredCount = listOf(slot0Ims, slot1Ims).count { it }

        val title = when {
            !isActivate -> context.getString(R.string.notification_title_restored)
            registeredCount >= 2 -> context.getString(R.string.notification_title_all_ok)
            registeredCount == 1 -> context.getString(R.string.notification_title_partial)
            else -> context.getString(R.string.notification_title_none)
        }

        val line0 = formatDetailLine(context, carrier0, slot0Ims, slot0Config)
        val line1 = formatDetailLine(context, carrier1, slot1Ims, slot1Config)

        val summary = context.getString(
            R.string.notification_summary_two_sims,
            formatShortLine(context, carrier0, slot0Ims),
            formatShortLine(context, carrier1, slot1Ims),
        )

        val bigText = buildString {
            append(line0)
            append('\n')
            append(line1)
            if (isActivate) {
                append("\n\n")
                append(
                    when {
                        registeredCount == 1 -> context.getString(R.string.notification_hint_dual_sim)
                        registeredCount == 0 -> context.getString(R.string.notification_hint_none_registered)
                        else -> context.getString(R.string.notification_hint_all_ok)
                    }
                )
            } else {
                append("\n\n")
                append(context.getString(R.string.notification_restore_hint))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ims_status_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatShortLine(context: Context, carrier: String, imsRegistered: Boolean): String {
        val ims = if (imsRegistered) {
            context.getString(R.string.notification_ims_ok_short)
        } else {
            context.getString(R.string.notification_ims_fail_short)
        }
        return "$carrier $ims"
    }

    private fun formatDetailLine(
        context: Context,
        carrier: String,
        imsRegistered: Boolean,
        configApplied: Boolean,
    ): String {
        val ims = if (imsRegistered) {
            context.getString(R.string.notification_ims_registered)
        } else {
            context.getString(R.string.notification_ims_not_registered)
        }
        val config = if (configApplied) {
            context.getString(R.string.notification_config_applied)
        } else {
            context.getString(R.string.notification_config_default)
        }
        return context.getString(R.string.notification_detail_line, carrier, ims, config)
    }

    private fun readFlag(context: Context, fileName: String): Boolean {
        return try {
            java.io.File(context.filesDir, fileName).readText().trim().toBoolean()
        } catch (_: Exception) {
            false
        }
    }
}
