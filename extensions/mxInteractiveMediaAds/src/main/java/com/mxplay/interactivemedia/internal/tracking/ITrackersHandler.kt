package com.mxplay.interactivemedia.internal.tracking

import com.mxplay.interactivemedia.api.AdErrorEvent
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.internal.api.AdBreakErrorListener
import com.mxplay.interactivemedia.internal.api.AdBreakEventListener
import com.mxplay.interactivemedia.internal.api.CompanionAdEventListener

interface ITrackersHandler : AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdBreakEventListener, AdBreakErrorListener, CompanionAdEventListener{
    /*fun onAdProgressUpdate(ad : Ad, progress : VideoProgressUpdate)
    fun onAdSkipped(ad: Ad)
    fun onAdClicked(ad: Ad)
    fun onImpression(ad: Ad)
    fun onError(ad: Ad)
    fun onAdLoaded(ad: Ad)
    fun onAdShown(ad: Ad)
    fun onEnterFullScreen()
    fun onExitFullScreen()
    fun onPause(ad: Ad)
    fun onResume(ad: Ad)
    fun onCreateView(ad : Ad)
    fun onCreateView(ad : CompanionAdData)
    fun onAdBreakError(ad: AdBreak)*/
}