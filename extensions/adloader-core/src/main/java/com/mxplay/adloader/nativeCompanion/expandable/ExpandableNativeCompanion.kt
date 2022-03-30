package com.mxplay.adloader.nativeCompanion.expandable

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import com.mxplay.adloader.nativeCompanion.NativeCompanion
import com.mxplay.interactivemedia.api.AdEvent
import com.mxplay.interactivemedia.api.CompanionAdSlot
import com.mxplay.interactivemedia.internal.data.RemoteDataSource
import com.mxplay.logger.ZenLogger
import org.json.JSONObject

class ExpandableNativeCompanion(json: JSONObject, private val companionAdSlot: CompanionAdSlot, private val eventsTracker: EventsTracker, private val resourceProvider: CompanionResourceProvider,
                                type: NativeCompanionType) : NativeCompanion(type, json) {


    private val context: Context = companionAdSlot.container.context


    companion object {
        private const val TAG = "ExpandableNativeCompanion"
        const val NATIVE_EXPAND_THRESHOLD = 5000L
        const val DURATION_SHORT = 300L
        const val DURATION_LONG = 500L
    }


    override fun onAdEvent(adEvent: AdEvent) {
        super.onAdEvent(adEvent)

        val type: AdEvent.AdEventType = adEvent.type
        if (type == AdEvent.AdEventType.AD_PROGRESS) {
            val contentDuration = adEvent.ad?.getDuration()
            /* val contentPos = eventWrapper.currentPosition
             if (contentDuration > 0 && contentPos > 0
                 && contentDuration >= 2 * NATIVE_EXPAND_THRESHOLD
                 &&  contentPos >= NATIVE_EXPAND_THRESHOLD
                 && expandHandler?.getTag(R.id.tag_visibility)?: false == false
             ) {
                 expandHandler?.performClick()
                 return
             }*/
        }


        if (type == AdEvent.AdEventType.CONTENT_RESUME_REQUESTED || type == AdEvent.AdEventType.COMPLETED || type == AdEvent.AdEventType.ALL_ADS_COMPLETED || type == AdEvent.AdEventType.SKIPPED) {
            // #debug debug
            ZenLogger.dt(TAG, " hiding animation start ")
            release()
            return
        }
        return
    }


    fun release() {
        template.renderer.release()
    }


    val template: NativeCompanion.NativeCompanionTemplate
        get() = ExpandableTemplate(json, "ExpandableTemplate", ExpandableTemplate.getRenderer(json.optString(ExpandableTemplate.ID_KEY), context, companionAdSlot.container, json, eventsTracker, resourceProvider)!!)

    override fun loadCompanion() {
        template.loadCompanionTemplate()
    }


}

class ExpandableTemplate(val json: JSONObject, override val id: String, override val renderer: NativeCompanion.NativeCompanionRenderer) : NativeCompanion.NativeCompanionTemplate {
    companion object {
        const val ID_KEY = "templateId"

        fun getRenderer(id: String, context: Context, parent: ViewGroup, json: JSONObject, eventsTracker: EventsTracker, companionResourceProvider: CompanionResourceProvider): NativeCompanion.NativeCompanionRenderer? {
            return when (id) {
                UniImageRenderer.ID -> UniImageRenderer(context, parent, json, eventsTracker, companionResourceProvider)
                else -> null
            }
        }
    }

    override fun loadCompanionTemplate(): View? {
        return renderer.render()
    }


}

