package com.fubao.orderdisplay

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/** Phat chuong lap lai qua luong ALARM -> keu ca khi dang o app khac. */
object AlarmPlayer {
    private var mp: MediaPlayer? = null

    @Synchronized
    fun start(ctx: Context) {
        if (mp != null) return
        try {
            val afd: AssetFileDescriptor = ctx.resources.openRawResourceFd(R.raw.order_alarm)
            mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "start loi", e)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        try {
            mp?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            mp = null
        }
    }

    fun isPlaying(): Boolean = mp != null
}
