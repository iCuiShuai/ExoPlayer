package com.mxplay.interactivemedia.internal.data.xml

import com.mxplay.interactivemedia.util.TestConfigData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest= Config.NONE)
internal class VMAPParserTest{

    private var xmlPullParser : XmlPullParser? = null
    private var vastProcessor : VastXmlParser? = null


    @Before
    fun doSetup(){
        xmlPullParser = createNewParser()
        vastProcessor = VastXmlParser(xmlPullParser!!)
    }

    private fun createNewParser(): XmlPullParser {
        val xmlPullParserFactory = XmlPullParserFactory.newInstance()
        val xmlParser = xmlPullParserFactory.newPullParser()
        xmlParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        return xmlParser
    }

    @Test
    fun parsingValidationTest(){
        val vmap  = XmlParserHelper.parseXmlResponse(xmlPullParser!!, vastProcessor!!, TestConfigData().stringFromResourceFile("any-vast-vmap.xml"))
        assertNotNull(vmap)
    }

    @Test
    fun parseInlineResponseVMap(){
        val vmap  = XmlParserHelper.parseXmlResponse(xmlPullParser!!, vastProcessor!!, TestConfigData().stringFromResourceFile("inline-vmap.xml"))
        assertTrue(vmap!!.adBreaks.size == 2)
        assertTrue(vmap.adBreaks.size == 2 && vmap.adBreaks[0].totalAdsCount == 1)
        assertTrue(vmap.adBreaks.size == 2 && vmap.adBreaks[1].totalAdsCount == 4)
        assertEquals(17, vmap.adBreaks[1].adSource[0].getAds()[0].provideTrackingEvent()!!.size)
    }


    @Test
    fun parseWrapperSingleAdsBreaks(){
        val vmap  = XmlParserHelper.parseXmlResponse(xmlPullParser!!, vastProcessor!!, TestConfigData().stringFromResourceFile("single-ads.xml"))
        assertTrue(vmap!!.adBreaks.size == 3 && vmap.adBreaks[0].breakId == "preroll" && vmap.adBreaks[1].breakId == "midroll-1" && vmap.adBreaks[2].breakId == "postroll")
    }


    @Test
    fun parsePodAdsBreaks(){
        val vmap  = XmlParserHelper.parseXmlResponse(xmlPullParser!!, vastProcessor!!, TestConfigData().stringFromResourceFile("1-pre-roll-3-midroll-1-postroll.xml"))
        assertTrue(vmap!!.adBreaks.size == 3 && vmap.adBreaks[1].adSource.size == 3)
    }

}