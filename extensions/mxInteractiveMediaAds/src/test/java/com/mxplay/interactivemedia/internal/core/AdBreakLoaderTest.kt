package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.MainCoroutineRule
import com.mxplay.interactivemedia.api.OmSdkSettings
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.interactivemedia.internal.data.model.AdBreak
import com.mxplay.interactivemedia.internal.data.model.AdTagUriHost
import com.mxplay.interactivemedia.internal.data.model.VASTModel
import com.mxplay.interactivemedia.util.TestConfigData
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdBreakLoaderTest : TestCase() {
    @ExperimentalCoroutinesApi
    private val testDispatcher = TestCoroutineDispatcher()
    @ExperimentalCoroutinesApi
    private val ioOpsScope: CoroutineScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Test
    fun testLoadAdBreak() {
        val mockDataSource = mock<RemoteDataSource>()
        val response = TestConfigData().getOkHttpSuccessResponse("vast.xml")
        whenever(mockDataSource.fetchDataFromUri(any(), any(), any())).thenReturn(response)
        val mockOmSdkSettings = mock<OmSdkSettings>()
        val mockCallback = mock<AdBreakLoader.AdBreakLoadingCallback>()
        val argumentCaptor = argumentCaptor<AdBreak>()
        doNothing().whenever(mockCallback).onAdBreakLoaded(argumentCaptor.capture())
        val adBreakLoader = AdBreakLoader(ioOpsScope, mockDataSource, mockOmSdkSettings)
        val adBreak = mock<AdBreak>()
        whenever(adBreak.totalAdsCount).thenReturn(3)
        val uriHost = mock<AdTagUriHost>()
        val vastArgumentCaptor = argumentCaptor<VASTModel>()
        doNothing().whenever(uriHost).handleAdTagUriResult(vastArgumentCaptor.capture())
        whenever(uriHost.getPendingAdTagUri()).thenReturn("http://mxp-server.in")
        whenever(adBreak.getPendingAdTagUriHost()).thenReturn(uriHost)
        adBreakLoader.loadAdBreak(adBreak, 2000L, mockCallback)
        assertEquals(adBreak, argumentCaptor.lastValue)
        assertEquals(1, vastArgumentCaptor.lastValue.ads!!.size)
    }


    @Test
    fun testLoadAdBreakEmptyVastError() {
        val mockDataSource = mock<RemoteDataSource>()
        val response = TestConfigData().getOkHttpEmptyVastResponse("vast.xml")
        whenever(mockDataSource.fetchDataFromUri(any(), any(), any())).thenReturn(response)
        val mockOmSdkSettings = mock<OmSdkSettings>()
        val mockCallback = mock<AdBreakLoader.AdBreakLoadingCallback>()
        val adBreakLoader = AdBreakLoader(ioOpsScope, mockDataSource, mockOmSdkSettings)
        val adBreak = mock<AdBreak>()
        val uriHost = mock<AdTagUriHost>()
        whenever(uriHost.getPendingAdTagUri()).thenReturn("http://mxp-server.in")
        whenever(adBreak.getPendingAdTagUriHost()).thenReturn(uriHost)
        adBreakLoader.loadAdBreak(adBreak, 2000L, mockCallback)
        verify(mockCallback).onAdBreakFetchError(any(), any())
    }
}