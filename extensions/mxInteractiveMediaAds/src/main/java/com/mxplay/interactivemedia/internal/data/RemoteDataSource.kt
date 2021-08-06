package com.mxplay.interactivemedia.internal.data

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import com.mxplay.interactivemedia.api.Configuration
import com.mxplay.interactivemedia.internal.api.IDataSource
import com.mxplay.mediaads.exo.JsonUtil
import com.mxplay.mediaads.exo.OmaUtil
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class RemoteDataSource(val configuration: Configuration) : IDataSource{

    private val CONN_TIMEOUT = 8000
    private val READ_TIMEOUT = 10000

    private var okHttpClient: OkHttpClient = normalClient(configuration.context, configuration.ioExecutor, configuration.debugModeEnabled)


    private class UserAgentInterceptor(val configuration: Configuration) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var originalRequest = chain.request()
            if (!TextUtils.isEmpty(configuration.userAgent)) {
                assert(configuration.userAgent != null)
                originalRequest = originalRequest.newBuilder()
                        .header("User-Agent", configuration.userAgent!!)
                        .build()
            }
            return chain.proceed(originalRequest)
        }
    }

    private class HttpLogger : HttpLoggingInterceptor.Logger {
        private val mMessage = StringBuffer()
        override fun log(message: String) {
            // 请求或者响应开始
            var message = message
            if (message.startsWith("--> GET") || message.startsWith("--> POST")) {
                mMessage.setLength(0)
            }
            // 以{}或者[]形式的说明是响应结果的json数据，需要进行格式化
            if (message.startsWith("{") && message.endsWith("}")
                    || message.startsWith("[") && message.endsWith("]")) {
                message = JsonUtil.formatJson(JsonUtil.decodeUnicode(message))
            }
            mMessage.append(message).append("\n")
            // 响应结束，打印整条日志
            if (message.startsWith("<-- END HTTP")) {
                Log.d(TAG, mMessage.toString())
            }
        }

        companion object {
            const val TAG = "HTTP"
        }
    }

    class CacheOverWriteInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val forceResponseHeaderValue = request.headers()[FORCE_RESPONSE_CACHE]
            if (forceResponseHeaderValue != null) {
                val cacheControl = CacheControl.Builder()
                        .maxAge(forceResponseHeaderValue.toInt(), TimeUnit.SECONDS) // 60 minutes cache
                        .build()
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", cacheControl.toString())
                        .build()
            }
            return response
        }

        companion object {
            /** Value should be in seconds  */
            const val FORCE_RESPONSE_CACHE = "Force-Cache-Response"
        }
    }

    private fun cache(context: Context): Cache {
        val cacheSize = 5 * 1024 * 1024L //5MB
        return Cache(context.cacheDir, cacheSize)
    }

    fun normalClient(context: Context, ioExecutor: ExecutorService,
                     debugModeEnabled: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
                .connectTimeout(CONN_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .cache(cache(context))
                .addInterceptor(UserAgentInterceptor(configuration))
                .addInterceptor(CacheOverWriteInterceptor())
                .dispatcher(Dispatcher(ioExecutor))
                .followRedirects(true)
                .retryOnConnectionFailure(true)
        if (debugModeEnabled) {
            val logInterceptor = HttpLoggingInterceptor(
                    HttpLogger())
            logInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addNetworkInterceptor(logInterceptor)
        }
        return builder.build()
    }

    @WorkerThread
    @Throws(IOException::class)
    fun fetchDataFromUri(url : String,  queries : Map<String, String> = mapOf(),
                         headers :  Map<String, String> = mapOf()): Response {
        val omaUtil = OmaUtil()
        val httpUrl = HttpUrl.parse(url)!!
        // append query

        val urlBuilder = httpUrl.newBuilder()

        for ((key, value) in queries) {
            if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                urlBuilder.addQueryParameter(key, value)
            }
        }

        // add headers
        val headerMap = configuration.urlStitchingService.commonHeaders()
        headerMap.putAll(headers)

        val requestBuilder: Request.Builder = Request.Builder().url(urlBuilder.build()).get().headers(Headers.of(headerMap))
        return okHttpClient.newCall(requestBuilder.build()).execute()
    }


    fun fetchOmSdkJs(): String? {
        val requestBuilder: Request.Builder = Request.Builder().url(configuration.trackersConfig.omSdkUrl)
        requestBuilder.headers(Headers.of(CacheOverWriteInterceptor.FORCE_RESPONSE_CACHE, "3600")) // 1 hour
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (response.isSuccessful){
            return response.body()?.string()
        }
        throw IOException("something went wrong fetching om sdk js")
    }

    @WorkerThread
    @Throws(IOException::class)
    fun trackEvent(url : String, urlStitching : (url : String) -> String, headersProvider : () -> MutableMap<String, String>): Boolean {
        val requestBuilder: Request.Builder = Request.Builder().url(urlStitching(url))
        requestBuilder.headers(Headers.of(headersProvider()))
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            return true
        } else throw IOException(" something went wrong in sending tracker")

    }

    @WorkerThread
    @Throws(IOException::class)
    fun fetchCompanionResource(url: String): Response {
        val requestBuilder: Request.Builder = Request.Builder().url(url)
        requestBuilder.headers(Headers.of(CacheOverWriteInterceptor.FORCE_RESPONSE_CACHE, "3600")) // 1 hour
        return okHttpClient.newCall(requestBuilder.build()).execute()
    }


}