package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.api.CompanionAd
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.model.ICompanionResource

class AdCompanionInfo(val companionAdSlot: CompanionAdSlot, val companionAd: CompanionAd) {
    private var resource: ICompanionResource? = null

    fun getResource(): ICompanionResource? {
        return resource
    }

    fun setResource(resource: ICompanionResource) {
        this.resource = resource
    }
}