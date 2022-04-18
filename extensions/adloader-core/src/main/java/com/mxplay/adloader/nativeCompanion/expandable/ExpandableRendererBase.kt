package com.mxplay.adloader.nativeCompanion.expandable

import android.animation.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.logger.ZenLogger
import org.json.JSONObject

abstract class ExpandableRendererBase(val context: Context, val container: ViewGroup, val json: JSONObject, val eventsTracker: EventsTracker, val companionResourceProvider: CompanionResourceProvider) : NativeCompanion.NativeCompanionRenderer {

    companion object {
        const val TAG = "ExpandableRendererBase"
    }

    private var companionHideAnimators: AnimatorSet? = null
    private var companionShowAnimators: AnimatorSet? = null
    private var expandHandler: ImageButton? = null
    private var nativeCompanionView: View? = null

    private fun createNativeCompanionView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_native_expandable_companion, container, false) as LinearLayout
        container.addView(view)
        companionResourceProvider.loadImage(json.optString("logo"), view.findViewById<ImageView>(R.id.logo))
        view.findViewById<TextView>(R.id.title).text = json.optString("title")
        view.findViewById<TextView>(R.id.subtitle).text = json.optString("description")
        val action = view.findViewById<TextView>(R.id.cta_button)
        bindCTA(action)


        expandHandler = view.findViewById<ImageButton>(R.id.expand)
        val templateView: View = renderChildView(view)
        expandHandler!!.visibility = View.VISIBLE
        templateView.visibility = View.GONE
        view.addView(templateView, LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        expandHandler!!.setOnClickListener {
            expandHandler?.setTag(R.id.tag_visibility, true)
            if (templateView.visibility == View.VISIBLE) {
                startCollapseAnimation(templateView, object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        templateView.visibility = View.GONE
                        action.visibility = View.VISIBLE
                        expandHandler?.setImageResource(R.drawable.navigation_down_arrow)
                    }
                })
            } else {
                startExpandAnimation(templateView, object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        expandHandler?.setImageResource(R.drawable.ic_close__light)
                    }

                    override fun onAnimationStart(animation: Animator?) {
                        super.onAnimationStart(animation)
                        action.visibility = View.INVISIBLE
                    }
                })
            }
        }
        return view

    }

    protected fun bindCTA(action: TextView) {
        if (json.has("clickThroughUrl")) {
            action.text = json.optString("CTA") ?: context.getString(R.string.cta_learn_more)
            action.setOnClickListener {
                kotlin.runCatching {
                    context.startActivity(Intent().apply {
                        setAction(Intent.ACTION_VIEW)
                        data = Uri.parse(json.getString("clickThroughUrl"))
                    })
                    trackClick(json)
                }
            }
        } else {
            action.visibility = View.GONE
        }
    }

    protected  fun trackClick(json: JSONObject) {
        val clickTrackerUrls = json.optJSONArray("clickTracker")
        clickTrackerUrls?.let {
            val urls = mutableListOf<String>()
            for (i in 0 until it.length()) {
                if (!TextUtils.isEmpty(it.getString(i))) {
                    urls.add(it.getString(i))
                }
            }
            eventsTracker.trackClick(urls, json)
        }
    }


    private fun startExpandAnimation(animateView: View, animateAdapter: AnimatorListenerAdapter? = null): AnimatorSet {
        // #debug debug
        animateView.visibility = View.VISIBLE
        val matchParentMeasureSpec = View.MeasureSpec.makeMeasureSpec((animateView.parent as View).width, View.MeasureSpec.EXACTLY)
        val wrapContentMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        animateView.measure(matchParentMeasureSpec, wrapContentMeasureSpec)
        val targetHeight: Int = animateView.getMeasuredHeight()
        ZenLogger.dt(TAG, "running expand animation targetHeight : ${targetHeight}")
        animateView.layoutParams.height = 1

        val showAnimators = AnimatorSet()
        val alphaAnimator = ObjectAnimator.ofFloat(animateView, "alpha", 0.0f, 1.0f)
        val heightAnimator = ValueAnimator.ofInt(0, targetHeight)
        showAnimators.playTogether(heightAnimator, alphaAnimator)
        showAnimators.duration = ExpandableNativeCompanion.DURATION_LONG
        showAnimators.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                animateAdapter?.onAnimationStart(animation)
            }

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                val params = animateView.layoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    animateView.layoutParams = params
                }
                animateAdapter?.onAnimationEnd(animation)
            }
        })
        heightAnimator.addUpdateListener { animation: ValueAnimator ->
            val params = animateView.layoutParams
            if (params != null) {
                params.height = animation.animatedValue as Int
                animateView.layoutParams = params
            }
        }
        showAnimators.start()
        return showAnimators
    }


    private fun startCollapseAnimation(animateView: View, animateAdapter: AnimatorListenerAdapter? = null): AnimatorSet {
        // #debug debug
        ZenLogger.dt(TAG, " running collapse animation ")
        val hideAnimators = AnimatorSet()
        val alphaAnimator = ObjectAnimator.ofFloat(animateView, "alpha", 1.0f, 0.0f)
        val heightAnimator = ValueAnimator.ofInt(animateView.measuredHeight, 0)
        hideAnimators.playTogether(heightAnimator, alphaAnimator)
        hideAnimators.duration = ExpandableNativeCompanion.DURATION_SHORT
        hideAnimators.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                animateAdapter?.onAnimationStart(animation)
            }

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                animateAdapter?.onAnimationEnd(animation)
            }
        })
        heightAnimator.addUpdateListener { animation: ValueAnimator ->
            val params = animateView.layoutParams
            if (params != null) {
                params.height = animation.animatedValue as Int
                animateView.layoutParams = params
            }
        }
        hideAnimators.start()
        return hideAnimators
    }

    final override fun render(): View {
        val impressionUrl = json.optJSONArray("impressionTracker")
        impressionUrl?.let {
            val urls = mutableListOf<String>()
            for (i in 0 until it.length()) {
                if (!TextUtils.isEmpty(it.getString(i))) {
                    urls.add(it.getString(i))
                }
            }
            eventsTracker.trackAdImpression(urls, json)
        }
        return createNativeCompanionView()
    }

    abstract fun renderChildView(parent : ViewGroup) : View

    final override fun release() {
        ZenLogger.dt(TAG, " release ")
        nativeCompanionView = null
    }



}