package com.mxplay.adloader.nativeCompanion

import com.google.gson.Gson
import com.mxplay.adloader.nativeCompanion.expandable.data.BigBannerTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.EndCardTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.TableViewTemplateData
import com.mxplay.adloader.nativeCompanion.expandable.data.TemplateData
import org.json.JSONObject

const val ID_KEY = "templateId"
const val KEY_ADS = "ads"
const val KEY_IMAGE = "image"

const val ID_UNI_IMAGE_TEMPLATE = "UNI_IMAGE_TEMPLATE"
const val ID_GRID_IMAGE_TEMPLATE = "GRID_IMAGE_TEMPLATE"
const val ID_CAROUSEL_IMAGE_TEMPLATE = "CAROUSEL_IMAGE_TEMPLATE"
const val ID_END_CARD_IMAGE_TEMPLATE = "END_CARD_IMAGE_TEMPLATE"

const val DEFAULT_COMPANION_NONE = "none"

const val ITEM_TYPE = "detailed"

fun parse(data : JSONObject) : TemplateData{
    val gson  = Gson()
    if (data.optString("type") == DEFAULT_COMPANION_NONE)
        return gson.fromJson(data.toString(), TemplateData::class.java)

    val templateId = data.getString(ID_KEY)

    return when(templateId){
        ID_UNI_IMAGE_TEMPLATE -> gson.fromJson(data.toString(), BigBannerTemplateData::class.java)
        ID_GRID_IMAGE_TEMPLATE -> gson.fromJson(data.toString(), TableViewTemplateData::class.java)
        ID_CAROUSEL_IMAGE_TEMPLATE -> gson.fromJson(data.toString(), TableViewTemplateData::class.java)
        ID_END_CARD_IMAGE_TEMPLATE -> gson.fromJson(data.toString(), EndCardTemplateData::class.java)
        else -> throw IllegalStateException("$templateId not supported")
    }
}