package com.mxplay.adloader.nativeCompanion.expandable.data

open class CompanionTrackingInfo(val adId: String, val campaignId: String?,
                                 val campaignName: String?, val creativeId: String, val templateId : String?, val type : String?){

    open fun toMap(): MutableMap<String, String> {
        return HashMap<String, String>().apply {
            put("adId", adId)
            put("campaignId", campaignId ?: "")
            put("campaignName", campaignName ?: "")
            put("creativeId", creativeId)
            put("templateId", templateId ?: "")
            put("type", type ?: "")
        }
    }

    data class CompanionItemTrackingInfo(val position: String, val itemId: String?, val trackingInfo: CompanionTrackingInfo) : CompanionTrackingInfo(trackingInfo.adId, trackingInfo.campaignId, trackingInfo.campaignName, trackingInfo.creativeId, trackingInfo.templateId, trackingInfo.type){
        override fun toMap(): MutableMap<String, String> {
            return super.toMap().toMutableMap().apply {
                put("itemId", itemId ?: "")
                put("itemPosition", position)
            }
        }

    }
}