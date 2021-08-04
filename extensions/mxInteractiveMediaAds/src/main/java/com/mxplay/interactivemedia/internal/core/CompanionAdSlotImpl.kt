package com.mxplay.interactivemedia.internal.core

import android.view.View
import android.view.ViewGroup
import com.mxplay.interactivemedia.api.CompanionAdSlot

class CompanionAdSlotImpl : CompanionAdSlot {
    private var width = 0
    private var height = 0
    private var viewContainer: ViewGroup? = null
    private var childViewTag: String = "companionChild"
    private var clickListeners: MutableList<CompanionAdSlot.ClickListener>? = null


    override fun isFilled(): Boolean {
        return viewContainer!!.findViewWithTag<View?>(childViewTag) != null
    }


    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    override fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }



    override  fun getContainer(): ViewGroup? {
        return viewContainer
    }

    override fun setContainer(var1: ViewGroup?) {
        viewContainer = var1
    }

    override fun addClickListener(listener: CompanionAdSlot.ClickListener) {
        clickListeners!!.add(listener)
    }

    override fun removeClickListener(listener: CompanionAdSlot.ClickListener) {
        clickListeners!!.remove(listener)
    }

    fun getAllClickListeners(): List<CompanionAdSlot.ClickListener>? {
        return clickListeners
    }
}