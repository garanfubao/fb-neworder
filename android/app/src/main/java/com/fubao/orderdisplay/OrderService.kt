package com.fubao.orderdisplay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OrderService : Service() {

    private lateinit var client: OkHttpClient
    private var ws: WebSocket? = null
    private var reconnecting = false
    private var stopped = false
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val stopAlarmRunnable = Runnable { AlarmPlayer.stop() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            Notifications.SERVICE_NOTIF_ID,
            Notifications.buildServiceNotification(this, "Đang kết nối...")
        )
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fubao:ws").apply {
            setReferenceCounted(false)
            acquire()
        }
        client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            main.removeCallbacks(stopAlarmRunnable)
            AlarmPlayer.stop()
        }
        return START_STICKY
    }

    private fun setStatus(s: String) {
        OrderStore.setStatus(s)
        Notifications.updateServiceNotification(this, s)
    }

    private fun connect() {
        if (stopped) return
        val url = Prefs.wsUrl(this) + "?key=" +
            URLEncoder.encode(Prefs.apiKey(this), "UTF-8")
        val req = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            setStatus("URL sai: kiem tra Cai dat")
            return
        }
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                setStatus("Đã kết nối ✓")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("OrderService", "onFailure: ${t.message}")
                setStatus("Mất kết nối, đang thử lại...")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code == 4001) {
                    setStatus("Sai API key (kiem tra Cai dat)")
                    return
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnecting || stopped) return
        reconnecting = true
        main.postDelayed({
            reconnecting = false
            connect()
        }, 3000)
    }

    private fun handle(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "snapshot" -> {
                    val arr = json.getJSONArray("orders")
                    val list = ArrayList<Order>(arr.length())
                    for (i in 0 until arr.length()) list.add(Order.fromJson(arr.getJSONObject(i)))
                    OrderStore.snapshot(list)
                }
                "new" -> {
                    val o = Order.fromJson(json.getJSONObject("order"))
                    if (OrderStore.add(o)) onNewOrder(o)
                }
            }
        } catch (e: Exception) {
            Log.e("OrderService", "parse loi", e)
        }
    }

    private fun onNewOrder(o: Order) {
        Notifications.showNewOrder(this, o)
        AlarmPlayer.start(this)
        vibrate()
        // Tu tat chuong sau 60s neu khong ai bam "Da nhan"
        main.removeCallbacks(stopAlarmRunnable)
        main.postDelayed(stopAlarmRunnable, 60_000)
    }

    private fun vibrate() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        AlarmPlayer.stop()
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        // Neu he thong giet, START_STICKY se tu tao lai service.
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.fubao.orderdisplay.STOP_ALARM"

        fun start(ctx: Context) {
            val i = Intent(ctx, OrderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stopAlarm(ctx: Context) {
            val i = Intent(ctx, OrderService::class.java).setAction(ACTION_STOP_ALARM)
            ctx.startService(i)
        }
    }
}
