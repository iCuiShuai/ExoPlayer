package com.mxplay.interactivemedia.internal.data.xml

import com.mxplay.interactivemedia.api.AdsManager
import com.mxplay.interactivemedia.internal.data.model.AdBreak
import com.mxplay.interactivemedia.internal.data.model.AdSource
import com.mxplay.interactivemedia.internal.data.model.VASTModel
import com.mxplay.interactivemedia.internal.data.model.VMAPModel
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import kotlin.jvm.Throws

object XmlParserHelper {


    /**
     * create a new [XmlPullParser]
     */
    fun createNewParser(): XmlPullParser {
        val xmlPullParserFactory = XmlPullParserFactory.newInstance()
        val xmlParser = xmlPullParserFactory.newPullParser()
        xmlParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        return xmlParser
    }


    @Throws(ProtocolException::class, XmlPullParserException::class, IOException::class)
    fun parseXmlResponse(mainPullParser: XmlPullParser, vastParser : Parser<VASTModel>, contentXml : String): VMAPModel? {
        mainPullParser.setInput(StringReader(contentXml))
        var event = mainPullParser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (mainPullParser.name) {
                        VMAPModel.VMAP -> {
                            val vmapParser = VMAPParser(mainPullParser, vastParser)
                            return vmapParser.parse()
                        }
                        VASTModel.VAST -> {
                            val vastData = vastParser.parse()
                            val vmapModel = VMAPModel()
                            vmapModel.addABreak(AdBreak().apply {
                                this.breakId = AdBreak.BreakIds.PRE_ROLL
                                this.breakType = AdBreak.BreakTypes.LINEAR
                                this.podIndex = AdsManager.PRE_ROLL_POD_INDEX
                                this.startTime = AdBreak.TimeOffsetTypes.START
                                this.adSource.add(AdSource("1", allowMultiple = true, followRedirect = true).apply {
                                    this.vastData = vastData
                                })
                                this.refreshAds()

                            })
                            return vmapModel
                        }
                        else -> {
                            skip(mainPullParser)
                        }
                    }
                }
            }
            event = mainPullParser.next()
        }
        return null
    }



    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(parser: XmlPullParser): String? {
        var result: String? = null
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            result = result?.trim() ?: ""
            parser.nextTag()
        }
        return result
    }

    /**
     * read the attribute value of the tag
     */
    fun readAttr(parser: XmlPullParser, attributeName: String): String? {
        return parser.getAttributeValue(null, attributeName)
    }

    /**
     * read the attribute value of the tag as boolean
     */
    fun readAttrAsBool(
            parser: XmlPullParser,
            attributeName: String
    ): Boolean? {
        return readAttr(
                parser,
                attributeName
        )?.toBoolean()
    }

    /**
     * read the attribute value of the tag as int
     */
    fun readAttrAsInt(
            parser: XmlPullParser,
            attributeName: String
    ): Int? {
        return readAttr(
                parser,
                attributeName
        )?.toInt()
    }

    /**
     * read the attribute value of the tag as long
     */
    fun readAttrAsLong(
            parser: XmlPullParser,
            attributeName: String
    ): Long? {
        return readAttr(
                parser,
                attributeName
        )?.toLong()
    }


    @Throws(IOException::class, XmlPullParserException::class)
    fun assertStartTag(parser: XmlPullParser, tag: String) {
        parser.require(XmlPullParser.START_TAG, null, tag)
    }


    @Throws(IOException::class, XmlPullParserException::class)
    fun assertEndTag(parser: XmlPullParser, tag: String) {
        parser.require(XmlPullParser.END_TAG, null, tag)
    }


    @Throws(XmlPullParserException::class, IOException::class)
    fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IOException("${parser.eventType} not of type start tag")
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }




}