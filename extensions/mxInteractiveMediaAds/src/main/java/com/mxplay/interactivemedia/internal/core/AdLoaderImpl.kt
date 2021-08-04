package com.mxplay.interactivemedia.internal.core

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.mxplay.interactivemedia.api.*
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.VMAPModel
import com.mxplay.interactivemedia.internal.data.xml.ProtocolException
import com.mxplay.interactivemedia.internal.data.xml.VastXmlParser
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper
import com.mxplay.interactivemedia.internal.tracking.ITrackersHandler
import com.mxplay.interactivemedia.internal.tracking.MxOmid
import com.mxplay.interactivemedia.internal.tracking.TrackersHandler
import com.mxplay.mediaads.exo.OmaUtil
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import org.xml.sax.SAXParseException
import java.lang.ref.WeakReference
import java.util.*

data class AdRequestTask(val request: AdsRequest)

class AdLoaderImpl(context: Context, private val configuration : Configuration, private val omaUtil: OmaUtil, private val sdkSettings: OmSdkSettings, private val adDisplayContainer: AdDisplayContainer, private val xmlParserHelper: XmlParserHelper = XmlParserHelper) : AdsLoader {

    companion object {
        const val TAG = "AdLoader"
    }

    private val context: WeakReference<Context> = WeakReference(context)
    private val ioOpsScope: CoroutineScope = CoroutineScope(SupervisorJob() + configuration.ioDispatcher)

    private var vmapModel: VMAPModel? = null
    private val xmlPullParser by lazy { xmlParserHelper.createNewParser() }

    private var trackersHandler: ITrackersHandler? = null
    private var mxOmid : MxOmid? = null
    private val remoteDataSource = createRemoteDataSource(configuration)


    private fun createRemoteDataSource(configuration: Configuration): RemoteDataSource {
        return RemoteDataSource(configuration)
    }

    class AdLoaderResponse {
        var adErrorEvent: AdErrorEvent? = null
        var adsManagerLoadedEvent: AdsManagerLoadedEvent? = null

        constructor(adErrorEvent: AdErrorEvent?) {
            this.adErrorEvent = adErrorEvent
        }

        constructor(adsManagerLoadedEvent: AdsManagerLoadedEvent?) {
            this.adsManagerLoadedEvent = adsManagerLoadedEvent
        }
    }

    private val adsLoadedListeners = Collections
            .synchronizedSet(HashSet<AdsLoader.AdsLoadedListener>())
    private val adErrorListeners = Collections
            .synchronizedSet(HashSet<AdErrorEvent.AdErrorListener>())

    override fun contentComplete() {}
    override fun requestAds(adsRequest: AdsRequest) {
        schedule(AdRequestTask(adsRequest))
    }


    override fun addAdsLoadedListener(adsLoadedListener: AdsLoader.AdsLoadedListener) {
        synchronized(adsLoadedListeners) { adsLoadedListeners.add(adsLoadedListener) }
    }

    override fun removeAdsLoadedListener(adsLoadedListener: AdsLoader.AdsLoadedListener) {
        synchronized(adsLoadedListeners) { adsLoadedListeners.remove(adsLoadedListener) }
    }

    override fun addAdErrorListener(adErrorListener: AdErrorEvent.AdErrorListener) {
        synchronized(adErrorListeners) { adErrorListeners.add(adErrorListener) }
    }

    override fun removeAdErrorListener(adErrorListener: AdErrorEvent.AdErrorListener) {
        synchronized(adErrorListeners) { adErrorListeners.remove(adErrorListener) }
    }

    override fun release() {
        adsLoadedListeners.clear()
        adErrorListeners.clear()
        ioOpsScope.cancel()
    }


    private fun schedule(task: AdRequestTask) {
        ioOpsScope.launch {
            val response = processAdRequest(task.request)
            withContext(configuration.mainDispatcher) {
                mxOmid?.ensureOmidActivated(configuration.context)
                notifyListeners(response)
            }
        }
    }

