package com.mxplay.interactivemedia.internal.tracking

import android.content.Context
import android.text.TextUtils
import com.iab.omid.library.mxplayerin.Omid
import com.iab.omid.library.mxplayerin.adsession.*
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.AdVerification
import com.mxplay.interactivemedia.api.Configuration
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.*

class MxOmid(val remoteDataSource : RemoteDataSource, private val configuration : Configuration) {
    var OMID_JS_SERVICE_CONTENT: String? = null
    private var partner: Partner? = null



    init {
        val pInfo = configuration.context.packageManager.getPackageInfo(configuration.context.packageName, 0)
        partner = Partner.createPartner(configuration.trackersConfig.omPartnerName, pInfo.versionName)
    }

    @Throws(IOException::class)
    fun prepare(){
        OMID_JS_SERVICE_CONTENT = remoteDataSource.fetchOmSdkJs()
    }


    fun createSession(appContext: Context, adVerification: AdVerification, isVideo: Boolean): AdSession? {
        var adSession: AdSession? = null
        try {
            if (partner == null || OMID_JS_SERVICE_CONTENT == null) {
                return null
            }
            ensureOmidActivated(appContext.applicationContext)
            val context = AdSessionContext.createNativeAdSessionContext(partner, OMID_JS_SERVICE_CONTENT, getListVerificationScriptResources(adVerification),
                    configuration.trackersConfig.omContentUrl, configuration.trackersConfig.customRefrenceData)
            val adSessionConfiguration = AdSessionConfiguration.createAdSessionConfiguration(if (isVideo) CreativeType.VIDEO else CreativeType.NATIVE_DISPLAY, ImpressionType.BEGIN_TO_RENDER,
                    Owner.NATIVE, if (isVideo) Owner.NATIVE else Owner.NONE, false)
            adSession = AdSession.createAdSession(adSessionConfiguration, context)
        } catch (e: IllegalArgumentException) { // TODO error handling
            e.printStackTrace()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }
        return adSession
    }

    @Throws(MalformedURLException::class)
    private fun getListVerificationScriptResources(adVerification: AdVerification): List<VerificationScriptResource> {
        val verificationScriptResources: MutableList<VerificationScriptResource> = ArrayList()
        val verificationScriptResource = getVerificationScriptResource(adVerification)
        verificationScriptResources.add(verificationScriptResource)
        return verificationScriptResources
    }

    @Throws(MalformedURLException::class)
    private fun getVerificationScriptResource(adVerification: AdVerification): VerificationScriptResource {
        return if (TextUtils.isEmpty(adVerification.params) || TextUtils.isEmpty(adVerification.vendorKey)) {
            VerificationScriptResource.createVerificationScriptResourceWithoutParameters(
                    URL(adVerification.url))
        } else VerificationScriptResource.createVerificationScriptResourceWithParameters(adVerification.vendorKey,
                URL(adVerification.url), adVerification.params)
    }

    fun ensureOmidActivated(context: Context) {
        Omid.activate(context.applicationContext)
    }
}