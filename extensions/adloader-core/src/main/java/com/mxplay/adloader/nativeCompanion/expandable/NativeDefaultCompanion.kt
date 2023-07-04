package com.mxplay.adloader.nativeCompanion.expandable

import android.view.ViewGroup
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.expandable.data.TemplateData
import com.mxplay.interactivemedia.api.CompanionAdSlot

class NativeDefaultCompanion(
    payload: TemplateData,
    companionAdSlot: CompanionAdSlot,
    eventsTracker: EventsTracker,
    resourceProvider: CompanionResourceProvider
) : PlayerBottomCompanion(payload, companionAdSlot, eventsTracker, resourceProvider) {


    override fun renderOverlay(): ViewGroup?  = null

    override fun release() {
        super.release()
    }

    override fun nativeLayoutId(): Int {
        return R.layout.layout_player_bottom_native_custom_cta_companion
    }


}


