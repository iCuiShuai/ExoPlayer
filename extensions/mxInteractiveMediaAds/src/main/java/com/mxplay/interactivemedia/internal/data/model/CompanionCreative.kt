package com.mxplay.interactivemedia.internal.data.model

class CompanionCreative(id : String) : Creative(id) {

    var companionAds : MutableList<CompanionAdData> ? = null


    companion object{
        const val TAG_COMPANION_AD = "Companion"
        const val ATTR_CREATIVE_TYPE = "creativeType"

        const val TAG_COMPANION_CLICK_THROUGH = "CompanionClickThrough"
        const val ID = "id"
        const val ATTR_WIDTH = "width"
        const val ATTR_HEIGHT = "height"


    }
}