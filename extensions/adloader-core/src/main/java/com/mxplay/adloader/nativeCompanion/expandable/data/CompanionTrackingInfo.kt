package com.mxplay.adloader.nativeCompanion.expandable.data

open class CompanionTrackingInfo(val adId: String, val campaignId: String?,
                                 val campaignName: String?, val creativeId: String, val templateId : String){

    open fun toMap(): Map<String, String> {
        return HashMap<String, String>().apply {
            put("adId", adId)
            put("campaignId", campaignId ?: "")
            put("campaignName", campaignName ?: "")
            put("creativeId", creativeId)
            put("templateId", templateId)
        }
    }

    class CompanionItemTrackingInfo(val id : String?, trackingInfo: CompanionTrackingInfo) : CompanionTrackingInfo(trackingInfo.adId, trackingInfo.campaignId, trackingInfo.campaignName, trackingInfo.creativeId, trackingInfo.templateId){
        override fun toMap(): Map<String, String> {
            return super.toMap().toMutableMap().apply {
                put("itemId", id ?: "")
            }
        }

    }
}