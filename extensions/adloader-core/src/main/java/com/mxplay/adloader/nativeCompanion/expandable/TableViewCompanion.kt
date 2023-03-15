package com.mxplay.adloader.nativeCompanion.expandable

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.*
import com.mxplay.adloader.nativeCompanion.expandable.data.Ad
import com.mxplay.adloader.nativeCompanion.expandable.data.CompanionTrackingInfo
import com.mxplay.adloader.nativeCompanion.expandable.data.TableViewTemplateData
import com.mxplay.adloader.nativeCompanion.view.TableItemWrapperLayout
import com.mxplay.adloader.utils.DeviceUtils
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.logger.ZenLogger

class TableViewCompanion(
    private val payload: TableViewTemplateData,
    private val companionAdSlot: CompanionAdSlot,
    private val eventsTracker: EventsTracker,
    private val resourceProvider: CompanionResourceProvider
) : PlayerBottomCompanion(
    payload,
    companionAdSlot,
    eventsTracker,
    resourceProvider
) {


    private val context: Context = companionAdSlot.getContainer()?.context!!
    private val container = companionAdSlot.getContainer()!!
    private val expandOverlayContainer : ViewGroup? = (container.parent as ViewGroup).findViewById(R.id.expandable_overlay)
    private var  templateBannerView : ViewGroup? = null
    private var  hostViewVisibilityTracker : VisibilityTracker? = null


    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)
    }


    override fun release() {
        super.release()
        ZenLogger.dt(TAG, " TableViewCompanion release")
        expandOverlayContainer?.removeAllViews()
        expandOverlayContainer?.visibility = View.GONE
    }





    override fun renderOverlay(): ViewGroup? {
        if (expandOverlayContainer == null || payload.ads.isEmpty()) return null
        ZenLogger.dt(TAG, " TableViewCompanion renderOverlay")

        templateBannerView = LayoutInflater.from(context).inflate(R.layout.layout_native_expandable_template, null, false) as LinearLayout
        resourceProvider.loadImage(payload.logoUrl(), templateBannerView!!.findViewById(R.id.logo))
        templateBannerView!!.findViewById<TextView>(R.id.title).text = payload.title
        templateBannerView!!.findViewById<TextView>(R.id.subtitle).text = payload.description
        val action = templateBannerView!!.findViewById<ImageButton>(R.id.dismiss)
        action.setOnClickListener {
            eventsTracker.trackAdHide(this, false, payload.getTrackingData())
            startCollapseAnimation(templateBannerView!!, object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    templateBannerView!!.visibility = View.GONE
                }
            })
        }
        val list = templateBannerView!!.findViewById<RecyclerView>(R.id.list)
        val dividerWidth = context.resources.getDimensionPixelSize(R.dimen.recycler_divider_width)
        val space = context.resources.getDimensionPixelSize(R.dimen.ad_content_padding)

        list.addItemDecoration(SimpleItemDecoration(dividerWidth, dividerWidth, dividerWidth, dividerWidth, space, dividerWidth, space, dividerWidth))
        if (payload.templateId == ID_CAROUSEL_IMAGE_TEMPLATE){
            list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }else{
            val spanCount = 2
            list.layoutManager = GridLayoutManager(context, spanCount)
        }

        val adapter = AdsAdapter(context, this, payload, payload.ads, resourceProvider, eventsTracker, payload.templateId)
        if (payload.templateId == ID_CAROUSEL_IMAGE_TEMPLATE){
            list.layoutParams = (list.layoutParams as LinearLayout.LayoutParams).apply {
                height = context.resources.getDimensionPixelSize( if (adapter.getItemViewType(0) == AdsAdapter.ITEM_TYPE_BASIC) R.dimen.recycler_view_height_image else R.dimen.recycler_view_height_detailed)
                weight = 0f
            }
        }
        list.adapter = adapter
        bindCTA(templateBannerView!!.findViewById(R.id.native_ad_action_button))
        return templateBannerView
    }

    override fun display() {
        super.display()
        ZenLogger.dt(TAG, " TableViewCompanion loadCompanion")
        expandOverlayContainer?.removeAllViews()
        expandOverlayContainer?.visibility = View.VISIBLE
        expandOverlayContainer?.addView(templateBannerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        getExpandHandlerView()?.let {
            it.setOnClickListener { view ->
                view.setTag(R.id.tag_visibility, true)
                templateBannerView?.visibility = View.VISIBLE
                if (hostViewVisibilityTracker?.isVisible() == true && templateBannerView?.parent != null){
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
                    it.performClick()
                }

            }

        }
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


    class AdsAdapter(
        val context: Context,
        val nativeCompanion: NativeCompanion,
        val payload: TableViewTemplateData,
        val ads: List<Ad>,
        val resourceProvider: CompanionResourceProvider,
        val eventsTracker: EventsTracker,
        val templateId : String
    ) : RecyclerView.Adapter<AdsAdapter.BasicAdViewHolder>() {
        companion object{
            const val ITEM_TYPE_BASIC = 100
            const val ITEM_TYPE_DETAILED = 101
        }
        private val inflater = LayoutInflater.from(context)

        fun getRecyclerViewSpacing(): Int {
            val space = 2* context.resources.getDimensionPixelSize(R.dimen.ad_content_padding)
            val dividerWidth = 2 * context.resources.getDimensionPixelSize(R.dimen.recycler_divider_width)
            return space + dividerWidth
        }

        open inner class BasicAdViewHolder(val view : View) : RecyclerView.ViewHolder(view), TableItemWrapperLayout.OnWindowAttachListener {
            private val image = view.findViewById<ImageView>(R.id.image)

            init {
                if(view is TableItemWrapperLayout){
                    view.addAttachedListener(this)
                }
                setUpLayoutParams()
            }

            private fun setUpLayoutParams(){

                if (templateId == ID_CAROUSEL_IMAGE_TEMPLATE){
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    image.layoutParams =  (image.layoutParams as LinearLayout.LayoutParams).apply {
                        weight = 1.0f
                        width = context.resources.getDimensionPixelSize(R.dimen.carousal_image_width)
                        height = 0
                    }
                    image.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.recycler_view_height_image)
                    view.layoutParams = view.layoutParams.apply {
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                }else{
                    val itemAspectRatio = payload.getItemAspectRatio()
                    image.layoutParams = image.layoutParams.apply {
                        val itemExpectedWidth = (DeviceUtils.getDeviceWidth() - getRecyclerViewSpacing()) / 2
                        val ratio =  itemAspectRatio?.let { it.height.toFloat() / it.width} ?: 1.0f
                        val itemHeight = (ratio * itemExpectedWidth).toInt()
                        height = itemHeight
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    if (itemAspectRatio == null){
                        image.scaleType = ImageView.ScaleType.FIT_CENTER
                    }else{
                        image.scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                }
            }

            @CallSuper
            open fun bind(ad : Ad){
                view.setTag(R.id.ad_tag_view, ad)
                resourceProvider.loadImage(ad.bannerUrl(payload.imageCdnUrl), image)
                view.setOnClickListener {
                    kotlin.runCatching {
                        context.startActivity(Intent().apply {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(ad.clickThroughUrl)
                        })
                        eventsTracker.trackClick( nativeCompanion,
                            ad.clickTracker,
                            CompanionTrackingInfo.CompanionItemTrackingInfo(ads.indexOf(ad).toString(), ad.id, payload.getTrackingData())
                        )
                    }
                }

            }

            override fun onAttachedToWindow() {
                val ad  = (view.getTag(R.id.ad_tag_view) ?: return ) as Ad
                if (!ad.isImpressed){
                    ad.isImpressed = true
                    ad.impressionTrackers?.let {
                        eventsTracker.trackAdItemImpressionStream(nativeCompanion, EventsTracker.ImpressionData(it, CompanionTrackingInfo.CompanionItemTrackingInfo(ads.indexOf(ad).toString(), ad.id, payload.getTrackingData())))
                    }
                }
            }
        }

        inner class DetailedAdViewHolder(view : View) : BasicAdViewHolder(view){
            private val title = view.findViewById<TextView>(R.id.title)
            private val price = view.findViewById<TextView>(R.id.price)
            private val cta = view.findViewById<TextView>(R.id.cta_button)
            override fun bind(ad: Ad) {
                super.bind(ad)
                if(TextUtils.isEmpty(ad.title)){
                    title.visibility = View.GONE
                }else{
                    title.text = ad.title
                    title.visibility = View.VISIBLE
                }

                if(TextUtils.isEmpty(ad.price)){
                    price.visibility = View.GONE
                }else{
                    price.text = context.getString(R.string.price, ad.price)
                    price.visibility = View.VISIBLE
                }

                cta.isEnabled = false
                if(TextUtils.isEmpty(ad.CTA)){
                    cta.visibility = View.GONE
                }else{
                    cta.text = ad.CTA
                    cta.visibility = View.VISIBLE
                }
                
            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicAdViewHolder {
            if (viewType == ITEM_TYPE_BASIC){
                return BasicAdViewHolder(inflater.inflate(R.layout.list_item_image, parent, false))
            }else{
                return DetailedAdViewHolder(inflater.inflate(R.layout.list_item_with_details, parent, false))
            }
        }

        override fun onBindViewHolder(holder: BasicAdViewHolder, position: Int) {
            holder.bind(ads[position])
        }

        override fun getItemCount(): Int {
            return ads.size
        }

        override fun getItemViewType(position: Int): Int {
            return if (ads.getOrNull(position)?.itemType == Ad.TYPE_IMAGE) ITEM_TYPE_BASIC else ITEM_TYPE_DETAILED
        }

    }

}


