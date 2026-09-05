package com.fubao.orderdisplay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: OrderAdapter
    private lateinit var status: TextView
    private lateinit var empty: TextView
    private val listener: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        status = findViewById(R.id.txtStatus)
        empty = findViewById(R.id.txtEmpty)

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OrderAdapter { order -> confirmDone(order) }
        rv.adapter = adapter

        findViewById<Button>(R.id.btnStopAlarm).setOnClickListener {
            OrderService.stopAlarm(this)
        }
        findViewById<Button>(R.id.btnStopApp).setOnClickListener {
            confirmStopApp()
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettings()
        }

        ensurePermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        OrderStore.addListener(listener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        OrderStore.removeListener(listener)
    }

    private fun refresh() {
        val orders = OrderStore.all()
        adapter.submit(orders)
        status.text = OrderStore.status
        empty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
    }

    // Dung han service (ngat WebSocket + tha wakelock) roi dong app -> tiet kiem pin khi dong cua.
    private fun confirmStopApp() {
        AlertDialog.Builder(this)
            .setTitle("Dừng nhận đơn?")
            .setMessage("Sẽ ngắt kết nối và tắt app để đỡ tốn pin.\nMở lại app khi mở quán để nhận đơn tiếp.")
            .setPositiveButton("Dừng") { _, _ ->
                AlarmPlayer.stop()
                stopService(Intent(this, OrderService::class.java))
                finishAndRemoveTask()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun confirmDone(order: Order) {
        AlertDialog.Builder(this)
            .setTitle("Đơn đã xong?")
            .setMessage((order.items ?: "Đơn này") + "\n\nẨn khỏi danh sách?")
            .setPositiveButton("Xong ✓") { _, _ ->
                OrderService.markDone(this, order.id)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ----- Quyen + khoi dong service -----
    private fun ensurePermissionsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            }
        }
        askIgnoreBattery()
        OrderService.start(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        OrderService.start(this)
    }

    @SuppressLint("BatteryLife")
    private fun askIgnoreBattery() {
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } catch (_: Exception) {
        }
    }

    // ----- Cai dat URL + key ngay trong app -----
    private fun showSettings() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val wsInput = EditText(this).apply {
            hint = "wss://ten-app.onrender.com/ws"
            setText(Prefs.wsUrl(this@MainActivity))
        }
        val keyInput = EditText(this).apply {
            hint = "API key"
            setText(Prefs.apiKey(this@MainActivity))
        }
        box.addView(TextView(this).apply { text = "Dia chi WebSocket (wss://.../ws)" })
        box.addView(wsInput)
        box.addView(TextView(this).apply { text = "API key" })
        box.addView(keyInput)

        AlertDialog.Builder(this)
            .setTitle("Cai dat ket noi")
            .setView(box)
            .setPositiveButton("Luu & ket noi lai") { _, _ ->
                Prefs.save(this, wsInput.text.toString(), keyInput.text.toString())
                // Khoi dong lai service de ap dung cau hinh moi
                stopService(Intent(this, OrderService::class.java))
                OrderService.start(this)
            }
            .setNegativeButton("Huy", null)
            .show()
    }
}
