package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.api.AdDisplayContainer
import com.mxplay.interactivemedia.internal.data.model.CompanionAdData
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class CompanionSelectorTest : TestCase(){

    @Test
    fun pickBestCompanionsSuccess(){
        val adDisplayContainer = mock<AdDisplayContainer>()
        whenever(adDisplayContainer.getCompanionAdSlots()).thenReturn(listOf(
                CompanionAdSlotImpl().apply { setSize(300, 250) }
        ))
        val listOfCompanions = listOf(
                CompanionAdData().apply { _width = 300; _height = 250; resourceType = CompanionAdData.TAG_STATIC_RESOURCE },
                CompanionAdData().apply { _width = 300; _height = 300; resourceType = CompanionAdData.TAG_STATIC_RESOURCE }
        )
        val companionSelector = CompanionSelector()
        val companionSelected = companionSelector.pickBestCompanions(adDisplayContainer, listOfCompanions)
        assertTrue(companionSelected!!.isNotEmpty())
    }


    @Test
    fun pickBestCompanionsFail(){
        val adDisplayContainer = mock<AdDisplayContainer>()
        whenever(adDisplayContainer.getCompanionAdSlots()).thenReturn(listOf(
                CompanionAdSlotImpl().apply { setSize(200, 250) }
        ))
        val listOfCompanions = listOf(
                CompanionAdData().apply { _width = 300; _height = 250; resourceType = CompanionAdData.TAG_STATIC_RESOURCE },
                CompanionAdData().apply { _width = 300; _height = 300; resourceType = CompanionAdData.TAG_STATIC_RESOURCE }
        )
        val companionSelector = CompanionSelector()
        val companionSelected = companionSelector.pickBestCompanions(adDisplayContainer, listOfCompanions)
        assert(companionSelected != null && companionSelected.isEmpty())
    }


}