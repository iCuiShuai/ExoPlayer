package com.mxplay.interactivemedia.internal.data.model

import java.util.*

 class VMAPModel {
     companion object{
         const val VMAP = "vmap:VMAP"
         const val AD_BREAK = "vmap:AdBreak"
         const val VAST_AD_SOURCE = "vmap:AdSource"
         const val VAST_AD_TAG_URI = "vmap:VASTAdTagURI"
         const val VAST_DATA_XML_TAG_V1 = "VASTData"
         const val VAST_DATA_XML_TAG_V2 = "vmap:VASTAdData"
     }

     var version: String? = null
     var adBreaks = ArrayList<AdBreak>()

    fun addABreak(adBreak: AdBreak) {
        adBreaks.add(adBreak)
    }

    fun getAdBreaks(): List<AdBreak> {
        return adBreaks
    }
}