    private fun processAdRequest(adsRequest: AdsRequest): AdLoaderResponse {
        return try {
            val vastProcessor = VastXmlParser(xmlPullParser)
            if (adsRequest.adsResponse != null) {
                vmapModel = xmlParserHelper.parseXmlResponse(xmlPullParser, vastProcessor, adsRequest.adsResponse)
                return toAdLoaderResponse(adsRequest)
            } else {
                val response = remoteDataSource.fetchDataFromUri(adsRequest.adTagUrl)
                if (response.isSuccessful) {
                    val responseBody: ResponseBody = response.body()!!
                    val content = responseBody.string()
                    if (!TextUtils.isEmpty(content)) {
                        vmapModel = xmlParserHelper.parseXmlResponse(xmlPullParser, vastProcessor, content)
                        return toAdLoaderResponse(adsRequest)
                    } else {
                        AdLoaderResponse(AdErrorEvent(AdError(AdError.AdErrorType.LOAD,
                                AdError.AdErrorCode.FAILED_TO_REQUEST_ADS, " Empty response from server"),
                                adsRequest.userRequestContext))
                    }

                } else {
                    AdLoaderResponse(AdErrorEvent(AdError(AdError.AdErrorType.LOAD,
                            AdError.AdErrorCode.FAILED_TO_REQUEST_ADS, response.message()),
                            adsRequest.userRequestContext))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "", e)
            when (e::class) {
                ProtocolException::class -> {
                    AdLoaderResponse(AdErrorEvent((e as ProtocolException).error,
                            adsRequest.userRequestContext))
                }
                SAXParseException::class -> {
                    AdLoaderResponse(AdErrorEvent(AdError(AdError.AdErrorType.LOAD,
                            AdError.AdErrorCode.VAST_MALFORMED_RESPONSE, e.message),
                            adsRequest.userRequestContext))
                }
            }

            AdLoaderResponse(AdErrorEvent(AdError(AdError.AdErrorType.LOAD,
                    AdError.AdErrorCode.FAILED_TO_REQUEST_ADS, e.message),
                    adsRequest.userRequestContext))
        }
    }

    private  fun toAdLoaderResponse(adsRequest: AdsRequest): AdLoaderResponse {
        val ctx = context.get() ?: throw IllegalStateException("Context is null")
        try {
            mxOmid  = MxOmid(remoteDataSource, configuration)
            mxOmid!!.prepare()
        } catch (e: Exception) {
            Log.e(TAG, " error in creating tracker", e)
        }
        trackersHandler = TrackersHandler(mxOmid, remoteDataSource, ioOpsScope, adDisplayContainer.getObstructionList())
        val adsManager = AdsManagerImpl(ctx, adDisplayContainer, vmapModel!!.adBreaks, adsRequest.contentProgressProvider, adsRequest.userRequestContext, trackersHandler, AdBreakLoader(ioOpsScope, remoteDataSource, sdkSettings), CompanionAdManager(ioOpsScope, remoteDataSource, trackersHandler))

        return AdLoaderResponse(
                AdsManagerLoadedEvent(adsManager, adsRequest.userRequestContext))
    }

    private fun notifyListeners(adLoaderResponse: AdLoaderResponse) {
        if (adLoaderResponse.adErrorEvent != null) {
            notifyError(adLoaderResponse.adErrorEvent!!)
        } else {
            synchronized(adsLoadedListeners) {
                for (listener in adsLoadedListeners) {
                    listener.onAdsManagerLoaded(adLoaderResponse.adsManagerLoadedEvent)
                }
            }

        }
    }

    private fun notifyError(adErrorEvent: AdErrorEvent) {
        synchronized(adErrorListeners) {
            for (listener in adErrorListeners) {
                listener.onAdError(adErrorEvent)
            }
        }
    }


}