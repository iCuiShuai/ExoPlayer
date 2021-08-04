package com.mxplay.interactivemedia.internal.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.Configuration
import java.util.HashMap

class UrlStitchingService(private val configuration: Configuration) {

    private val commonHeader = HashMap<String, String?>()
    private val ipAddress by lazy { NetworkUtil.getIPAddress(true) }
    private val installerPackageName by lazy { getInstallerPackageName(configuration.context) }


    private fun getNPA(): String {
        return if (configuration.nonPersonalizedAd) "1" else "0"
    }

    fun getTracker(url: String, random: String): String {
        return getTracker(url, getParamsMap(random))
    }


    private fun getTracker(url: String, map: Map<String, String?>): String {
        var mutableUrl = url
        if (!TextUtils.isEmpty(mutableUrl)) {
            for ((key, value) in map) {
                if (mutableUrl.contains("[$key]") && value != null) {
                    mutableUrl = mutableUrl.replace("[$key]", value)
                }
            }
        }
        return mutableUrl
    }

    fun getRandomNumber(): String {
        return (Math.random() * 10000000000000000L).toString()
    }

    // ERRORCODE
    //CONTENTPLAYHEAD
    //ASSETURI
    //TIMESTAMP
    fun getParamsMap(random: String): Map<String, String?> {
        return HashMap<String, String?>().apply {
            put("CACHEBUSTER", random)
            put("ADVERTISING_ID", configuration.advertiserId)
            put("ADVERTISING_ID_TYPE", "GAID")
            put("APP_NAME", configuration.appName)
            put("NPA", getNPA())
            put("UA", configuration.userAgent)
            put("IP", ipAddress)
            put("OS", "android")
            put("CLICK_ID", random)
            put("IMPRESSION_ID", random)
        }
    }

    fun errorMacro(url: String, errorCode: Int): String {
        return url.replace("[ERRORCODE]", errorCode.toString())
    }


    fun addMacros(ad: Ad?, data: String): String {
        //TIMESTAMP
        //APPBUNDLE
        //SERVERSIDE
        var mutableData = data
        mutableData = mutableData.replace("[adv]", ad?.getTitle() ?: "")
        mutableData = mutableData.replace("[adid]", ad?.getAdId() ?: "")
        mutableData = mutableData.replace("[cid]", ad?.getCreativeId() ?: "")
        mutableData = mutableData.replace("[adunit]", configuration.adUnitId)
        mutableData = mutableData.replace("[npa]", getNPA())
        mutableData = addCommonMacros(mutableData)
        return mutableData
    }

    fun addCommonMacros(mutableData: String): String {
        var mutableData1 = mutableData
        mutableData1 = mutableData1.replace("[TIMESTAMP]", DateTimeUtils.getTimeStampMacroValue())
        mutableData1 = mutableData1.replace("[APPBUNDLE]", configuration.context.packageName)
        return mutableData1
    }


    fun commonHeaders(): MutableMap<String, String?> {
        val commonHeaders: MutableMap<String, String?> = HashMap()
        commonHeaders["clientId"] = configuration.userUIID
        commonHeaders["clientToken"] = configuration.userToken
        commonHeaders["platform"] = configuration.appName
        commonHeaders.putAll(getDeviceInfo())
        if (configuration.nonPersonalizedAd) {
            commonHeaders.remove("advertisingId")
        } else if (TextUtils.isEmpty(commonHeaders["advertisingId"]) && !TextUtils.isEmpty(configuration.advertiserId)) {
            commonHeaders["advertisingId"] = configuration.advertiserId
        }
        return commonHeaders
    }

    private fun getDeviceInfo(): Map<String, String?> {
        val deviceHeaders: MutableMap<String, String?> = HashMap()
        var locale = ""
        var networkType = ""
        var networkSubType = ""
        var mcc = ""
        var mnc = ""
        var mccMnc = ""
        try {
            if (commonHeader.isEmpty()) {
                initCommonHeader(configuration.context)
            }
        } catch (e: Exception) {
        }
        try {
            val currentLocale = configuration.context.resources.configuration.locale
            locale = currentLocale.toString()
        } catch (e: Exception) {
        }
        try {
            val cm = configuration.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkType = cm.activeNetworkInfo!!.typeName
            networkSubType = cm.activeNetworkInfo!!.subtypeName
        } catch (e: Exception) {
            //ignore
        }
        try {
            mcc = configuration.context.resources.configuration.mcc.toString() + ""
            mnc = configuration.context.resources.configuration.mnc.toString() + ""
            mccMnc = configuration.context.resources.configuration.mcc.toString() + "_" + configuration.context.resources.configuration.mnc
        } catch (e: Exception) {
            //ignore
        }
        try {
            deviceHeaders["locale"] = locale
            deviceHeaders["networkType"] = networkType
            deviceHeaders["networkSubType"] = networkSubType
            deviceHeaders["mcc"] = mcc
            deviceHeaders["mnc"] = mnc
            deviceHeaders["mccMnc"] = mccMnc
            deviceHeaders.putAll(commonHeader)
        } catch (e: Exception) {
        }
        return deviceHeaders
    }

    private fun initCommonHeader(context: Context?) {
        var versionCode = ""
        var versionName = ""
        var androidId: String? = ""
        var model: String? = ""
        var androidVersion: String? = ""
        try {
            androidId = Settings.Secure.getString(context!!.contentResolver, Settings.Secure.ANDROID_ID)
            model = Build.MODEL
            androidVersion = Build.VERSION.RELEASE
        } catch (e: Exception) {
        }
        try {
            val pInfo = context!!.packageManager.getPackageInfo(context.packageName, 0)
            versionCode = pInfo.versionCode.toString()
            versionName = pInfo.versionName
        } catch (e: Exception) {
        }
        try {
            commonHeader["installer"] = installerPackageName
            if (androidId != null) commonHeader["androidId"] = androidId
            commonHeader["versionCode"] = versionCode
            commonHeader["versionName"] = versionName
            if (model != null) commonHeader["model"] = model
            if (androidVersion != null) commonHeader["androidVersion"] = androidVersion
        } catch (e: Exception) {
        }
    }

    private fun getInstallerPackageName(context: Context): String {
        var installerPackageName: String? = null
        try {
            //TODO
//            installerPackageName = context.packageManager.getInstallSourceInfo(context.packageName).initiatingPackageName
            installerPackageName = context.packageManager.getInstallerPackageName(context.packageName)
        } catch (e: Exception) {
            // package manager died.
            e.printStackTrace()
        }
        if (installerPackageName == null || installerPackageName == "") installerPackageName = "unknown"
        return installerPackageName
    }



}