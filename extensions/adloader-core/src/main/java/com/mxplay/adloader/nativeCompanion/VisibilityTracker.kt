package com.mxplay.adloader.nativeCompanion

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

class VisibilityTracker(val view : View, val minPercentageViewed: Int) {
    private var mOnPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var mVisibilityTrackerListener: VisibilityTrackerListener? = null
    private var mIsVisibilityCheckScheduled = false
    private val mVisibilityChecker: VisibilityChecker
    private val mVisibilityHandler: Handler
    private val mVisibilityRunnable: Runnable
    private val visibility : Boolean? = null

    interface VisibilityTrackerListener {
        fun onVisibilityChanged(isVisible: Boolean)
    }

    fun setVisibilityTrackerListener(listener: VisibilityTrackerListener?) {
        mVisibilityTrackerListener = listener
    }

    fun removeVisibilityTrackerListener() {
        mVisibilityTrackerListener = null
    }

    private fun scheduleVisibilityCheck() {
        if (mIsVisibilityCheckScheduled) {
            return
        }
        mIsVisibilityCheckScheduled = true
        mVisibilityHandler.postDelayed(mVisibilityRunnable, VISIBILITY_CHECK_DELAY_MILLIS)
    }

    internal class VisibilityChecker {
        private val mClipRect = Rect()
        fun isVisible(view: View?, minPercentageViewed: Int): Boolean {
            if (view == null || view.visibility != View.VISIBLE || view.parent == null) {
                return false
            }
            if (!view.getGlobalVisibleRect(mClipRect)) {
                return false
            }
            val visibleArea = mClipRect.height().toLong() * mClipRect.width()
            val totalViewArea = view.height.toLong() * view.width
            return totalViewArea > 0 && 100 * visibleArea >= minPercentageViewed * totalViewArea
        }
    }

    internal inner class VisibilityRunnable : Runnable {

        override fun run() {
            mIsVisibilityCheckScheduled = false
            if (mVisibilityTrackerListener != null){
                val visible = mVisibilityChecker.isVisible(view, minPercentageViewed)
                if (visible != visibility) {
                    mVisibilityTrackerListener!!.onVisibilityChanged(visible)
                }
            }
        }


    }

    companion object {
        private const val VISIBILITY_CHECK_DELAY_MILLIS: Long = 100
    }

    init {
        val viewTreeObserver = view.viewTreeObserver
        mVisibilityHandler = Handler(Looper.getMainLooper())
        mVisibilityChecker = VisibilityChecker()
        mVisibilityRunnable = VisibilityRunnable()
        if (viewTreeObserver.isAlive) {
            mOnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
                scheduleVisibilityCheck()
                true
            }
            viewTreeObserver.addOnPreDrawListener(mOnPreDrawListener)
        }
    }

    fun release(){
        val viewTreeObserver = view.viewTreeObserver
        viewTreeObserver.removeOnPreDrawListener(mOnPreDrawListener)
        mVisibilityHandler.removeCallbacksAndMessages(null)
    }
}