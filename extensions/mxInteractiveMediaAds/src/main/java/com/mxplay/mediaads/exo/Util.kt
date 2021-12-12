package com.mxplay.mediaads.exo

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*

object Util {
    val UTF_8 = Charset.forName("UTF-8")

    /**
     * Reads data from the specified opened [DataSource] until it ends, and returns a byte array
     * containing the read data.
     *
     * @param dataSource The source from which to read.
     * @return The concatenation of all read data.
     * @throws IOException If an error occurs reading from the source.
     */
    @Throws(IOException::class)
    fun readToEnd(dataSource: DataSource): ByteArray {
        var data = ByteArray(1024)
        var position = 0
        var bytesRead = 0
        while (bytesRead != C.RESULT_END_OF_INPUT) {
            if (position == data.size) {
                data = Arrays.copyOf(data, data.size * 2)
            }
            bytesRead = dataSource.read(data, position, data.size - position)
            if (bytesRead != C.RESULT_END_OF_INPUT) {
                position += bytesRead
            }
        }
        return Arrays.copyOf(data, position)
    }

    /**
     * Returns a new [String] constructed by decoding UTF-8 encoded bytes.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @return The string.
     */
    fun fromUtf8Bytes(bytes: ByteArray?): String {
        return String(bytes!!, UTF_8)
    }

    /**
     * Returns a new [String] constructed by decoding UTF-8 encoded bytes in a subarray.
     *
     * @param bytes The UTF-8 encoded bytes to decode.
     * @param offset The index of the first byte to decode.
     * @param length The number of bytes to decode.
     * @return The string.
     */
    fun fromUtf8Bytes(bytes: ByteArray?, offset: Int, length: Int): String {
        return String(bytes!!, offset, length, UTF_8)
    }

    /**
     * Formats a string using [Locale.US].
     *
     * @see String.format
     */
    fun formatInvariant(format: String?, vararg args: Any?): String {
        return String.format(Locale.US, format!!, *args)
    }


    @Throws(Exception::class)
    fun convertStreamToString(`is`: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(`is`, Charset.defaultCharset()))
        val sb = StringBuilder()
        var line: String? = null
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString()
    }

}