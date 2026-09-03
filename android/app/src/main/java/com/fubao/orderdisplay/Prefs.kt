package com.fubao.orderdisplay

import android.content.Context

/** Cho phep chinh URL server + key ngay trong app (khong can build lai). */
object Prefs {
    private const val FILE = "fubao_prefs"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun wsUrl(ctx: Context): String =
        sp(ctx).getString("ws", Config.WS_URL) ?: Config.WS_URL

    fun apiKey(ctx: Context): String =
        sp(ctx).getString("key", Config.API_KEY) ?: Config.API_KEY

    fun save(ctx: Context, ws: String, key: String) {
        sp(ctx).edit().putString("ws", ws.trim()).putString("key", key.trim()).apply()
    }
}
