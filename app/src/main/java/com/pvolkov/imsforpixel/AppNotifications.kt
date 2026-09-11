package com.pvolkov.imsforpixel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

object AppNotifications {
    const val PAIRING_ID = 202
    const val IMS_ID = 203
    const val PAIRING_CHANNEL = "pairing_v2"
    const val IMS_CHANNEL = "ims_status_v2"

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun styled(context: Context, channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_ims)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentIntent(openAppIntent(context))
    }

    fun ensureImsChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            IMS_CHANNEL,
            context.getString(R.string.ims_status_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.ims_status_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensurePairingChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            PAIRING_CHANNEL,
            context.getString(R.string.pairing_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.pairing_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun showPairingInput(context: Context, replyPendingIntent: PendingIntent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensurePairingChannel(context, manager)

        val remoteInput = RemoteInput.Builder("extra_pairing_code")
            .setLabel(context.getString(R.string.pairing_code_hint))
            .build()
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_send,
            context.getString(R.string.send_pairing_code),
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val notification = styled(context, PAIRING_CHANNEL)
            .setContentTitle(context.getString(R.string.pairing_notification_title))
            .setContentText(context.getString(R.string.pairing_notification_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.pairing_notification_body)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
            .build()
        manager.notify(PAIRING_ID, notification)
    }

    fun showPairingStatus(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensurePairingChannel(context, manager)
        val notification = styled(context, PAIRING_CHANNEL)
            .setContentTitle(context.getString(R.string.pairing_status_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(PAIRING_ID, notification)
    }
}
