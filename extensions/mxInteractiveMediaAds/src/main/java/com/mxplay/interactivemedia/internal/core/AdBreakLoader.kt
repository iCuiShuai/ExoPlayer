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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.util.*
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
        var uriHost: AdTagUriHost? = adBreak.getPendingAdTagUriHost()
        var maxRedirect = sdkSettings.maxRedirects

        while (uriHost != null) {
            val response = remoteDataSource.fetchDataFromUri(uriHost.getPendingAdTagUri()!!)
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
                                    uriHost.handleAdTagUriResult(vastModel)
                                    adBreak.refreshAds()
                                }
                            }
                        }

                        event = pullParser.next()
                    }

                    uriHost = uriHost.getPendingAdTagUriHost()
                }
            } else {
                throw IOException("invalid response from server")
            }
            maxRedirect--
            if (maxRedirect == 0) throw MaxRedirectLimitReachException("MAX redirect limit reached ${sdkSettings.maxRedirects}")
            yield()
        }

    }


}