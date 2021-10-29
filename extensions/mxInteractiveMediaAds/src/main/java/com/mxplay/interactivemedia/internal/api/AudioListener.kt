package com.mxplay.interactivemedia.internal.api

interface AudioListener {
    fun registerAudioListener()
    fun unregisterAudioListener()
    fun onVolumeChanged(volume: Float)
}