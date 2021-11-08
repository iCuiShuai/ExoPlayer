package com.mxplay.interactivemedia.internal.core

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.api.CompanionAdEventListener
import com.mxplay.interactivemedia.internal.api.ICompanionRenderer
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.CompanionAdData
import com.mxplay.interactivemedia.internal.data.model.CompanionAdEvent
import com.mxplay.interactivemedia.internal.data.model.StaticCompanionResource
import com.mxplay.interactivemedia.internal.tracking.ITrackersHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.*
import kotlin.collections.HashSet

class CompanionStaticResourceRenderer (val ioOpsScope: CoroutineScope, private val remoteDataSource: RemoteDataSource, trackersHandler: ITrackersHandler?) : ICompanionRenderer {

    private val companionAdsEventListeners: MutableSet<CompanionAdEventListener> = Collections
            .synchronizedSet(HashSet())

    init {
        trackersHandler?.let {
            companionAdsEventListeners.add(it)
        }
    }

    override fun render(companionAdInfo: List<AdCompanionInfo>?) {
        if (companionAdInfo.isNullOrEmpty()) {
            return
        }
        companionAdInfo.forEach {
            if (it.resourceType == CompanionAdData.TAG_STATIC_RESOURCE) {
                render(it)
            }
        }
    }

    private fun render(companionInfo: AdCompanionInfo) {
        val companionAd = companionInfo.companionAd as CompanionAdData
        val companionContainer = companionInfo.companionAdSlot.container
        val resource = companionInfo.getResource()
        val imageView = ImageView(companionContainer.context)
        if (resource is StaticCompanionResource) {
            imageView.setImageDrawable(resource.bitmapDrawable)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            companionContainer.removeAllViews()
            companionContainer.addView(imageView)
            onEvent(CompanionAdEvent(AdEventImpl(AdEvent.AdEventType.CREATIVE_VIEW, null, null), companionAd))
            imageView.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(companionAd.clickUrl)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                companionContainer.context.startActivity(intent)
                onEvent(CompanionAdEvent(AdEventImpl(AdEvent.AdEventType.COMPANION_CLICKED, null, null), companionAd))
            }
        }
    }

    override fun load(companionAdInfo: List<AdCompanionInfo>?) {
        if (companionAdInfo.isNullOrEmpty()) {
            return
        }
        companionAdInfo.forEach {
            if(it.resourceType == CompanionAdData.TAG_STATIC_RESOURCE) {
                load(it)
            }
        }
    }

    private fun load(companionInfo: AdCompanionInfo) {
        ioOpsScope.launch {
            try {
                withTimeout(8000L) {
                    val response = remoteDataSource.fetchCompanionResource(companionInfo.companionAd.resourceValue)
                    if (response.isSuccessful){
                        val context = companionInfo.companionAdSlot.container.context
                        val width = px2dp(companionInfo.companionAd.width, context)
                        val height = px2dp(companionInfo.companionAd.height, context)
                        val scaledBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(response.body()?.byteStream()), width, height, false)
                        companionInfo.setResource(StaticCompanionResource(BitmapDrawable(context.resources, scaledBitmap)))
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    override fun release(companionAdSlots: List<CompanionAdSlot>?) {
        companionAdSlots?.forEach {
            val container = it.container
            container.removeAllViews()
        }
    }

    private fun onEvent(event: CompanionAdEvent) {
        synchronized(companionAdsEventListeners) {
            for(listener in companionAdsEventListeners) {
                listener.onEvent(event)
            }
        }
    }
}