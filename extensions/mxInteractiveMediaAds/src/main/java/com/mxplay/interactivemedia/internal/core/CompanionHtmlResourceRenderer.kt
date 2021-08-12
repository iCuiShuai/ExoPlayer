package com.mxplay.interactivemedia.internal.core

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.*
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.api.CompanionAdEventListener
import com.mxplay.interactivemedia.internal.api.ICompanionRenderer
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.CompanionAdData
import com.mxplay.interactivemedia.internal.data.model.CompanionAdEvent
import com.mxplay.interactivemedia.internal.tracking.ITrackersHandler
import kotlinx.coroutines.CoroutineScope
import java.util.*
import kotlin.collections.HashSet

class CompanionHtmlResourceRenderer(val ioOpsScope: CoroutineScope, private val remoteDataSource: RemoteDataSource, trackersHandler: ITrackersHandler?): ICompanionRenderer {

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
            if (it.resourceType == CompanionAdData.TAG_HTML_RESOURCE) {
                render(it)
            }
        }
    }

    private fun render(companionInfo: AdCompanionInfo) {
        val companionAd = companionInfo.companionAd as CompanionAdData
        val companionContainer = companionInfo.companionAdSlot.container
        val resource = companionAd.resourceValue
        val width = px2dp(companionAd.width, companionContainer.context)
        val height = px2dp(companionAd.height, companionContainer.context)
        val webView = WebView(companionContainer.context)
        webView.layoutParams = ViewGroup.LayoutParams(width, height)
        companionContainer.removeAllViews()
        companionContainer.addView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.blockNetworkImage = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.settings.setAppCacheEnabled(true)
        webView.settings.databaseEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(true)
        webView.setInitialScale(1)
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.builtInZoomControls = false
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (!url.isNullOrEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    companionContainer.context.startActivity(intent)
                    onEvent(CompanionAdEvent(AdEventImpl(AdEvent.AdEventType.CLICKED, null, null), companionAd))
                    true
                } else {
                    false
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                return super.shouldInterceptRequest(view, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
            }
        }
        webView.loadDataWithBaseURL(null, resource, "text/html", "utf-8", null)
        onEvent(CompanionAdEvent(AdEventImpl(AdEvent.AdEventType.CREATIVE_VIEW, null, null), companionAd))
    }

    override fun load(companionAdInfo: List<AdCompanionInfo>?) {
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