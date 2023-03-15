package com.mxplay.adloader.nativeCompanion.surveyAd

import android.text.TextUtils
import com.google.gson.Gson
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAdsResponse
import com.mxplay.adloader.nativeCompanion.surveyAd.model.SurveyAnswerType
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.logger.ZenLogger
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private var mxAdListener: SurveyAdsListener? = builder.listener
    private val requestParams: MutableMap<String, String> = builder.params ?: mutableMapOf()
    private val remoteDataSource = builder.remoteDataSource
    private val companionSdkScope: CoroutineScope = builder.companionSdkScope
    private var isAdLoading = false
    private var requestJob: Job? = null

    companion object {
        private const val TAG = "SurveyAdsRequest"
        private const val GET = "GET"
        private const val POST = "POST"

        private const val SURVEY_PATH_GET = "survey"
        private const val SURVEY_PATH_POST = "surveyresponse"
    }

    private fun addSupportedSurveyTypes() {
        var supportedTypes = ""
        SurveyAnswerType.values().map { it.value }.forEach { type ->
            if(supportedTypes.isNotEmpty()) {
                supportedTypes += ","
            }
            supportedTypes += type
        }
        requestParams["supportedAnswerTypes"] = supportedTypes
    }

    fun request() {
        addSupportedSurveyTypes()
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
                val httpUrl = (url + SURVEY_PATH_POST).toHttpUrlOrNull()
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
        this.adResponse = response
        mxAdListener?.onSuccess(response)
        isAdLoading = false
    }

    private suspend fun onApiResponseReceived(response: HttpResponse) = withContext(Dispatchers.IO) {
        // decrypt response0
        val deResponse: HttpResponse = response
        var content: String? = null
        try {
            content = deResponse.body()

            // reponse failed
            if (!remoteDataSource.isSuccessful(response)) {
                onFailed(response.status.value, response.status.description, content)
            } else if (!remoteDataSource.isSuccessful(deResponse)) { // decrypt reponse failed
                onFailed(deResponse.status.value, deResponse.status.description, content)
            } else if (TextUtils.isEmpty(content)) {
                onFailed(deResponse.status.value, "Read response body failed", content)
            }

            onSucceed(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSurveyResponse(url: String) {
        requestJob = companionSdkScope.launch {
            try {
                val response  = withTimeout(5000) {
                    remoteDataSource.fetchDataFromUriAsync(url, requestParams ?: mapOf())
                }
                onApiResponseReceived(response)
            } catch (e: Exception) {
                onFailed(0, "Survey Get Request Failed", null)
            }
        }
    }

    private fun postSurveyResponse(url: String, json: String) {
        requestJob = companionSdkScope.launch {
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

    private suspend fun onSucceed(result: String?) = withContext(Dispatchers.Main) {
        var apiAdResponse: SurveyAdsResponse? = null
        if (method == GET) {
            try {
                if (!TextUtils.isEmpty(result)) {
                    apiAdResponse = Gson().fromJson(result, SurveyAdsResponse::class.java)
                }
            } catch (e: Exception) {
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

    private suspend fun onFailed(errCode: Int, errMsg: String?, errBody: String?)  = withContext(Dispatchers.Main) {
        val errJson = if (!TextUtils.isEmpty(errBody)) JSONObject(errBody) else null
        if (method == POST && errCode == 400 && errJson?.optString("statusCode", "") == "alreadyresponded") {
            mxAdListener?.surveyAlreadyResponded()
            isAdLoading = false
        }
        else if (retryCount < retry) {
            retryCount++
            request()
        } else {
            mxAdListener?.onFailed(errCode)
            isAdLoading = false
        }
    }

    fun release() {
        mxAdListener = null
        requestJob?.cancel()
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