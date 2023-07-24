package com.mxplay.adloader.nativeCompanion.expandable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.*
import com.mxplay.adloader.nativeCompanion.expandable.data.BigBannerTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.TableViewTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.TemplateData
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.logger.ZenLogger
import org.json.JSONObject

abstract class PlayerBottomCompanion(
    private val payload: TemplateData,
    private val companionAdSlot: CompanionAdSlot,
    private val eventsTracker: EventsTracker,
    private val resourceProvider: CompanionResourceProvider
) : NativeCompanion(), VisibilityTracker.VisibilityTrackerListener {


    private val context: Context = companionAdSlot.getContainer()?.context!!
    private val container = companionAdSlot.getContainer()!!
    private var visibilityTracker : VisibilityTracker? = null
    private var isImpressed : Boolean = false
    private var companionView : ViewGroup? = null
    private var templateView: View? = null
    private var  expandHandler : ImageButton? = null

    companion object {
        private const val TAG = "ExpandableNativeCompanion"
        const val DURATION_SHORT = 200L
        const val DURATION_LONG = 500L


        fun create(json: JSONObject, companionAdSlot: CompanionAdSlot, eventsTracker: EventsTracker, resourceProvider: CompanionResourceProvider) : PlayerBottomCompanion{
            val data = parse(json)
            if (data is BigBannerTemplateData){
                return BigBannerCompanion(data, companionAdSlot, eventsTracker, resourceProvider)
            }else if (data is TableViewTemplateData){
                return TableViewCompanion(data, companionAdSlot, eventsTracker, resourceProvider)
            } else {
                return NativeDefaultCompanion(data, companionAdSlot, eventsTracker, resourceProvider)
            }
        }
    }


    abstract fun renderOverlay() : View?

    override fun preload() {
        companionView = LayoutInflater.from(context).inflate(nativeLayoutId(), null, false) as ConstraintLayout
        resourceProvider.loadImage(payload.logoUrl(), companionView!!.findViewById(R.id.logo))
        companionView!!.findViewById<TextView>(R.id.title).text = payload.title
        companionView!!.findViewById<TextView>(R.id.subtitle).text = payload.description
        companionView!!.findViewById<TextView>(R.id.advertiser)?.text = payload.advertiser
        val action = companionView!!.findViewById<TextView>(R.id.cta_button)
        bindCTA(action)
        expandHandler = companionView!!.findViewById(R.id.expand)
        templateView = renderOverlay()
        if (templateView != null){
            expandHandler?.let {
                it.visibility = View.VISIBLE
                it.setOnClickListener {
                    templateView?.visibility = View.VISIBLE
                }
            }
        }else{
            if(expandHandler != null){
                expandHandler!!.visibility = View.GONE
                action.layoutParams = (action.layoutParams as ConstraintLayout.LayoutParams).apply {
                    this.rightMargin = context.resources.getDimensionPixelSize(R.dimen.ad_action_cta_margin)
                }
            }
        }
        companionState =CompanionState.PRELOADED
        companionView?.setOnClickListener { onAdClick() }
        templateView?.setOnClickListener { onAdClick() }
    }

    open fun nativeLayoutId():  Int {
        return R.layout.layout_player_bottom_native_companion
    }

    override fun isAdExpanded() = templateView?.visibility == View.VISIBLE

    override fun display() {
        container.removeAllViews()
        val isCompanionDisabled = container.getTag(R.id.is_companion_disabled)
        if(isCompanionDisabled == true) return

        container.addView(companionView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (visibilityTracker == null) visibilityTracker = VisibilityTracker(companionView!!, 60)
        visibilityTracker!!.setVisibilityTrackerListener(this)
        companionState =CompanionState.DISPLAYED
        return
    }


    protected fun bindCTA(action: TextView) {
        if (payload.clickThroughUrl != null) {
            action.text = payload.CTA ?: context.getString(R.string.cta_learn_more)
            action.setOnClickListener {
                onAdClick()
            }
        } else {
            action.visibility = View.GONE
        }
    }

    private fun onAdClick() {
        kotlin.runCatching {
            context.startActivity(Intent().apply {
                setAction(Intent.ACTION_VIEW)
                data = Uri.parse(payload.clickThroughUrl)
            })
            trackClick(payload.clickTracker)
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean) {
        if (isVisible && !isImpressed){
            isImpressed = true
            ZenLogger.dt(EventsTracker.TAG, "onVisibilityChanged ad top view")
            trackImpression()
            visibilityTracker?.release()
        }
    }


    private fun trackImpression(){
        payload.impressionTracker?.let {
            eventsTracker.trackAdImpression(this, it, payload.getTrackingData())
        }
    }

    private  fun trackClick(trackers: List<String>?) {
        trackers?.let {
            eventsTracker.trackClick(this, it, payload.getTrackingData())
        }
    }

    override fun release() {
        super.release()
    }

    fun getExpandHandlerView() = expandHandler

}


