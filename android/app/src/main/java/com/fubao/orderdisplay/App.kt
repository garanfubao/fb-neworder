package com.fubao.orderdisplay

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DoneIds.init(this)
        Notifications.createChannels(this)
    }
}
