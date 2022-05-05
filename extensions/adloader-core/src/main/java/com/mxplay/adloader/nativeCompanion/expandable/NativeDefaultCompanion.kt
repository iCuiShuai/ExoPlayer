package com.mxplay.adloader.nativeCompanion.expandable

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.VisibilityTracker
import com.mxplay.adloader.nativeCompanion.expandable.data.BigBannerTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.CompanionTrackingInfo
import com.mxplay.adloader.nativeCompanion.expandable.data.TemplateData
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.logger.ZenLogger

class NativeDefaultCompanion(
    payload: TemplateData,
    companionAdSlot: CompanionAdSlot,
    eventsTracker: EventsTracker,
    resourceProvider: CompanionResourceProvider
) : PlayerBottomCompanion(payload, companionAdSlot, eventsTracker, resourceProvider) {


    override fun renderOverlay(): ViewGroup?  = null


}


