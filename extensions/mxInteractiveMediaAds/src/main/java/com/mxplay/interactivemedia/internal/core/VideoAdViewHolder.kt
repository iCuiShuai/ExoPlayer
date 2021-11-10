package com.mxplay.interactivemedia.internal.core

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import ccom.mxplay.offlineads.exo.R
import com.mxplay.interactivemedia.api.AdDisplayContainer
import com.mxplay.interactivemedia.api.AdPodInfo
import com.mxplay.interactivemedia.api.AdProgressListener
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.api.player.VideoProgressUpdate
import com.mxplay.interactivemedia.internal.data.model.AdInline
import com.mxplay.interactivemedia.internal.data.model.Extension
import com.mxplay.interactivemedia.internal.data.model.ExtensionTypes
import com.mxplay.interactivemedia.internal.util.DateTimeUtils.formatTime
import java.util.*

class VideoAdViewHolder(private val context : Context ,private val displayContainer: AdDisplayContainer) : IVideoAdViewHolder {
    private var adRootView : View? = null
    private var adProgressText: TextView? = null
    private var skipButton: Button? = null
    private var learnMoreButton: View? = null
    private var activeAd : ActiveAd? = null


    private fun createView(): View {
        val inflatedView = LayoutInflater.from(context).inflate(R.layout.layout_ad_controls, displayContainer.getAdContainer(), false)
        return inflatedView!!
    }

    override fun bind(activeAd : ActiveAd){
        this.activeAd = activeAd
        if (adRootView == null) {
            adRootView = createView()
            adProgressText = adRootView!!.findViewById<TextView>(R.id.adCounter).also { it.visibility = View.GONE }
            skipButton = adRootView!!.findViewById<Button>(R.id.skipButton).also { it.visibility = View.GONE; it.tag = activeAd }
            learnMoreButton = adRootView!!.findViewById<TextView>(R.id.learnMoreButton).also { it.visibility = View.GONE; it.tag = activeAd }
        }

        val advertiserName = activeAd.ad.getAdvertiserName()
        if (activeAd.ad is AdInline && activeAd.ad.extensions != null && activeAd.ad.extensions!!.containsKey(ExtensionTypes.MX_CTA.value)){
            val extension = activeAd.ad.extensions!![ExtensionTypes.MX_CTA.value]
            (learnMoreButton as? TextView)?.text = extension!!.attrs[Extension.KEY_CTATEXT]
            (learnMoreButton as? TextView)?.setTextColor(Color.parseColor(extension.attrs[Extension.KEY_CTATEXTCOLOR]))
        }else if (!TextUtils.isEmpty(advertiserName)) {
            (learnMoreButton as? TextView)?.text = advertiserName
        }

        if (adRootView!!.tag != null && adRootView!!.tag == activeAd) return
        adRootView!!.tag = activeAd
        displayContainer.getAdContainer()!!.addView(adRootView)

        adProgressText!!.visibility = View.GONE
        skipButton!!.let {
            it.visibility = View.GONE;
            it.tag = activeAd
            it.setOnClickListener(activeAd)
        }
        val clickUrl = (activeAd.ad as AdInline).mediaCreative?.videoClicks?.clickThrough
        if (clickUrl == null){
            learnMoreButton!!.visibility = View.GONE
        }else{
            clickUrl.let {
                learnMoreButton!!.let {
                    it.visibility = View.VISIBLE;
                    it.tag = activeAd
                    it.setOnClickListener(activeAd)
                }
            }
        }

    }

    override fun onAdBuffering(adMediaInfo: AdMediaInfo?) {

    }

    override fun onAdProgressUpdate(adMediaInfo: AdMediaInfo?, progress: VideoProgressUpdate) {
        if (activeAd == null || activeAd!!.ad.getMediaInfo() != adMediaInfo) return
        adProgressText!!.visibility = View.VISIBLE
        adProgressText!!.text = formatAdProgress(activeAd!!.ad.getAdPodInfo(), progress)
        if (activeAd!!.ad.isSkippable() && (progress.durationMs / 1000) > activeAd!!.ad.getSkipTimeOffset()) {
            if ((progress.currentTimeMs / 1000) >= activeAd!!.ad.getSkipTimeOffset()) {
                // Allow skipping.
                skipButton!!.text = context.getString(R.string.skip_ad)
                skipButton!!.isEnabled = true
                skipButton!!.textSize = 16f
            } else {
                val skipString = String.format(
                        Locale.US, "You can skip this ad in %d",
                        (activeAd!!.ad.getSkipTimeOffset() -
                                (progress.currentTimeMs / 1000)).toInt())
                skipButton!!.text = skipString
                skipButton!!.isEnabled = false
                skipButton!!.textSize = 12f
            }
            skipButton!!.visibility = View.VISIBLE
        } else {
            skipButton!!.visibility = View.INVISIBLE
        }
    }

    override fun unbind(){
        if (adRootView == null || adRootView!!.tag == null) return
        adRootView!!.tag = null
        displayContainer.getAdContainer()!!.removeView(adRootView)
        adProgressText!!.visibility = View.GONE
        skipButton!!.visibility = View.GONE
        skipButton!!.tag = null
        learnMoreButton!!.visibility = View.GONE
        learnMoreButton!!.tag = null
        activeAd = null
    }



    private fun formatAdProgress(podInfo: AdPodInfo, update: VideoProgressUpdate): SpannableString {
        val adProgress = formatTime((update.durationMs/1000 - update.currentTimeMs/1000).toFloat())
        val progress: String = if (podInfo.totalAds > 1) {
            context.getString(R.string.txt_ad_progress, podInfo.adPosition,
                    podInfo.totalAds, adProgress)
        } else {
            context.getString(R.string.txt_ad_progress_without_group, adProgress)
        }
        val prefix: String = context.getString(R.string.ad_prefix)
        val dot: String = context.getString(R.string.dot_unicode_char)
        val spannableString = SpannableString(prefix + dot + progress)
        spannableString.setSpan(ForegroundColorSpan(context.getResources().getColor(R.color.dot_color)), prefix.length, prefix.length + dot.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }

    override fun hide() {
        adRootView?.visibility = View.GONE
    }

    override fun show() {
        adRootView?.visibility = View.VISIBLE
    }


}