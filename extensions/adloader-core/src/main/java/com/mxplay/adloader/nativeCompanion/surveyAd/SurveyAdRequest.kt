package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import com.google.gson.Gson
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.logger.ZenLogger
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import java.util.HashMap

class SurveyAdRequest private constructor(builder: Builder) {
    private var adResponse: SurveyAdsResponse? = null
    private var url = builder.url
    private val retry: Int = builder.retry
    private var retryCount = 0
    private val method: String = builder.method
    private val body: String? = builder.body
    private val mxAdListener: SurveyAdsListener? = builder.listener
    private val requestParams: HashMap<String, String>? = builder.params
    private val remoteDataSource = builder.remoteDataSource
    private val companionSdkScope: CoroutineScope = builder.companionSdkScope
    private var isAdLoading = false

    companion object {
        private const val TAG = "SurveyAdsRequest"
        private const val GET = "GET"
        private const val POST = "POST"

        private const val SURVEY_PATH_GET = "survey"
        private const val SURVEY_PATH_POST = "surveyresponse"
    }

    fun request() {
        val url = getUrl()
        if (!TextUtils.isEmpty(url)) {
            isAdLoading = true
            if (method == GET) {
                getSurveyResponse(url!!)
            } else {
                if (!TextUtils.isEmpty(body)) {
                    postSurveyResponse(url!!, body!!)
                }
            }
        }
    }

    private fun getUrl(): String? {
        if (!TextUtils.isEmpty(url)) {
            if (method == GET) return url + SURVEY_PATH_GET
            else {
                val httpUrl = HttpUrl.parse(url + SURVEY_PATH_POST)
                if (!requestParams.isNullOrEmpty()) {
                    val urlBuilder: HttpUrl.Builder? = httpUrl?.newBuilder()
                    for ((key, value) in requestParams.entries) {
                        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                            urlBuilder?.addQueryParameter(key, value)
                        }
                    }
                    return urlBuilder?.build()?.toString()
                }
            }
        }
        return null
    }

    fun isLoaded(): Boolean {
        return adResponse != null
    }

    fun onAdResponseReceived(response: SurveyAdsResponse?) {
        ZenLogger.et(TAG, "onAdResponseReceived ${response}")
        this.adResponse = response
        mxAdListener?.onSuccess(response)
        isAdLoading = false
    }

    private fun onApiResponseReceived(response: Response) {
        // decrypt response
        val deResponse = response
        var content: String? = null
        ZenLogger.et(TAG, "onApiResponseReceived ${response} ${deResponse}")
        try {
            val responseBody: ResponseBody? = deResponse.body()
            content = responseBody?.string()

            ZenLogger.et(TAG, "onApiResponseReceived ${responseBody} ${content}")

            // reponse failed
            if (!response.isSuccessful) {
                onFailed(response.code(), response.message(), content)
                return
            }

            // decrypt reponse failed
            if (!deResponse.isSuccessful) {
                onFailed(deResponse.code(), deResponse.message(), content)
                return
            }

            if (TextUtils.isEmpty(content)) {
                onFailed(deResponse.code(), "Read response body failed", content)
                return
            }

            onSucceed(content)
        } catch (e: Exception) {
            e.printStackTrace()
            ZenLogger.et(TAG, "onApiResponseReceived ${e} ${e.message}")
        }
    }

    private fun getSurveyResponse(url: String) {
        companionSdkScope.launch {
            try {
                val response  = withTimeout(5000) {
                    remoteDataSource.fetchDataFromUriAsync(url, requestParams ?: mapOf())
                }
                onApiResponseReceived(response)
            } catch (e: Exception) {
                e.printStackTrace()
                ZenLogger.et(TAG, "Survey Get Request Failed ${e.message}")
                onFailed(0, "Survey Get Request Failed", null)
            }
        }
    }

    private fun postSurveyResponse(url: String, json: String) {
        companionSdkScope.launch {
            try {
                val response  = withTimeout(5000) {
                    remoteDataSource.postDataToUriAsync(url, json)
                }
                onApiResponseReceived(response)
            } catch (e: Exception) {
                onFailed(0, "Survey response Post request failed", null)
            }
        }
    }

    private fun onSucceed(result: String?) {
        var apiAdResponse: SurveyAdsResponse? = null
        if (method == GET) {
            try {
                if (!TextUtils.isEmpty(result)) {
                    apiAdResponse = Gson().fromJson(result, SurveyAdsResponse::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ZenLogger.et(TAG, "onSucceed json parsing error ${e.message}")
            }
            if ((apiAdResponse == null || apiAdResponse.isEmpty())) {
                onFailed(400, "Not Valid Response", null)
            } else {
                onAdResponseReceived(apiAdResponse)
            }
        } else {
            onAdResponseReceived(apiAdResponse)
        }
    }

    fun onFailed(errCode: Int, errMsg: String?, errBody: String?) {
        ZenLogger.et(TAG, "onFailed ${errCode} ${errMsg} ${errBody}")
        val errJson = if (!TextUtils.isEmpty(errBody)) JSONObject(errBody) else null
        if (method == POST && errCode == 400 && errJson?.optString("statusCode", "") == "alreadyresponded") {
            mxAdListener?.surveyAlreadyResponded()
            isAdLoading = false
            return
        }
        if (retryCount < retry) {
            retryCount++
            request()
        } else {
            mxAdListener?.onFailed(errCode)
            isAdLoading = false
        }
    }

    class Builder(val remoteDataSource: RemoteDataSource, val companionSdkScope: CoroutineScope) {
        var method: String = GET
        var url: String = ""
        var params: HashMap<String, String>? = null
        var listener: SurveyAdsListener? = null
        var body: String? = null
        var retry: Int = 0

        fun addRequestBody(json: String?): Builder {
            body = json
            return this
        }

        fun <CL> addRequestBody(obj: CL): Builder {
            val json: String = Gson().toJson(obj)
            body = json
            return this
        }

        fun get(): Builder {
            method = GET
            return this
        }

        fun post(): Builder {
            method = POST
            return this
        }

        fun url(url: String): Builder {
            this.url = url
            return this
        }

        fun addParam(key: String, value: String?): Builder {
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                return this
            }
            updateParams(key, value!!)
            return this
        }

        private fun updateParams(key: String, value: String) {
            if (params == null) {
                params = HashMap()
            }
            params!![key] = value
        }

        fun listener(listener: SurveyAdsListener?): Builder {
            this.listener = listener
            return this
        }

        fun retry(value: Int): Builder {
            retry = value
            return this
        }

        fun surveyId(value: String?): Builder {
            addParam("surveyId", value)
            return this
        }

        fun build(): SurveyAdRequest {
            return SurveyAdRequest(this)
        }

        fun addParams(params: Map<String, String>?): Builder {
            if (params == null) {
                return this
            }
            if (this.params == null) {
                this.params = HashMap()
            }
            this.params!!.putAll(params)
            return this
        }
    }

    interface SurveyAdsListener {
        fun onSuccess(response: SurveyAdsResponse?)
        fun onFailed(errCode: Int)
        fun surveyAlreadyResponded()
    }
}