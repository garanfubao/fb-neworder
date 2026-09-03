package com.fubao.orderdisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifications {
    const val CH_SERVICE = "fubao_service"
    const val CH_NEW = "fubao_new_order"
    const val SERVICE_NOTIF_ID = 1001
    private var newOrderCounter = 2000

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)

        val service = NotificationChannel(
            CH_SERVICE, "Ket noi don hang", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Giu app chay ngam de nhan don" }

        val newOrder = NotificationChannel(
            CH_NEW, "Don moi", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Bao khi co don moi"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            // Am thanh do AlarmPlayer phat lap lai (luong ALARM), nen tat sound cua channel de khoi keu 2 lop.
            setSound(null, null)
        }

        nm.createNotificationChannel(service)
        nm.createNotificationChannel(newOrder)
    }

    /** Thong bao co dinh cua foreground service. */
    fun buildServiceNotification(ctx: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setContentTitle("Fubao TingTing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateServiceNotification(ctx: Context, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java)
                .notify(SERVICE_NOTIF_ID, buildServiceNotification(ctx, text))
        }
    }

    /** Banner "Don moi" bat len ke ca khi dang o app khac. */
    fun showNewOrder(ctx: Context, o: Order) {
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = buildString {
            append(o.items ?: "Don moi")
            o.address?.let { append("\n📍 ").append(it) }
            o.phone?.let { append("\n📞 ").append(it) }
        }
        val n = NotificationCompat.Builder(ctx, CH_NEW)
            .setContentTitle("🔔 CÓ ĐƠN MỚI")
            .setContentText(o.items ?: "Đơn mới")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(newOrderCounter++, n)
    }
}
