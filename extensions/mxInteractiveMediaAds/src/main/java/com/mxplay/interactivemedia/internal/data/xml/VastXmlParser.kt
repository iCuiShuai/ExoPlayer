package com.mxplay.interactivemedia.internal.data.xml

import android.webkit.URLUtil
import com.google.android.exoplayer2.util.Log
import com.mxplay.interactivemedia.api.Ad
import com.mxplay.interactivemedia.api.AdError
import com.mxplay.interactivemedia.internal.data.model.*
import com.mxplay.interactivemedia.internal.data.model.MediaFile.Companion.ID_XML_ATTR
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.assertEndTag
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.assertStartTag
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttr
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttrAsBool
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttrAsInt
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readAttrAsLong
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.readText
import com.mxplay.interactivemedia.internal.data.xml.XmlParserHelper.skip
import com.mxplay.interactivemedia.internal.util.DateTimeUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 *
 *
100 XML parsing error.
101 VAST schema validation error.
102 VAST version of response not supported.
200 Trafficking error. Video player received an Ad type that it was not expecting and/or
cannot display.
201 Video player expecting different linearity.
202 Video player expecting different duration.
203 Video player expecting different size.
204 Ad category was required but not provided.
300 General Wrapper error.
301 Timeout of VAST URI provided in Wrapper element, or of VAST URI provided in a
subsequent Wrapper element. (URI was either unavailable or reached a timeout as
defined by the video player.)
302 Wrapper limit reached, as defined by the video player. Too many Wrapper
responses have been received with no InLine response.
303 No VAST response after one or more Wrappers.
304 InLine response returned ad unit that failed to result in ad display within defined time
limit.
400 General Linear error. Video player is unable to display the Linear Ad.
401 File not found. Unable to find Linear/MediaFile from URI.
402 Timeout of MediaFile URI.
403 Couldn’t find MediaFile that is supported by this video player, based on the
attributes of the MediaFile element.
405 Problem displaying MediaFile. Video player found a MediaFile with supported type
but couldn’t display it. MediaFile may include: unsupported codecs, different MIME
type than MediaFile@type, unsupported delivery method, etc.
406 Mezzanine was required but not provided. Ad not served.
407 Mezzanine is in the process of being downloaded for the first time. Download may
take several hours. Ad will not be served until mezzanine is downloaded and
transcoded.
408 Conditional ad rejected.
409 Interactive unit in the InteractiveCreativeFile node was not executed.
410 Verification unit in the Verification node was not executed.
411 Mezzanine was provided as required, but file did not meet required specification. Ad
not served.
500 General NonLinearAds error.
501 Unable to display NonLinear Ad because creative dimensions do not align with
creative display area (i.e. creative dimension too large).
502 Unable to fetch NonLinearAds/NonLinear resource.
503 Couldn’t find NonLinear resource with supported type.

600 General CompanionAds error.
601 Unable to display Companion because creative dimensions do not fit within
Companion display area (i.e., no available space).
602 Unable to display required Companion.
603 Unable to fetch CompanionAds/Companion resource.
604 Couldn’t find Companion resource with supported type.
900 Undefined Error.
901 General VPAID error
 *
 *
 */
class VastXmlParser(private val pullParser: XmlPullParser) : Parser<VASTModel> {

    companion object{
        const val TAG = "VastXmlParser"
    }


