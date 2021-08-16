
package com.mxplay.interactivemedia.internal.data.xml

import androidx.annotation.WorkerThread
import java.io.IOException
import kotlin.jvm.Throws

interface Parser<T> {


    /**
     * Parse and return [T]
     *
     * called on background thread
     * @throws IOException
     */
    @WorkerThread
    @Throws(ProtocolException::class)
    fun parse(): T?
}
