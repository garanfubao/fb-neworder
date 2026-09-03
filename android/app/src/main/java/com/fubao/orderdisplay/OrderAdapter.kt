package com.fubao.orderdisplay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OrderAdapter : RecyclerView.Adapter<OrderAdapter.VH>() {

    private var data: List<Order> = emptyList()

    fun submit(list: List<Order>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.txtTime)
        val items: TextView = v.findViewById(R.id.txtItems)
        val address: TextView = v.findViewById(R.id.txtAddress)
        val phone: TextView = v.findViewById(R.id.txtPhone)
        val note: TextView = v.findViewById(R.id.txtNote)
        val pay: TextView = v.findViewById(R.id.txtPay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = data[position]
        holder.items.text = o.items ?: "(khong ro mon)"
        setOrHide(holder.address, "📍 ", o.address)
        setOrHide(holder.phone, "📞 ", o.phone)
        setOrHide(holder.note, "📝 ", o.note)
        val payText = listOfNotNull(o.totalPrice, o.paymentMethod).joinToString(" · ")
        setOrHide(holder.pay, "💵 ", payText.ifEmpty { null })
        holder.time.text = formatTime(o.receivedAt)
    }

    private fun setOrHide(tv: TextView, prefix: String, value: String?) {
        if (value.isNullOrBlank()) {
            tv.visibility = View.GONE
        } else {
            tv.visibility = View.VISIBLE
            tv.text = prefix + value
        }
    }

    private val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val outFmt = SimpleDateFormat("HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }

    private fun formatTime(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val d: Date = inFmt.parse(iso.substring(0, 19)) ?: return ""
            outFmt.format(d)
        } catch (_: Exception) {
            ""
        }
    }
}
