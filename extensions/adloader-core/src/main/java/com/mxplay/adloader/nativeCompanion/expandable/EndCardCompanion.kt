package com.mxplay.adloader.nativeCompanion.expandable

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import ccom.mxplay.adloader.R
import com.mxplay.adloader.AdsBehaviour
import com.mxplay.adloader.nativeCompanion.*
import com.mxplay.adloader.nativeCompanion.expandable.data.EndCardTemplateData
import com.mxplay.adloader.utils.DeviceUtils
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.logger.ZenLogger
import org.json.JSONObject
import java.util.*

class EndCardCompanion(
    private val payload: EndCardTemplateData,
    private val companionAdSlot: CompanionAdSlot,
    private val eventsTracker: EventsTracker,
    private val resourceProvider: CompanionResourceProvider,
    private val adsBehaviour: AdsBehaviour?
) : NativeCompanion(), VisibilityTracker.VisibilityTrackerListener  {


    companion object{
        const val TAG = "EndCardCompanion"
        private const val PROGRESS_THRESHOLD = 320

        fun create(json: JSONObject, companionAdSlot: CompanionAdSlot, eventsTracker: EventsTracker, resourceProvider: CompanionResourceProvider, adsBehaviour: AdsBehaviour?) : EndCardCompanion{
            val data = parse(json)
            if (data is EndCardTemplateData){
                return EndCardCompanion(data, companionAdSlot, eventsTracker, resourceProvider, adsBehaviour)
            }else throw IllegalStateException("Unrecognised ad data")

        }
    }
    private val context: Context = companionAdSlot.getContainer()?.context!!
    private val container = companionAdSlot.getContainer()!!
    private var  hostViewVisibilityTracker : VisibilityTracker? = null
    private var companionView : ViewGroup? = null
    private var isImpressed : Boolean = false
    private var visibilityTracker : VisibilityTracker? = null
    private var countDownTimer: CountDownTimer? = null
    private var txtTimeLeft: AppCompatTextView? = null
    private var isThirdQuartileReached = false



    override fun preload() {
        companionView = LayoutInflater.from(context).inflate(R.layout.layout_native_endcard, null, false) as ViewGroup
        resourceProvider.loadImage(payload.logoUrl(), companionView!!.findViewById(R.id.logo))
        companionView!!.findViewById<TextView>(R.id.title).text = payload.title
        companionView!!.findViewById<TextView>(R.id.subtitle).text = payload.description
        txtTimeLeft = companionView!!.findViewById(R.id.time_left)
        companionView!!.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismissEndCard()
        }
        val action = companionView!!.findViewById<TextView>(R.id.native_ad_action_button)
        bindCTA(companionView!!, action)
        companionState = CompanionState.PRELOADED

        val ad = payload.ads.first()
        val banner = companionView!!.findViewById<ImageView>(R.id.image)
        resourceProvider.loadImage(ad.bannerUrl(payload.imageCdnUrl), banner)

    }

    override fun display() {
    }


    private fun showVideoAdEndcard() {
        if (companionState == CompanionState.DISPLAYED) return
        companionState = CompanionState.DISPLAYED
        container.removeAllViews()
        container.addView(companionView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (visibilityTracker == null) visibilityTracker = VisibilityTracker(companionView!!, 60)
        visibilityTracker!!.setVisibilityTrackerListener(this)
        val duration = (payload.endCardDuration ?: 5) * 1000L
        // #debug debug
        ZenLogger.dt(TAG, " Showing video end card $duration")
        container.visibility = View.VISIBLE
        if (countDownTimer != null) countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                txtTimeLeft!!.visibility = View.VISIBLE
                txtTimeLeft!!.text = String.format(Locale.getDefault(), "Ad closes in %d ", (millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                dismissEndCard()
            }
        }
        countDownTimer?.start()
    }

    private fun dismissEndCard() {
        // #debug debug
        ZenLogger.dt(TAG, " dismiss video end card ")
        if (countDownTimer != null) countDownTimer?.cancel()
        container.removeAllViews()
    }

    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
        val type: AdEvent.AdEventType = adEvent.type
        val player = adsBehaviour?.getPlayer()
        isThirdQuartileReached =  isThirdQuartileReached || type == AdEvent.AdEventType.THIRD_QUARTILE
        if (type == AdEvent.AdEventType.AD_PROGRESS && player != null && companionState == CompanionState.PRELOADED) {
            val contentDuration: Long = player.duration
            val contentPos: Long = player.currentPosition
            val isCompanionDisabled = container.getTag(R.id.is_companion_disabled)
            if ((isCompanionDisabled == null || isCompanionDisabled == false) && isThirdQuartileReached && contentDuration > 0 && contentPos > 0 && contentDuration - contentPos <= PROGRESS_THRESHOLD && context is Activity) {
                val orientation = DeviceUtils.getScreenOrientation(context, context.windowManager.defaultDisplay)
                if (orientation  == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE){
                    showVideoAdEndcard()
                }

            }
        }
    }


    override fun release() {
        super.release()
        ZenLogger.dt(TAG, " release")
        countDownTimer?.cancel()
        hostViewVisibilityTracker?.release()
        hostViewVisibilityTracker = null
    }


    private fun clickAction(){
        kotlin.runCatching {
            context.startActivity(Intent().apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(payload.clickThroughUrl)
            })
            trackClick(payload.clickTracker)
        }
    }

    private fun bindCTA(adView: View , action: TextView) {

        if (payload.clickThroughUrl != null) {
            action.text = payload.CTA ?: context.getString(R.string.cta_learn_more)
            adView.setOnClickListener {
                clickAction()
            }
            action.setOnClickListener {
                clickAction()
            }
        } else {
            action.visibility = View.GONE
        }
    }

    private  fun trackClick(trackers: List<String>?) {
        trackers?.let {
            eventsTracker.trackClick(this, it, payload.getTrackingData())
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
            eventsTracker.trackAdImpression(this, it, payload.getTrackingData())
        }
    }

}


