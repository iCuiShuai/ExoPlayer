package com.mxplay.adloader.nativeCompanion.expandable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        bindCTA(action)
        val ad = json.optJSONArray("ads")?.getJSONObject(0)
        if (ad != null){
            val url = ad.optString("image")
            val imageView = view.findViewById<ImageView>(R.id.image)
            companionResourceProvider.loadImage(url, imageView)
            if (ad.has("clickThroughUrl")){
                view.setOnClickListener {
                    kotlin.runCatching {
                        context.startActivity(Intent().apply {
                            setAction(Intent.ACTION_VIEW)
                            data = Uri.parse(json.getString("clickThroughUrl"))
                        })
                        trackClick(ad)
                    }
                }
            }

        }

    }


    override fun renderChildView(parent: ViewGroup): View {
        val createView = createView(context, parent)
        bindView(createView)
        return createView
    }


}