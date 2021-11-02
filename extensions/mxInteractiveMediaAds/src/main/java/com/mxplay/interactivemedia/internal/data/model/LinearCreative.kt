/*
 * Copyright (C) 2020 Flipkart Internet Pvt Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mxplay.interactivemedia.internal.data.model


class LinearCreative(id: String) : Creative(id) {
    /** duration of the ad **/
    var duration: String? = null

    /** duration of the ad in seconds **/
    var durationInSeconds: Long = 0

    /** skip offset, either HH:MM:SS.mmm or % **/
    var skipOffset: String? = null

    /** skip offset in seconds **/
    var skipOffsetInSeconds: Long = -1

    /** video click events **/
    var videoClicks: VideoClicks? = null

    /** list of media files **/
    var mediaFiles: List<MediaFile>? = null

    var adParameters: String? = null

    fun hasMedia() = mediaFiles != null && mediaFiles!!.isNotEmpty()

    companion object {
        const val DURATION_XML_TAG = "Duration"
        const val AD_PARAMETERS_XML_TAG = "AdParameters"
        const val SKIP_OFFSET_XML_TAG = "skipoffset"
        const val MEDIA_FILES_XML_TAG = "MediaFiles"
        const val VIDEO_CLICKS_XML_TAG = "VideoClicks"
    }

    override fun provideTrackingEvent(): Map<EventName, MutableList<TrackingEvent>>? {
        val eventsMapping = super.provideTrackingEvent()?.toMutableMap()
        videoClicks?.clickTracking?.let {
            if (it.isNotEmpty()) {
                val key = it[0].name
                eventsMapping?.set(key, it.toMutableList())
            }
        }
        return eventsMapping
    }
}
