package com.pvolkov.imsforpixel

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object ImsStatusNotification {

    fun show(context: Context, isActivate: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        AppNotifications.ensureImsChannel(context, manager)

        val slots = SlotStatus.presentSlots(context)
        val details = slots.map { slot ->
            SlotLine(
                carrier = CarrierInfo.getCarrierLabel(context, slot),
                ims = SlotStatus.imsState(context, slot),
                configApplied = SlotStatus.isConfigApplied(context, slot),
            )
        }

        val registeredCount = details.count { it.ims == SlotStatus.ImsState.Registered }
        val title = when {
            !isActivate -> context.getString(R.string.notification_title_restored)
            details.isNotEmpty() && registeredCount == details.size -> {
                context.getString(R.string.notification_title_all_ok)
            }
            registeredCount > 0 -> context.getString(R.string.notification_title_partial)
            else -> context.getString(R.string.notification_title_none)
        }

        val lines = details.map { formatDetailLine(context, it) }
        val summary = when {
            details.isEmpty() -> title
            details.size == 1 -> formatShortLine(context, details[0])
            else -> context.getString(
                R.string.notification_summary_two_sims,
                formatShortLine(context, details[0]),
                formatShortLine(context, details[1]),
            )
        }
        val hint = if (isActivate) {
            when {
                details.size >= 2 && registeredCount == 1 -> {
                    context.getString(R.string.notification_hint_dual_sim)
                }
                registeredCount == 0 -> context.getString(R.string.notification_hint_none_registered)
                else -> context.getString(R.string.notification_hint_all_ok)
            }
        } else {
            context.getString(R.string.notification_restore_hint)
        }

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(hint)
        lines.forEach { style.addLine(it) }

        val notification = AppNotifications.styled(context, AppNotifications.IMS_CHANNEL)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        manager.notify(AppNotifications.IMS_ID, notification)
    }

    private fun formatShortLine(context: Context, line: SlotLine): String {
        val ims = when (line.ims) {
            SlotStatus.ImsState.Registered -> context.getString(R.string.notification_ims_ok_short)
            SlotStatus.ImsState.NotRegistered -> context.getString(R.string.notification_ims_fail_short)
            SlotStatus.ImsState.Unknown -> context.getString(R.string.ims_unknown_short)
        }
        return context.getString(R.string.notification_short_line, line.carrier, ims)
    }

    private fun formatDetailLine(context: Context, line: SlotLine): String {
        val ims = when (line.ims) {
            SlotStatus.ImsState.Registered -> context.getString(R.string.ims_registered_short)
            SlotStatus.ImsState.NotRegistered -> context.getString(R.string.ims_not_registered_short)
            SlotStatus.ImsState.Unknown -> context.getString(R.string.ims_unknown_short)
        }
        val config = if (line.configApplied) {
            context.getString(R.string.app_optimized)
        } else {
            context.getString(R.string.system_default)
        }
        return context.getString(R.string.notification_detail_line, line.carrier, ims, config)
    }

    private data class SlotLine(
        val carrier: String,
        val ims: SlotStatus.ImsState,
        val configApplied: Boolean,
    )
}
