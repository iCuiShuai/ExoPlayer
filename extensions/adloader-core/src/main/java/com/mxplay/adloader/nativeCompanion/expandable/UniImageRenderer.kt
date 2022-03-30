package com.mxplay.adloader.nativeCompanion.expandable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ccom.mxplay.adloader.R
import com.mxplay.adloader.nativeCompanion.CompanionResourceProvider
import com.mxplay.adloader.nativeCompanion.EventsTracker
import org.json.JSONObject

class UniImageRenderer(context: Context, parent: ViewGroup, json: JSONObject, eventsTracker: EventsTracker, companionResourceProvider: CompanionResourceProvider) : ExpandableRendererBase(context, parent, json, eventsTracker,  companionResourceProvider) {

    companion object {
        const val ID = "UNI_IMAGE_TEMPLATE"
    }

    fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.layout_native_expandable_template_uniimage, parent, false)
    }

    fun bindView(view: View) {
        val action = view.findViewById<TextView>(R.id.native_ad_action_button)
        if (json.has("clickThroughUrl")) {
            action.setOnClickListener {
                kotlin.runCatching {
                    view.context.startActivity(Intent().apply {
                        setAction(Intent.ACTION_VIEW)
                        data = Uri.parse(json.getString("clickThroughUrl"))
                    })

                }
            }
        }
        action.text = json.optString("CTA") ?: view.context.getString(R.string.cta_learn_more)
        companionResourceProvider.loadImage(json.optString("image"), view.findViewById(R.id.image))
    }


    override fun renderChildView(parent: ViewGroup): View {
        val createView = createView(context, parent)
        bindView(createView)
        return createView
    }


}