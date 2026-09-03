package com.fubao.orderdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tu khoi dong lai service sau khi may reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            OrderService.start(context)
        }
    }
}