    @Throws(ProtocolException::class)
    override fun parse(): VASTModel {
        try {
            /** make sure the vast response starts with the correct tag **/
            assertStartTag(pullParser, VASTModel.VAST)

            val vastData = VASTModel()
            vastData.version = readAttr(pullParser, VASTModel.VERSION)

            pullParser.next()

            var currentAd: AdData? = null
            val ads = ArrayList<AdData>()
            var event = pullParser.eventType

            /** loop until the closing tag is found **/
            while (pullParser.name != VASTModel.VAST) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (pullParser.name) {
                            VASTModel.AD_XML_TAG -> {
                                /** ad tag found **/
                                currentAd = readAd(pullParser)
                            }
                            VASTModel.ERROR_XML_ATTR -> {
                                /** error tag found **/
                                val errorUrl = readText(pullParser)
                                val errorUrlList =
                                        vastData.errorUrls?.toMutableList() ?: ArrayList()
                                errorUrl?.let { errorUrlList.add(it) }
                                vastData.errorUrls = errorUrlList
                            }
                            AdData.INLINE_XML_TAG -> {
                                /** inline tag found **/
                                currentAd  = readInLine(pullParser, currentAd!!)
                            }
                            AdData.WRAPPER_XML_TAG -> {
                                currentAd  = readWrapper(pullParser, currentAd!!)
                            }

                            else -> skip(pullParser)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (pullParser.name) {
                            VASTModel.AD_XML_TAG -> {
                                val message = "Invalid vast response"
                                if (currentAd != null && !(currentAd is AdWrapper) && !(currentAd is AdInline)) throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VAST_MALFORMED_RESPONSE, message))
                                currentAd ?.let {
                                    ads.add(it)
                                }
                            }
                        }
                    }
                }
                event = pullParser.next()
            }

            /** make sure the vast response ends with the correct tag **/
            assertEndTag(pullParser, VASTModel.VAST)

            vastData.ads = ads
            return vastData
        } catch (e : ProtocolException){
            throw e
        }
        catch (e: Exception) {
            throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.VAST_MALFORMED_RESPONSE, e.message ?: "Vast parsing failure"), e)
        }
    }

    /**
     * Read and build [Ad] model
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAd(xmlParser: XmlPullParser): AdData {
        /** check start tag **/
        assertStartTag(xmlParser, VASTModel.AD_XML_TAG)
        return AdData(readAttr(xmlParser, AdData.ID) ?: UUID.randomUUID().toString())
    }

    /**
     * Read and build [AdInline] model
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readInLine(pullParser: XmlPullParser, parent: AdData): AdInline {
        /** check start tag **/
        assertStartTag(pullParser, AdData.INLINE_XML_TAG)
        pullParser.nextTag()

        val inLine = AdInline(parent.id)
        inLine.sequence = readAttrAsInt(pullParser, AdData.SEQUENCE_XML_ATTR) ?: 1
        var event = pullParser.eventType

        /** loop until close tag for inline is found **/
        while (pullParser.name != AdData.INLINE_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    AdInline.AD_SYSTEM_XML_TAG -> inLine.adSystem = readText(pullParser)
                    AdInline.AD_TITLE_XML_TAG -> inLine.adTitle = readText(pullParser)
                    AdInline.DESCRIPTION_XML_TAG -> inLine.adDescription = readText(pullParser)
                    AdInline.ADVERTISER_XML_TAG -> inLine.advertiser = readText(pullParser)
                    AdData.ERROR_XML_TAG -> {
                        inLine.errorUrls = (inLine.errorUrls ?: LinkedList()).apply {
                            this.add(ImpressionEvent(EventName.ERROR, readText(pullParser)
                                    ?: ""))
                        }
                    }
                    AdData.IMPRESSION_XML_TAG -> {
                        inLine.impressionUrls = (inLine.impressionUrls ?: LinkedList()).apply {
                            this.add(ImpressionEvent(EventName.IMPRESSION, readText(pullParser)
                                    ?: ""))
                        }
                    }
                    AdData.ADVERIFICATIONS ->{
                        inLine.adverifications = readAdVerifications(pullParser)
                    }
                    AdData.CREATIVES_XML_TAG -> inLine.creatives = readCreatives(pullParser)
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(pullParser, AdData.INLINE_XML_TAG)
        return inLine
    }


    @Throws(IOException::class, XmlPullParserException::class)
    private fun readWrapper(pullParser: XmlPullParser, parent: AdData): AdWrapper {
        /** check start tag **/
        assertStartTag(pullParser, AdData.WRAPPER_XML_TAG)
        pullParser.nextTag()

        val adWrapper = AdWrapper(parent.id, readAttrAsBool(pullParser, AdSource.MULTIPLE_ADS_ATTR) ?: false, readAttrAsBool(pullParser, AdWrapper.FAlLBACK_ON_NO_AD) ?: true)
        var event = pullParser.eventType

        /** loop until close tag for inline is found **/
        while (pullParser.name != AdData.WRAPPER_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    AdWrapper.AD_TAG_URI -> adWrapper.adTagUri = readText(pullParser)
                    AdData.ERROR_XML_TAG -> {
                        adWrapper.errorUrls = (adWrapper.errorUrls ?: LinkedList()).apply {
                            this.add(ErrorEvent(EventName.ERROR, readText(pullParser) ?: ""))
                        }
                    }
                    AdData.IMPRESSION_XML_TAG -> {
                        adWrapper.impressionUrls = (adWrapper.impressionUrls ?: LinkedList()).apply {
                            this.add(ImpressionEvent(EventName.IMPRESSION, readText(pullParser) ?: ""))
                        }
                    }
                    AdData.ADVERIFICATIONS ->{
                        adWrapper.adverifications = readAdVerifications(pullParser)
                    }

                    AdData.CREATIVES_XML_TAG -> adWrapper.creatives = readCreatives(pullParser)
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(pullParser, AdData.WRAPPER_XML_TAG)
        return adWrapper
    }


    /**
     * Read and build the list of [Creative] models
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readCreatives(xmlParser: XmlPullParser): List<Creative> {
        /** check start tag **/
        assertStartTag(xmlParser, AdData.CREATIVES_XML_TAG)
        xmlParser.nextTag()

        val creatives = ArrayList<Creative>()
        var event = xmlParser.eventType
        while (xmlParser.name != AdData.CREATIVES_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (xmlParser.name) {
                    Creative.CREATIVE_XML_TAG -> {
                        creatives.add(readCreative(xmlParser)!!)
                    }
                    else -> skip(xmlParser)
                }
            }
            event = xmlParser.next()
        }

        /** check end tag **/
        assertEndTag(xmlParser, AdData.CREATIVES_XML_TAG)
        return creatives
    }



    @Throws(IOException::class, XmlPullParserException::class, ProtocolException::class)
    private fun readCreative(xmlParser: XmlPullParser): Creative? {
        assertStartTag(xmlParser, Creative.CREATIVE_XML_TAG)
        val id = readAttr(xmlParser, Creative.ID_XML_ATTR) ?: ""
        val sequence = readAttr(xmlParser, Creative.SEQUENCE_XML_ATTR) ?: "1"
        xmlParser.nextTag()
        var event = xmlParser.eventType
        while (xmlParser.name != Creative.CREATIVE_XML_TAG){
            if (event == XmlPullParser.START_TAG) {
                when (xmlParser.name) {
                    Creative.LINEAR_XML_TAG -> {
                        val linearCreative = LinearCreative(id).apply { this.sequence = sequence }
                        readLinearCreative(xmlParser, linearCreative)
                        return linearCreative
                    }
                    Creative.TAG_COMPANIONS_ADS -> {
                        return readCompanionCreative(xmlParser, id, sequence)
                    }
                    else -> {
                        skip(xmlParser)
                    }
                }
            }
            event = xmlParser.next()

        }

        return null
    }

    @Throws(IOException::class, XmlPullParserException::class, ProtocolException::class)
    private fun readLinearCreative(xmlParser: XmlPullParser, linearCreative: LinearCreative) {
        /** check start tag **/
        assertStartTag(xmlParser, Creative.LINEAR_XML_TAG)

        linearCreative.skipOffset = readAttr(xmlParser, LinearCreative.SKIP_OFFSET_XML_TAG)

        xmlParser.nextTag()

        var event = xmlParser.eventType

        /** loop until closing tag is found **/
        while (xmlParser.name != Creative.LINEAR_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (xmlParser.name) {
                    LinearCreative.DURATION_XML_TAG -> {
                        linearCreative.duration = readText(xmlParser)
                        linearCreative.durationInSeconds = DateTimeUtils.convertDateFormatToSeconds(linearCreative.duration)
                    }
                    TrackingEvent.TRACKING_EVENTS_XML_TAG -> linearCreative.trackingEvents = readTrackingEvents(xmlParser)
                    LinearCreative.MEDIA_FILES_XML_TAG -> linearCreative.mediaFiles = readMediaFiles(xmlParser)
                    LinearCreative.VIDEO_CLICKS_XML_TAG -> linearCreative.videoClicks = readVideoClicks(xmlParser)
                    else -> skip(xmlParser)
                }
            }
            event = xmlParser.next()
        }

        /** check end tag **/
        assertEndTag(xmlParser, Creative.LINEAR_XML_TAG)

        linearCreative.skipOffset?.let {
            if (it.contains("%")) {
                // skip offset of type n%
                val percentage = it.replace("%", "").toDouble()
                linearCreative.skipOffsetInSeconds = ((linearCreative.durationInSeconds * percentage) / 100).toLong()
            } else {
                // skip offset of type HH:MM:SS.mmm
                linearCreative.skipOffsetInSeconds = DateTimeUtils.convertDateFormatToSeconds(it)
            }
        }

    }

    @Throws(IOException::class, XmlPullParserException::class, ProtocolException::class)
    fun readCompanionCreative(xmlParser: XmlPullParser,  id : String, sequence : String): CompanionCreative {
        assertStartTag(xmlParser, Creative.TAG_COMPANIONS_ADS)
        val companionCreative =  CompanionCreative(id).apply { this.sequence = sequence  }
        xmlParser.nextTag()
        var event = xmlParser.eventType
        val companionAds = LinkedList<CompanionAdData>()
        while (xmlParser.name != Creative.TAG_COMPANIONS_ADS){
            if (event == XmlPullParser.START_TAG){
                when(xmlParser.name){
                    CompanionCreative.TAG_COMPANION_AD -> {
                        try {
                            companionAds.add(readCompanionAd(xmlParser))
                        } catch (e: Exception) {
                           throw ProtocolException(AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.COMPANION_GENERAL_ERROR, e.message ?: "Companion parsing failure"), e)
                        }
                    }
                    else -> {
                        skip(xmlParser)
                    }

                }
            }
            event = xmlParser.next()
        }

        assertEndTag(xmlParser, Creative.TAG_COMPANIONS_ADS)
        companionCreative.companionAds = companionAds
        return companionCreative
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readCompanionAd(pullParser: XmlPullParser) : CompanionAdData {
        assertStartTag(pullParser, CompanionCreative.TAG_COMPANION_AD)
        var event = pullParser.eventType
        val companionAdData = CompanionAdData()
        companionAdData.id = readAttr(pullParser, CompanionCreative.ID)
        companionAdData._width = readAttrAsInt(pullParser, CompanionCreative.ATTR_WIDTH)!!
        companionAdData._height = readAttrAsInt(pullParser, CompanionCreative.ATTR_HEIGHT)!!
        pullParser.nextTag()
        while (pullParser.name != CompanionCreative.TAG_COMPANION_AD){
            if (event == XmlPullParser.START_TAG){
                when(pullParser.name){
                    CompanionAdData.TAG_STATIC_RESOURCE -> {
                        val staticResource = CompanionAdData.StaticResource()
                        staticResource.creativeType = readAttr(pullParser, CompanionCreative.ATTR_CREATIVE_TYPE)!!
                        staticResource.url = readText(pullParser)
                        companionAdData.resourceType = CompanionAdData.TAG_STATIC_RESOURCE
                        companionAdData.staticResource = staticResource
                    }
                    CompanionAdData.TAG_HTML_RESOURCE -> {
                        val htmlResource = CompanionAdData.HTMLResource()
                        htmlResource.url = readText(pullParser)
                        companionAdData.resourceType = CompanionAdData.TAG_HTML_RESOURCE
                        companionAdData.htmlResource = htmlResource
                    }
                    TrackingEvent.TRACKING_EVENTS_XML_TAG -> {
                        companionAdData.trackingEvents = readTrackingEvents(pullParser)
                    }
                    CompanionCreative.TAG_COMPANION_CLICK_THROUGH -> {
                        companionAdData.clickUrl = readText(pullParser)!!
                    }
                    else -> {
                        skip(pullParser)
                    }
                }
            }
            event = pullParser.next()
        }

        assertEndTag(pullParser, CompanionCreative.TAG_COMPANION_AD)
        return companionAdData
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAdVerifications(parser: XmlPullParser) : List<AdVerification> {
        assertStartTag(parser, AdData.ADVERIFICATIONS)
        parser.nextTag()
        var event = parser.eventType
        val adVerifications = mutableListOf<AdVerification>()
        while (parser.name !=  AdData.ADVERIFICATIONS){
            if (event == XmlPullParser.START_TAG){
                when(parser.name){
                   AdVerification.TAG_VERIFICATION -> {
                       try {
                           adVerifications.add(readVerification(parser))
                       } catch (e: Exception) {
                           Log.e(TAG, " error parsing verification tag ", e)
                       }
                   } else -> skip(parser)
                }
            }
            event = parser.next()
        }
        assertEndTag(parser, AdData.ADVERIFICATIONS)
        return adVerifications
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readVerification(parser: XmlPullParser) : AdVerification {
        assertStartTag(parser, AdVerification.TAG_VERIFICATION)
        val verification = AdVerification()
        verification.vendorKey = readAttr(parser, AdVerification.ATTR_VENDOR)!!
        parser.nextTag()
        var event = parser.eventType

        while (parser.name !=  AdVerification.TAG_VERIFICATION){
            if (event == XmlPullParser.START_TAG){
                when(parser.name){
                    AdVerification.TAG_JAVASCRIPT_RESOURCE -> {
                        verification.url = readText(parser)
                    }
                    AdVerification.TAG_VERIFICATION_PARAMETERS -> {
                        verification.params = readText(parser)
                    }

                    TrackingEvent.TRACKING_EVENTS_XML_TAG -> {
                        verification.trackingEvents = readTrackingEvents(parser)
                    }
                    else -> skip(parser)

                }
            }
            event = parser.next()
        }

        assertEndTag(parser, AdVerification.TAG_VERIFICATION)
        return verification
    }

    /**
     * Read and build the list of trackers model
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTrackingEvents(parser: XmlPullParser): MutableMap<EventName, TrackingEvent> {
        /** check start tag **/
        assertStartTag(parser, TrackingEvent.TRACKING_EVENTS_XML_TAG)
        parser.nextTag()

        val trackingEvents = EnumMap<EventName, TrackingEvent>(EventName::class.java)
        var event = pullParser.eventType

        /** loop until closing tag is found **/
        while (pullParser.name != TrackingEvent.TRACKING_EVENTS_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    TrackingEvent.TRACKING_XML_TAG -> {
                        val tracking = TrackingEvent(EventName.getType(readAttr(parser, EVENT_XML_ATTR)!!)!!, readText(parser)!!)
                        trackingEvents[tracking.name] = tracking
                    }
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(parser, TrackingEvent.TRACKING_EVENTS_XML_TAG)
        return trackingEvents
    }

    /**
     * Read and build the list of [MediaFile] model
     */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readMediaFiles(parser: XmlPullParser): MutableList<MediaFile> {
        /** check start tag **/
        assertStartTag(parser, LinearCreative.MEDIA_FILES_XML_TAG)
        parser.nextTag()

        val mediaFiles = ArrayList<MediaFile>()
        var event = pullParser.eventType

        /** loop until closing tag is found **/
        while (pullParser.name != LinearCreative.MEDIA_FILES_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when (pullParser.name) {
                    MediaFile.MEDIA_FILE_XML_TAG -> {
                        val mediaFile = MediaFile()
                        mediaFile.id = readAttr(pullParser, MediaFile.ID_XML_ATTR)
                        mediaFile.delivery = readAttr(pullParser, MediaFile.DELIVERY_XML_ATTR)!!
                        mediaFile.width = readAttrAsInt(pullParser, MediaFile.WIDTH_XML_ATTR)
                        mediaFile.height = readAttrAsInt(pullParser, MediaFile.HEIGHT_XML_ATTR)
                        mediaFile.type = readAttr(pullParser, MediaFile.TYPE_XML_ATTR)
                        mediaFile.bitrate = readAttrAsLong(pullParser, MediaFile.BITRATE_XML_ATTR) ?: readAttrAsLong(pullParser, MediaFile.MIN_BITRATE_XML_ATTR)
                        mediaFile.scalable = readAttrAsBool(pullParser, MediaFile.SCALABLE_XML_ATTR)
                        mediaFile.maintainAspectRatio =
                                readAttrAsBool(pullParser, MediaFile.ASPECT_RATIO_XML_ATTR)
                        mediaFile.url = assertValidMediaFile(readText(pullParser)!!)
                        mediaFiles.add(mediaFile)
                    }
                    else -> skip(pullParser)
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(parser, LinearCreative.MEDIA_FILES_XML_TAG)
        return mediaFiles
    }

    private fun assertValidMediaFile(url : String): String {
        if (!URLUtil.isNetworkUrl(url) && !File(url).exists()){
            throw IOException("Invalid media url $url")
        }
        return url;
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readVideoClicks(parser: XmlPullParser): VideoClicks {
        /** check start tag **/
        assertStartTag(parser, LinearCreative.VIDEO_CLICKS_XML_TAG)
        parser.nextTag()

        val videoClicks = VideoClicks()
        var event = pullParser.eventType

        /** loop until closing tag is found **/
        while (pullParser.name != LinearCreative.VIDEO_CLICKS_XML_TAG) {
            if (event == XmlPullParser.START_TAG) {
                when(pullParser.name){
                    EventName.VIDEO_CLICK.name -> {
                        videoClicks.clickTracking =  ClickEvent(EventName.VIDEO_CLICK, readAttr(pullParser, ID_XML_ATTR)!!, readText(pullParser)!!)
                    }
                    VideoClicks.TAG_CLICK_THROUGH -> {
                        videoClicks.clickThrough = readText(pullParser)!!
                    }
                }
            }
            event = pullParser.next()
        }

        /** check end tag **/
        assertEndTag(parser, LinearCreative.VIDEO_CLICKS_XML_TAG)
        return videoClicks
    }


}