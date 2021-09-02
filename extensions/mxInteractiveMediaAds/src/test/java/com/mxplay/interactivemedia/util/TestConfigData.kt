package com.mxplay.interactivemedia.util

import com.mxplay.interactivemedia.internal.util.Util
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.InputStream

class TestConfigData {


    fun stringFromResourceFile(filename: String): String {
        val inputStream: InputStream? = null
        return try {
            val inputStream = this.javaClass.classLoader.getResourceAsStream(filename)
            return Util.convertStreamToString(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }finally {
            try {
                inputStream?.close()
            }catch (e1 : Exception){}
        }

    }

    fun getOkHttpResponse(filename: String) : Response{
        return Response.Builder()
                .request(Request.Builder().get().url("http://mxp-server.in").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
            .body(ResponseBody.create(null, TestConfigData().stringFromResourceFile(filename)))
            .build()
    }


}