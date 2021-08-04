package com.mxplay.interactivemedia.api

interface AdsManager : BaseManager {
    companion object{
        const val PRE_ROLL_POD_INDEX = 0
        const val POST_ROLL_POD_INDEX = -1
    }

    fun start()
    val adCuePoints: List<Float?>?
    fun pause()
    fun resume()
    fun skip()
    fun discardAdBreak()

    @Deprecated("")
    fun requestNextAdBreak()
    fun focus()
}