package com.example.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_SERVER_DOWN = "channel_server_down"
    const val CHANNEL_RECOVERED = "channel_recovered"
    const val CHANNEL_HIGH_USAGE = "channel_high_usage"
    const val CHANNEL_PANEL_UNREACHABLE = "channel_panel_unreachable"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelDown = NotificationChannel(
                CHANNEL_SERVER_DOWN,
                context.getString(R.string.channel_server_down_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_server_down_desc)
                enableVibration(true)
            }

            val channelRecovered = NotificationChannel(
                CHANNEL_RECOVERED,
                context.getString(R.string.channel_recovered_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_recovered_desc)
            }

            val channelHighUsage = NotificationChannel(
                CHANNEL_HIGH_USAGE,
                context.getString(R.string.channel_high_usage_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_high_usage_desc)
                enableVibration(true)
            }

            val channelPanelUnreachable = NotificationChannel(
                CHANNEL_PANEL_UNREACHABLE,
                context.getString(R.string.channel_panel_unreachable_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_panel_unreachable_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(channelDown, channelRecovered, channelHighUsage, channelPanelUnreachable)
            )
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendServerNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        panelId: String,
        serverIdentifier: String,
        notificationId: Int
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PANEL_ID, panelId)
            putExtra(MainActivity.EXTRA_SERVER_ID, serverIdentifier)
            putExtra(MainActivity.EXTRA_NAVIGATE_DASHBOARD, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_orca_transparent)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (channelId == CHANNEL_RECOVERED) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun sendPanelNotification(
        context: Context,
        title: String,
        message: String,
        panelId: String,
        notificationId: Int
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PANEL_ID, panelId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PANEL_UNREACHABLE)
            .setSmallIcon(R.drawable.ic_orca_transparent)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun sendTestNotifications(context: Context) {
        createNotificationChannels(context)
        sendServerNotification(
            context = context,
            channelId = CHANNEL_HIGH_USAGE,
            title = "Test: High usage alert",
            message = "Survival-01 (Production Node) CPU usage at 94.2% (Threshold: 90%)",
            panelId = "test_panel",
            serverIdentifier = "test_server",
            notificationId = 10001
        )
    }
}
