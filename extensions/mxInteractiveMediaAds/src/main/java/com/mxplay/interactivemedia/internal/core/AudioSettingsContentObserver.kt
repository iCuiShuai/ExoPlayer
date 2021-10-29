package com.mxplay.interactivemedia.internal.core

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.provider.Settings
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import com.mxplay.interactivemedia.internal.api.AudioListener
import java.lang.Exception

class AudioSettingsContentObserver(var context: Context, var audioListener: AudioListener, handler: Handler?) : ContentObserver(handler), AudioListener {
    override fun deliverSelfNotifications(): Boolean {
        return super.deliverSelfNotifications()
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        try {
            val audioService = context.getSystemService(Context.AUDIO_SERVICE)
            val currentVolume = (audioService as? AudioManager)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            val maxVolume = (audioService as? AudioManager)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
            val minVolume = (audioService as? AudioManager)?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
            val currentNormalisedVolume = (currentVolume * 1.0) / (maxVolume - minVolume)
            onVolumeChanged(currentNormalisedVolume.toFloat())
        } catch (e: Exception) {
        }
    }

    override fun registerAudioListener() {
        context.applicationContext.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, this)
    }

    override fun unregisterAudioListener() {
        context.applicationContext.contentResolver.unregisterContentObserver(this)
    }

    override fun onVolumeChanged(volume: Float) {
        audioListener.onVolumeChanged(volume)
    }
}