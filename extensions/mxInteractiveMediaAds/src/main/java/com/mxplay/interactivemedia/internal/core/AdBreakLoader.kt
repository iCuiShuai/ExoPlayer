package com.mxplay.interactivemedia.internal.core

import android.text.TextUtils
import android.util.Log
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.api.AdError
import com.mxplay.interactivemedia.api.OmSdkSettings
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper
import com.mxplay.interactivemedia.internal.data.model.AdBreak
import com.mxplay.interactivemedia.internal.data.model.AdTagUriHost
import com.mxplay.interactivemedia.internal.data.model.VASTModel
import com.mxplay.interactivemedia.internal.data.xml.ProtocolException
import com.mxplay.interactivemedia.internal.data.xml.VastXmlParser
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.util.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.jvm.Throws


class AdBreakLoader(val ioOpsScope: CoroutineScope, private val remoteDataSource: RemoteDataSource, private val sdkSettings: OmSdkSettings) {

    class MaxRedirectLimitReachException(message : String) : Exception(message)

    interface AdBreakLoadingCallback{
        fun onAdBreakLoaded(adBreak : AdBreak)
        fun onAdBreakFetchError(adBreak: AdBreak, e: Exception)
    }

    companion object{
        const val TAG  = "AdBreakLoader"
    }
    private val resolvedUris = Collections.synchronizedSet(HashSet<AdTagUriHost>())


    fun isUriResolved(adTagUri : AdTagUriHost) = resolvedUris.contains(adTagUri)

    fun loadAdBreak(adBreak: AdBreak, timeOutMs : Long, adBreakLoadCallback : AdBreakLoadingCallback){
        if (sdkSettings.isDebugMode) {
            Log.d(TAG, "loadAdBreak  ${adBreak.startTime}  media ads count ${adBreak.adsList.size} :: total ads ${adBreak.totalAdsCount} with timeOut ${timeOutMs}ms")
        }
        val pendingAdTagUriHost = adBreak.getPendingAdTagUriHost()
        pendingAdTagUriHost?.let {
            if(!resolvedUris.contains(it)){
                resolvedUris.add(it)
                ioOpsScope.launch {
                    try {
                        withTimeout(timeOutMs){
                            handleRedirection(adBreak)
                            if (adBreak.totalAdsCount == 0) throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VAST_EMPTY_RESPONSE, "Empty VAST response"))
                            adBreakLoadCallback.onAdBreakLoaded(adBreak)
                        }

                    } catch (e: Exception) {
                        adBreakLoadCallback.onAdBreakFetchError(adBreak, e)
                    } finally {

                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun handleRedirection(adBreak: AdBreak) {
        return suspendCoroutineUninterceptedOrReturn { continuation ->

            val uriHost: AdTagUriHost? = adBreak.getPendingAdTagUriHost()
            val maxRedirect = sdkSettings.maxRedirects
            val hostStack = Stack<AdTagUriHost>()
            val depthStack = Stack<Int>()
            var isHostResolved = false

            hostStack.push(uriHost)
            depthStack.push(1)

            while (!hostStack.empty() && !isHostResolved) {
                val currentUriHost = hostStack.peek()
                val currentDepth = depthStack.peek()

                if (sdkSettings.isDebugMode) Log.d(TAG, "handleRedirection: ${currentUriHost} at depth ${currentDepth}")

                val response = remoteDataSource.fetchDataFromUri(currentUriHost.getPendingAdTagUri()!!)
                if (response.isSuccessful) {
                    val responseBody: ResponseBody = response.body()!!
                    val content = responseBody.string()
                    if (!TextUtils.isEmpty(content)) {
                        val pullParser = XmlParserHelper.createNewParser().apply { setInput(StringReader(content)) }
                        val vastProcessor = VastXmlParser(pullParser)
                        var event = pullParser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (pullParser.name == VASTModel.VAST) {
                                        val vastModel = vastProcessor.parse()
                                        currentUriHost.handleAdTagUriResult(vastModel)
                                        adBreak.refreshAds()
                                    }
                                }
                            }

                            event = pullParser.next()
                        }

                        if (currentUriHost.getPendingAdTagUriHost() == null) {
                            isHostResolved = true
                        } else {
                            if (currentDepth < maxRedirect) {
                                hostStack.push(currentUriHost.getPendingAdTagUriHost())
                                depthStack.push(currentDepth + 1)
                            } else {
                                onError(adBreak, currentUriHost, hostStack, depthStack, MaxRedirectLimitReachException("MAX redirect limit reached ${sdkSettings.maxRedirects}"))
                            }
                        }
                    }
                } else {
                    onError(adBreak, currentUriHost, hostStack, depthStack, IOException("invalid response from server"))
                }
                val context = continuation.context
                val job = context[Job]
                if (job != null && !job.isActive) break
            }
            if (!isHostResolved) throw IOException("invalid response from server")

        }

    }

    @Throws(Exception::class)
    fun onError(adBreak: AdBreak, currentUriHost: AdTagUriHost, hostStack: Stack<AdTagUriHost>, depthStack: Stack<Int>, e: Exception) {
        if (!currentUriHost.isFallBackOnNoAd()) {
            currentUriHost.handleAdTagUriResult(null)
            if (sdkSettings.isDebugMode) Log.d(TAG, "onError removing: ${hostStack.peek()} at depth ${depthStack.peek()} with error:${e.message}")

            while (!hostStack.empty() && hostStack.peek().getPendingAdTagUriHost() == null) {
                hostStack.pop()
                depthStack.pop()
            }

            if (hostStack.empty() && adBreak.getPendingAdTagUriHost() != null) {
                hostStack.push(adBreak.getPendingAdTagUriHost())
                depthStack.push(1)
            } else if (!hostStack.empty() && hostStack.peek().getPendingAdTagUriHost() != null) {
                hostStack.push(hostStack.peek().getPendingAdTagUriHost())
                depthStack.push(depthStack.peek() + 1)
            }
        } else {
            throw e
        }
    }


}