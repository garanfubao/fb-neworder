package com.fubao.orderdisplay

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Kho don trong bo nho, luon sap xep don MOI NHAT len dau.
 * Service ghi vao day, MainActivity lang nghe de ve lai bang.
 */
object OrderStore {
    private val items = CopyOnWriteArrayList<Order>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var status: String = "Chua ket noi"
        private set

    /** Danh sach tu server (cu -> moi). Thay toan bo, khong keu chuong. */
    fun snapshot(serverList: List<Order>) {
        items.clear()
        // dao lai de moi nhat len dau
        for (o in serverList.reversed()) if (items.none { it.id == o.id }) items.add(o)
        notifyChange()
    }

    /** Them 1 don moi. Tra ve true neu thuc su la don moi (chua co). */
    fun add(o: Order): Boolean {
        if (items.any { it.id == o.id }) return false
        items.add(0, o)
        notifyChange()
        return true
    }

    fun all(): List<Order> = items.toList()

    fun setStatus(s: String) {
        status = s
        notifyChange()
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyChange() {
        main.post { listeners.forEach { it() } }
    }
}
