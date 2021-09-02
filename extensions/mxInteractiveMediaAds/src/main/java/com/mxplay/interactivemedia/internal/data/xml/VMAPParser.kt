
package com.mxplay.interactivemedia.internal.data.xml


import com.google.android.exoplayer2.util.Log
import com.mxplay.interactivemedia.api.AdError
import com.mxplay.interactivemedia.api.AdsManager
import com.mxplay.interactivemedia.internal.data.model.*
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.assertEndTag
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.assertStartTag
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttr
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttrAsBool
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readText
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.skip
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.jvm.Throws


class VMAPParser(private var pullParser: XmlPullParser, private var vastParser: Parser<VASTModel>) :
        Parser<VMAPModel> {

    @Throws(ProtocolException::class)
    override fun parse(): VMAPModel {
        try {
            /** make sure the vmap response starts with the correct tag **/
            assertStartTag(pullParser, VMAPModel.VMAP)

            val vmap = VMAPModel()
            vmap.version = readAttr(pullParser, VASTModel.VERSION)
            pullParser.next()

            val adBreaks = ArrayList<AdBreak>()
            var currentAdBreak: AdBreak? = null
            var event = pullParser.eventType

            /** loop until the correct closing tag is found **/
            while (pullParser.name != VMAPModel.VMAP) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (pullParser.name) {
                            VMAPModel.AD_BREAK -> {
                                /** ad break found **/
                                currentAdBreak = readAdBreak(pullParser)
                            }
                            AdBreak.AD_SOURCE-> {
                                /** ad source found **/
                                currentAdBreak?.adSource!!.add(readAdSource(pullParser))
                            }
                            AdBreak.TRACKING_EVENTS -> {
                                /** tracking events found **/
                                try {
                                    currentAdBreak?.trackingEvents =  EnumMap(EventName::class.java)
                                    readTrackingEvents(pullParser).forEach {
                                        currentAdBreak?.trackingEvents!!.getOrPut(it.key, { LinkedList<TrackingEvent>() }).add(it.value)
                                    }
                                } catch (e: Exception) {
                                    //TODO  Logger
                                }
                            }
                            else -> skip(pullParser)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (pullParser.name) {
                            VMAPModel.AD_BREAK -> {
                                /** once the ad break closing tag is found, add the object to the list **/
                                currentAdBreak?.let {
                                    adBreaks.add(it)
                                }
                            }
                        }
                    }
                }
                event = pullParser.next()
            }

            val breakIdMap = LinkedHashMap<String, AdBreak>()
            var podIndex = 1
            for (adBreak in adBreaks) {
                val existingAdBreak = breakIdMap[adBreak.breakId]
                if(existingAdBreak != null){
                    existingAdBreak.trackingEvents = existingAdBreak.trackingEvents ?: EnumMap(EventName::class.java)
                    existingAdBreak.trackingEvents!!.forEach{ entry ->
                        adBreak.trackingEvents?.get(entry.key)?.let { entry.value.addAll(it) }
                        adBreak.trackingEvents?.remove(entry.key)
                    }

                    adBreak.trackingEvents?.let {existingAdBreak.trackingEvents!!.putAll(it)}

                    existingAdBreak.adSource.addAll(adBreak.adSource)
                    existingAdBreak.adSource.forEach {
                        it.allowMultiple = false
                    }
                    existingAdBreak.refreshAds()
                    continue
                }
                breakIdMap.put(adBreak.breakId!!, adBreak)
                when (adBreak.startTime) {
                    AdBreak.TimeOffsetTypes.START -> {
                        adBreak.podIndex = AdsManager.PRE_ROLL_POD_INDEX
                    }
                    AdBreak.TimeOffsetTypes.END -> {
                        adBreak.podIndex = AdsManager.POST_ROLL_POD_INDEX
                    }
                    else -> {
                        adBreak.podIndex = podIndex
                        podIndex++
                    }
                }

                adBreak.refreshAds()
            }
            adBreaks.clear()
            adBreaks.addAll(breakIdMap.values)

            val message = "Empty vmap response"
            vmap.adBreaks = if (adBreaks.size > 0) adBreaks else throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VAST_EMPTY_RESPONSE, message))

            /** make sure the vmap response ends with the correct tag **/
            assertEndTag(pullParser, VMAPModel.VMAP)
            return vmap
        } catch (e: XmlPullParserException) {
            throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VMAP_MALFORMED_RESPONSE, e.message ?: "VMAP parsing failure"), e)
        } catch (e: IOException) {
            throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VMAP_MALFORMED_RESPONSE, e.message ?: "VMAP parsing failure"), e)
        }
    }

    /**
     * Read and build the [AdBreak] model
     */
    @Throws(IOException::class, XmlPullParserException::class, ProtocolException::class)
    private fun readAdBreak(parser: XmlPullParser): AdBreak {
        /** check start tag **/
        assertStartTag(parser, VMAPModel.AD_BREAK)

        val currentAdBreak = AdBreak()
        currentAdBreak.breakType = readAttr(parser, AdBreak.BREAK_TYPE)
        currentAdBreak.startTime = readAttr(parser, AdBreak.ATTR_TIME_OFFSET)
        currentAdBreak.breakId = readAttr(parser, AdBreak.BREAK_ID)
        currentAdBreak.repeatAfter = readAttr(parser, AdBreak.REPEAT_AFTER_ATTR)
        return currentAdBreak
    }

    /**
     * Read and build the list of [Tracking] model
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTrackingEvents(parser: XmlPullParser): Map<EventName, TrackingEvent> {
        /** check start tag **/
        assertStartTag(parser, AdBreak.TRACKING_EVENTS)
        parser.nextTag()

        val trackingEvents = EnumMap<EventName, TrackingEvent>(EventName::class.java)
        var event = pullParser.eventType

        /** loop until closing tag is found **/
        while (pullParser.name != AdBreak.TRACKING_EVENTS) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    AdBreak.TRACKING -> {
                        var eventName : String? = ""
                        try {
                            eventName  = readAttr(parser, EVENT_XML_ATTR)
                            val tracking = TrackingEvent(EventName.getType(eventName!!)!!, readText(parser)!!)
                            trackingEvents[tracking.name] = tracking
                        } catch (e: Exception) {
                            Log.e("VMAPParser","Event $eventName not found ",  e)
                        }
                    }
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(parser, EVENT_XML_ATTR)
        return trackingEvents
    }


    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAdSource(parser: XmlPullParser): AdSource {
        assertStartTag(parser, AdBreak.AD_SOURCE)
        val currentAdSource = AdSource(readAttr(parser, AdSource.ID) ?: System.currentTimeMillis().toString(), readAttrAsBool(parser, AdSource.MULTIPLE_ADS_ATTR) ?: true, readAttrAsBool(parser, AdSource.FOLLOW_REDIRECT_ATTR) ?: true)

        pullParser.nextTag()
        var event = pullParser.eventType

        while (pullParser.name != AdBreak.AD_SOURCE) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    AdSource.AD_TAG_URI_XML_TAG_V1 -> {
                        /** ad tag found **/
                        currentAdSource.adTagUri =
                            readAdTagURI(pullParser, AdSource.AD_TAG_URI_XML_TAG_V1)
                    }
                    AdSource.AD_TAG_URI_XML_TAG_V2 -> {
                        /** ad tag found **/
                        currentAdSource.adTagUri =
                            readAdTagURI(pullParser, AdSource.AD_TAG_URI_XML_TAG_V2)
                    }
                    VMAPModel.VAST_DATA_XML_TAG_V1, VMAPModel.VAST_DATA_XML_TAG_V2 -> {
                        /** vast found, delegate it to the vast parser **/
                        pullParser.nextTag()
                        currentAdSource.vastData = vastParser.parse()
                    }
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        assertEndTag(parser, AdBreak.AD_SOURCE)
        return currentAdSource
    }


    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAdTagURI(parser: XmlPullParser, tag: String): String {
        var uri : String ? = null
        assertStartTag(parser, tag)
        uri = readText(parser)
        /** check end tag **/
        assertEndTag(parser, tag)
        return uri!!
    }
}
