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

        val slots = SlotStatus.presentSlots(context)
        val details = slots.map { slot ->
            SlotLine(
                carrier = CarrierInfo.getCarrierLabel(context, slot),
                imsRegistered = SlotStatus.imsState(context, slot) == SlotStatus.ImsState.Registered,
                configApplied = SlotStatus.isConfigApplied(context, slot),
            )
        }

        val registeredCount = details.count { it.imsRegistered }
        val title = when {
            !isActivate -> context.getString(R.string.notification_title_restored)
            details.isNotEmpty() && registeredCount == details.size -> {
                context.getString(R.string.notification_title_all_ok)
            }
            registeredCount > 0 -> context.getString(R.string.notification_title_partial)
            else -> context.getString(R.string.notification_title_none)
        }

        val lines = details.map {
            formatDetailLine(context, it.carrier, it.imsRegistered, it.configApplied)
        }
        val summary = when {
            details.isEmpty() -> title
            details.size == 1 -> formatShortLine(context, details[0].carrier, details[0].imsRegistered)
            else -> context.getString(
                R.string.notification_summary_two_sims,
                formatShortLine(context, details[0].carrier, details[0].imsRegistered),
                formatShortLine(context, details[1].carrier, details[1].imsRegistered),
            )
        }

        val bigText = buildString {
            append(lines.joinToString("\n"))
            append("\n\n")
            if (isActivate) {
                append(
                    when {
                        details.size >= 2 && registeredCount == 1 -> {
                            context.getString(R.string.notification_hint_dual_sim)
                        }
                        registeredCount == 0 -> context.getString(R.string.notification_hint_none_registered)
                        else -> context.getString(R.string.notification_hint_all_ok)
                    }
                )
            } else {
                append(context.getString(R.string.notification_restore_hint))
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ims)
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

    private data class SlotLine(
        val carrier: String,
        val imsRegistered: Boolean,
        val configApplied: Boolean,
    )
}
