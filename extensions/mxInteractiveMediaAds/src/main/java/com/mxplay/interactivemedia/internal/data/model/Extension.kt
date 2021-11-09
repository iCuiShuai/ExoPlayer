package com.mxplay.interactivemedia.internal.data.model

data class Extension(val type : String, val attrs : Map<String, String>){
    companion object{
        const val TAG_EXTENSION = "Extension"
        const val ATTR_TYPE = "type"
        const val KEY_CTATEXT = "CtaText"
        const val KEY_CTATEXTCOLOR = "CtaTextColor"
        const val KEY_CTABOXCOLOR = "CtaBoxColor"
    }
}

