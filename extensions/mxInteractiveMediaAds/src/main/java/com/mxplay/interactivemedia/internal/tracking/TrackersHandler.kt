package com.mxplay.interactivemedia.internal.tracking

import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.AdErrorEvent
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.FriendlyObstruction
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.AdBreakErrorEvent
import com.mxplay.interactivemedia.internal.data.model.AdBreakEvent
import com.mxplay.interactivemedia.internal.data.model.CompanionAdEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class TrackersHandler(val mxOmid: MxOmid?, private val remoteDataSource: RemoteDataSource, private val coroutineScope: CoroutineScope, private val obstructionList: List<FriendlyObstruction>) : ITrackersHandler{

    private val eventPostFlow = MutableStateFlow<Any>(Any())
    private val trackers  = mutableMapOf<Any, EventTracker>()
    private val mediaState =  mutableMapOf<Ad, Int>()

    init {
        subscribeEventPostFlow()
    }

    private fun subscribeEventPostFlow(){
        coroutineScope.launch {
            eventPostFlow.collect { task ->
                when(task){
                    is AdEvent -> {
                        task.ad?.let {
                            val eventTracker = (trackers.getOrPut(it) { AdEventTracker(it, mxOmid, remoteDataSource, obstructionList) }) as AdEventTracker
                            eventTracker.onAdEvent(task)
                        }
                        onEvent()
                    }
                    is AdErrorEvent -> {
                        task.ad?.let {
                            val eventTracker = (trackers.getOrPut(it) { AdEventTracker(it, mxOmid, remoteDataSource, obstructionList) }) as AdEventTracker
                            eventTracker.onAdError(task)
                        }
                        onError()
                    }

                    is AdBreakEvent -> {
                        task.adBreak.let {
                            val eventTracker = (trackers.getOrPut(it) { AdBreakEventTracker(it, remoteDataSource) }) as AdBreakEventTracker
                            eventTracker.onEvent(task)
                        }

                    }
                    is AdBreakErrorEvent -> {
                        task.adBreak.let {
                            val eventTracker = (trackers.getOrPut(it) { AdBreakEventTracker(it, remoteDataSource) }) as AdBreakEventTracker
                            eventTracker.onError(task)
                        }

                    }

                    is CompanionAdEvent -> {
                        task.companionAd.let {
                            val eventTracker = (trackers.getOrPut(it) { CompanionAdEventTracker(it, remoteDataSource) }) as CompanionAdEventTracker
                            eventTracker.onEvent(task)
                        }

                    }

                }

            }
        }
    }

    fun onEvent(){

    }

    fun onError(){

    }

    override fun onAdEvent(adEvent: AdEvent) {
        eventPostFlow.tryEmit(adEvent)
    }

    override fun onAdError(adErrorEvent: AdErrorEvent) {
        eventPostFlow.tryEmit(adErrorEvent)
    }

    override fun onEvent(adBreakEvent: AdBreakEvent) {
        eventPostFlow.tryEmit(adBreakEvent)
    }

    override fun onEvent(companionAdEvent: CompanionAdEvent) {
        eventPostFlow.tryEmit(companionAdEvent)
    }

    override fun onError(adBreakErrorEvent: AdBreakErrorEvent) {
        eventPostFlow.tryEmit(adBreakErrorEvent)
    }

    fun release(){
        mediaState.clear()
        trackers.forEach{
            it.value.release()
        }
        trackers.clear()
    }

}