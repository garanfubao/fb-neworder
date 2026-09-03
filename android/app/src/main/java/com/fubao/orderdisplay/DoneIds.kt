package com.fubao.orderdisplay

import android.content.Context

/**
 * Nho cac id don da bam "Xong" -> khong hien lai nua,
 * ngay ca khi server gui lai snapshot (mat mang / mo lai app).
 */
object DoneIds {
    private const val FILE = "fubao_done"
    private const val KEY = "ids"
    private const val MAX = 500
    private val set = LinkedHashSet<String>()

    fun init(ctx: Context) {
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        synchronized(set) { set.addAll(saved) }
    }

    @Synchronized
    fun contains(id: String): Boolean = set.contains(id)

    @Synchronized
    fun add(ctx: Context, id: String) {
        set.add(id)
        while (set.size > MAX) {
            val it = set.iterator()
            it.next(); it.remove()
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, HashSet(set)).apply()
    }
}
