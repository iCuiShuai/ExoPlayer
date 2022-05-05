package com.mxplay.adloader.nativeCompanion.expandable

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SimpleItemDecoration : RecyclerView.ItemDecoration {
    private var leftSpace = 0
    private var topSpace = 0
    private var rightSpace = 0
    private var bottomSpace = 0
    private var left: Int
    private var right: Int
    private var top: Int
    private var bottom: Int

    constructor(left: Int, top: Int, right: Int, bottom: Int, flag: Boolean) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        if (flag) {
            leftSpace = left
            topSpace = top
            rightSpace = right
            bottomSpace = bottom
        }
    }

    /**
     *
     * @param left left margin of items
     * @param top top margin of items
     * @param right
     * @param bottom
     * @param leftSpace left padding of recyclerView
     * @param topSpace top padding of recyclerView
     * @param rightSpace
     * @param bottomSpace
     */
    constructor(left: Int, top: Int, right: Int, bottom: Int, leftSpace: Int, topSpace: Int, rightSpace: Int, bottomSpace: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.leftSpace = leftSpace
        this.topSpace = topSpace
        this.rightSpace = rightSpace
        this.bottomSpace = bottomSpace
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.left = left
        outRect.top = top
        outRect.right = right
        outRect.bottom = bottom
        val layoutManager: LinearLayoutManager = parent.getLayoutManager() as LinearLayoutManager
        if (layoutManager is GridLayoutManager) {
            val totalCount: Int = layoutManager.getItemCount()
            val spanCount: Int = (layoutManager as GridLayoutManager).getSpanCount()
            //divide exactly
            val surplusCount = totalCount % spanCount
            val childPosition: Int = parent.getChildAdapterPosition(view)
            if (layoutManager.getOrientation() === GridLayoutManager.VERTICAL) {
                //left column
                if (childPosition % spanCount == 0) {
                    outRect.left = leftSpace
                }
                //top raw
                if (childPosition < spanCount) {
                    outRect.top = topSpace
                }
                //right column
                if ((childPosition + 1) % spanCount == 0) {
                    outRect.right = rightSpace
                }
                //bottom raw
                if (surplusCount == 0 && childPosition > totalCount - spanCount - 1) {
                    outRect.bottom = bottomSpace
                } else if (surplusCount != 0 && childPosition > totalCount - surplusCount - 1) {
                    outRect.bottom = bottomSpace
                }
                if (spanCount >= 3) {
                    val widthSpace = leftSpace + rightSpace + (left + right) * (spanCount - 1)
                    val widthAverage = (widthSpace * 1.0f / spanCount).toInt()
                    if (childPosition % spanCount == 0) {
                        outRect.right = Math.max(0, widthAverage - outRect.left)
                    } else if ((childPosition + 1) % spanCount == 0) {
                        outRect.left = Math.max(0, widthAverage - outRect.right)
                    } else {
                        outRect.left = (widthAverage / 2f).toInt()
                        outRect.right = (widthAverage / 2f).toInt()
                    }
                }
            } else {
                //left column
                if (childPosition < spanCount) {
                    outRect.left = leftSpace
                }
                //top raw
                if (childPosition % spanCount == 0) {
                    outRect.top = topSpace
                }
                //right column
                if (surplusCount == 0 && childPosition > totalCount - spanCount - 1) {
                    outRect.right = rightSpace
                } else if (surplusCount != 0 && childPosition > totalCount - surplusCount - 1) {
                    outRect.right = rightSpace
                }
                //bottom raw
                if ((childPosition + 1) % spanCount == 0) {
                    outRect.bottom = bottomSpace
                }
            }
        } else {
            val totalCount: Int = layoutManager.getItemCount()
            if (layoutManager.getOrientation() === LinearLayoutManager.VERTICAL) {
                if (parent.getChildAdapterPosition(view) === 0) {
                    outRect.top = topSpace
                }
                if (parent.getChildAdapterPosition(view) === totalCount - 1) {
                    outRect.bottom = bottomSpace
                }
                outRect.left = leftSpace
                outRect.right = rightSpace
            } else {
                if (parent.getChildAdapterPosition(view) === 0) {
                    outRect.left = leftSpace
                }
                if (parent.getChildAdapterPosition(view) === totalCount - 1) {
                    outRect.right = rightSpace
                }
                outRect.top = topSpace
                outRect.bottom = bottomSpace
            }
        }
    }


}