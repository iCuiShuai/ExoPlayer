package com.mxplay.adloader.nativeCompanion.expandable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.CompanionAdSlot
import org.json.JSONObject

abstract class PlayerBottomCompanion(
    private val payload: TemplateData,
    private val companionAdSlot: CompanionAdSlot,
    private val eventsTracker: EventsTracker,
    private val resourceProvider: CompanionResourceProvider
) : NativeCompanion(), VisibilityTracker.VisibilityTrackerListener {


    private val context: Context = companionAdSlot.container.context
    private val container = companionAdSlot.container
    private var visibilityTracker : VisibilityTracker? = null
    private var isImpressed : Boolean = false
    private var companionView : ViewGroup? = null
    private val handler = Handler(Looper.getMainLooper())
    private var  expandHandler : ImageButton? = null

    companion object {
        private const val TAG = "ExpandableNativeCompanion"
        const val DURATION_SHORT = 200L
        const val DURATION_LONG = 500L


        fun create(ad : Ad, json: JSONObject, companionAdSlot: CompanionAdSlot, eventsTracker: EventsTracker, resourceProvider: CompanionResourceProvider) : PlayerBottomCompanion{
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
        companionView = LayoutInflater.from(context).inflate(R.layout.layout_player_bottom_native_companion, null, false) as ConstraintLayout
        resourceProvider.loadImage(payload.logoUrl(), companionView!!.findViewById(R.id.logo))
        companionView!!.findViewById<TextView>(R.id.title).text = payload.title
        companionView!!.findViewById<TextView>(R.id.subtitle).text = payload.description
        val action = companionView!!.findViewById<TextView>(R.id.cta_button)
        bindCTA(action)
        expandHandler = companionView!!.findViewById(R.id.expand)
        val templateView: View? = renderOverlay()
        if (templateView != null){
            expandHandler!!.visibility = View.VISIBLE
            expandHandler!!.setOnClickListener {
                templateView.visibility = View.VISIBLE
            }
        }else{
            expandHandler!!.visibility = View.GONE
        }
    }



    override fun loadCompanion() {
        container.addView(companionView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (visibilityTracker == null) visibilityTracker = VisibilityTracker(companionView!!, 60)
        visibilityTracker!!.setVisibilityTrackerListener(this)
        return
    }


    protected fun bindCTA(action: TextView) {
        if (payload.clickThroughUrl != null) {
            action.text = payload.CTA ?: context.getString(R.string.cta_learn_more)
            action.setOnClickListener {
                kotlin.runCatching {
                    context.startActivity(Intent().apply {
                        setAction(Intent.ACTION_VIEW)
                        data = Uri.parse(payload.clickThroughUrl)
                    })
                    trackClick(payload.clickTracker)
                }
            }
        } else {
            action.visibility = View.GONE
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean) {
        if (isVisible && !isImpressed){
            isImpressed = true
            trackImpression()
            visibilityTracker?.release()
        }
    }


    private fun trackImpression(){
        payload.impressionTracker?.let {
            eventsTracker.trackAdImpression(it, payload.getTrackingData())
        }
    }

    private  fun trackClick(trackers: List<String>?) {
        trackers?.let {
            eventsTracker.trackClick(it, payload.getTrackingData())
        }
    }

    override fun release() {
        super.release()
        handler.removeCallbacksAndMessages(null)
    }

    fun getExpandHandlerView() = expandHandler

}


