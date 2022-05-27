package com.mxplay.adloader.nativeCompanion

import android.animation.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import com.mxplay.adloader.nativeCompanion.expandable.PlayerBottomCompanion
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.logger.ZenLogger
import org.json.JSONObject
import java.util.*

abstract class NativeCompanion() : AdEvent.AdEventListener{

    companion object{
        const val TAG = "NativeCompanion"
    }

    var companionState = CompanionState.NONE
    val adExtensionSessionId = UUID.randomUUID().toString()
    val handler = Handler(Looper.getMainLooper())

    abstract fun preload()
    abstract fun display()
    open fun isAdExpanded() = false


    enum class NativeCompanionType(val value: String) {
        SURVEY_AD("survey"),
        EXPANDABLE("expandable"),
        ENDCARD("endCard"),
        NONE("none")
    }

    interface NativeCompanionTemplate {
        val id: String
        val renderer: NativeCompanionRenderer
        fun loadCompanionTemplate()
        fun showCompanionTemplate() : View?
    }

    interface NativeCompanionRenderer {
        fun render() : View?
        fun release()

        fun px2dp(px: Int, context: Context): Int {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    px * 1F,
                    context.resources.displayMetrics).toInt()
        }
    }



    override fun onAdEvent(adEvent: AdEvent) {

    }

    @CallSuper
    open fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    fun startExpandAnimation(animateView: View, animateAdapter: AnimatorListenerAdapter? = null, parentHeight: Int = -1): AnimatorSet {
        val targetHeight: Int = if (parentHeight > 0) parentHeight else animateView.measuredHeight
        ZenLogger.dt(TAG, "running expand animation targetHeight : ${targetHeight}")
        animateView.layoutParams.height = 1
        animateView.visibility = View.VISIBLE

        val showAnimators = AnimatorSet()
        val alphaAnimator = ObjectAnimator.ofFloat(animateView, "alpha", 0.0f, 1.0f)
        val heightAnimator = ValueAnimator.ofInt(0, targetHeight)
        showAnimators.playTogether(heightAnimator, alphaAnimator)
        showAnimators.duration = PlayerBottomCompanion.DURATION_SHORT
        showAnimators.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                animateAdapter?.onAnimationStart(animation)
                val params = animateView.layoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    animateView.layoutParams = params
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                super.onAnimationCancel(animation)
                val params = animateView.layoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    animateView.layoutParams = params
                }
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
        showAnimators.start()
        return showAnimators
    }


    fun startCollapseAnimation(animateView: View, animateAdapter: AnimatorListenerAdapter? = null): AnimatorSet {
        // #debug debug
        ZenLogger.dt(TAG, " running collapse animation ${animateView.measuredHeight}")
        val hideAnimators = AnimatorSet()
        val alphaAnimator = ObjectAnimator.ofFloat(animateView, "alpha", 1.0f, 0.0f)
        val heightAnimator = ValueAnimator.ofInt(animateView.measuredHeight, 1)
        hideAnimators.playTogether(heightAnimator, alphaAnimator)
        hideAnimators.duration = PlayerBottomCompanion.DURATION_SHORT
        hideAnimators.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                animateAdapter?.onAnimationStart(animation)
            }

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                animateAdapter?.onAnimationEnd(animation)
                animateView.visibility = View.INVISIBLE
                val params = animateView.layoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    animateView.layoutParams = params
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                super.onAnimationCancel(animation)
                animateView.visibility = View.INVISIBLE
                val params = animateView.layoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    animateView.layoutParams = params
                }
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
}