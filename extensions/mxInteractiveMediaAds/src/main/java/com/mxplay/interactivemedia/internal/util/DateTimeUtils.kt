package com.mxplay.interactivemedia.internal.util

import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {


    fun getTimeInMillis(source: String): Long {
        var source = source
        var millis = 0
        //00:00:15.000
        val split = source.split("\\.".toRegex()).toTypedArray()
        if (split.size > 1) {
            source = split[0]
            millis = split[1].toInt()
        }
        val tokens = source.split(":".toRegex()).toTypedArray()
        val secondsToMs = if (tokens.size > 2) tokens[2].toInt() * 1000 else 0
        val minutesToMs = tokens[1].toInt() * 60000
        val hoursToMs = tokens[0].toInt() * 3600000
        return (millis + secondsToMs + minutesToMs + hoursToMs).toLong()
    }

    @JvmStatic
    fun formatTime(time: Float): String {
        return String.format(Locale.US, "%02d:%02d", (time % 3600).toInt() / 60, (time % 60).toInt())
    }


    @Throws(IOException::class)
    fun convertDateFormatToSeconds(timeToConvert: String?): Long {
        if (timeToConvert == null) {
            return -1
        }

        try {
            var pattern = "1970-01-01 00:00:00.000"

            val sdf: SimpleDateFormat = when {
                timeToConvert.matches("\\d+:\\d{2}:\\d{2}.\\d{3}".toRegex()) -> SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.US
                )
                timeToConvert.matches("\\d+:\\d{2}:\\d{2}.\\d{2}".toRegex()) -> {
                    pattern = "1970-01-01 00:00:00.00"
                    SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss.SS",
                            Locale.US
                    )
                }
                timeToConvert.matches("\\d+:\\d{2}:\\d{2}.\\d{1}".toRegex()) -> {
                    pattern = "1970-01-01 00:00:00.0"
                    SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss.S",
                            Locale.US
                    )
                }
                timeToConvert.matches("\\d+:\\d{2}:\\d{2}".toRegex()) -> SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.US
                )
                else -> throw IOException("Time format does not match expected.")
            }

            sdf.parse(pattern)?.let {
                val time = (it.time / 1000).toDouble()
                sdf.parse("1970-01-01 $timeToConvert")?.let { date ->
                    return (date.time / 1000 - time).toLong()
                }
            }
            return -1
        } catch (e: ParseException) {
            throw IOException(e.message)
        }
    }

    fun getTimeStampMacroValue(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

}