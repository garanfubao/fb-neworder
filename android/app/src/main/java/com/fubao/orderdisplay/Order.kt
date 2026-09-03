package com.fubao.orderdisplay

import org.json.JSONObject

data class Order(
    val id: String,
    val receivedAt: String,
    val customerName: String?,
    val phone: String?,
    val address: String?,
    val items: String?,
    val note: String?,
    val totalPrice: String?,
    val paymentMethod: String?
) {
    companion object {
        private fun nz(o: JSONObject, key: String): String? {
            if (!o.has(key) || o.isNull(key)) return null
            val v = o.optString(key, "").trim()
            return if (v.isEmpty() || v == "null") null else v
        }

        fun fromJson(o: JSONObject): Order = Order(
            id = o.optString("id", System.nanoTime().toString()),
            receivedAt = o.optString("receivedAt", ""),
            customerName = nz(o, "customerName"),
            phone = nz(o, "phone"),
            address = nz(o, "address"),
            items = nz(o, "items"),
            note = nz(o, "note"),
            totalPrice = nz(o, "totalPrice"),
            paymentMethod = nz(o, "paymentMethod")
        )
    }
}
