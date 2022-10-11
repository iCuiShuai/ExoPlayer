package com.mxplay.adloader.nativeCompanion.expandable

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.VisibilityTracker
import com.mxplay.adloader.nativeCompanion.expandable.data.BigBannerTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.CompanionTrackingInfo
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.logger.ZenLogger

class BigBannerCompanion(
    private val payload: BigBannerTemplateData,
    private val companionAdSlot: CompanionAdSlot,
    private val eventsTracker: EventsTracker,
    private val resourceProvider: CompanionResourceProvider
) : PlayerBottomCompanion(payload, companionAdSlot, eventsTracker, resourceProvider) {


    private val context: Context = companionAdSlot.getContainer()?.context!!
    private val expandOverlayContainer : ViewGroup? = (companionAdSlot.getContainer()?.parent as ViewGroup).findViewById(R.id.expandable_overlay)
    private var  templateBannerView : ViewGroup? = null
    private var  hostViewVisibilityTracker : VisibilityTracker? = null

    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
    }


    override fun release() {
        super.release()
        ZenLogger.dt(TAG, " BigBannerCompanion release")
        expandOverlayContainer?.removeAllViews()
        hostViewVisibilityTracker?.release()
        hostViewVisibilityTracker = null
    }



    override fun renderOverlay(): ViewGroup? {
        if (expandOverlayContainer == null || payload.ads.isEmpty()) return null
        ZenLogger.dt(TAG, " BigBannerCompanion renderOverlay")
        initExpandableTopView()
        val ad = payload.ads.first()
        val banner = templateBannerView!!.findViewById<ImageView>(R.id.image)
        banner.setOnClickListener {
            kotlin.runCatching {
                context.startActivity(Intent().apply {
                    setAction(Intent.ACTION_VIEW)
                    data = Uri.parse(ad.clickThroughUrl)
                })
                eventsTracker.trackClick(this, ad.clickTracker, CompanionTrackingInfo.CompanionItemTrackingInfo("0", ad.id, payload.getTrackingData()))
            }
        }
        if (!ad.isImpressed){
            ad.isImpressed = true
            ad.impressionTrackers?.let {
                eventsTracker.trackAdItemImpressionStream(this, EventsTracker.ImpressionData(it, CompanionTrackingInfo.CompanionItemTrackingInfo("0", ad.id, payload.getTrackingData())))
            }
        }

        resourceProvider.loadImage(ad.bannerUrl(payload.imageCdnUrl), banner, 0, 0)
        bindCTA(templateBannerView!!.findViewById(R.id.native_ad_action_button))
        return templateBannerView
    }

    private fun initExpandableTopView() {
        templateBannerView = LayoutInflater.from(context).inflate(R.layout.layout_native_expandable_template_big_banner, null, false) as ViewGroup
        resourceProvider.loadImage(payload.logoUrl(), templateBannerView!!.findViewById<ImageView>(R.id.logo))
        templateBannerView!!.findViewById<TextView>(R.id.title).text = payload.title
        templateBannerView!!.findViewById<TextView>(R.id.subtitle).text = payload.description
        val action = templateBannerView!!.findViewById<ImageButton>(R.id.dismiss)
        action.setOnClickListener {
            eventsTracker.trackAdHide(this, false,  payload.getTrackingData())
            startCollapseAnimation(templateBannerView!!, object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                }
            })
        }
    }

    override fun display() {

        ZenLogger.dt(TAG, " BigBannerCompanion loadCompanion")
        expandOverlayContainer?.removeAllViews()
        expandOverlayContainer?.visibility = View.VISIBLE
        expandOverlayContainer?.addView(templateBannerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        getExpandHandlerView()?.let {
            it.setOnClickListener { view ->
                view.setTag(R.id.tag_visibility, true)
                if (hostViewVisibilityTracker?.isVisible() == true){
                    val autoExpanded = view.getTag(R.id.is_auto_expanded) == true
                    view.setTag(R.id.is_auto_expanded , false)
                    eventsTracker.trackAdShown(this, autoExpanded, payload.getTrackingData())
                    startExpandAnimation(templateBannerView!!, object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                        }

                        override fun onAnimationStart(animation: Animator?) {
                            super.onAnimationStart(animation)
                        }
                    }, (templateBannerView?.parent as View).measuredHeight)
                }

            }
            if (templateBannerView?.parent != null){
                hostViewVisibilityTracker = VisibilityTracker(templateBannerView?.parent as View, 80, 100)
                hostViewVisibilityTracker!!.setVisibilityTrackerListener(hostViewVisibilityTrackerListener)
            }
            if(hostViewVisibilityTracker?.isVisible() == true){
                if (it.getTag(R.id.tag_visibility)  == null){
                    it.setTag(R.id.is_auto_expanded, true)
                    it.performClick()
                }

            }

        }
        super.display()
    }

    private val hostViewVisibilityTrackerListener = object : VisibilityTracker.VisibilityTrackerListener{
        override fun onVisibilityChanged(isVisible: Boolean) {
            if(hostViewVisibilityTracker?.isVisible() == true){
                val handler = getExpandHandlerView()!!
                if (handler.getTag(R.id.tag_visibility)  == null){
                    handler.performClick()
                }

            }
        }

    }

}


