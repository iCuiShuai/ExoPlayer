package com.mxplay.interactivemedia.internal.data.model

import android.text.TextUtils
import android.util.Log
import com.mxplay.interactivemedia.api.AdEvent

const val EVENT_XML_ATTR = "event"


enum class EventName(val value : String){

    INIT("Init"),
    START("start"),
    PROGRESS("progress"),
    FIRST_QUARTILE("firstQuartile"),
    MIDPOINT("midpoint"),
    THIRD_QUARTILE("thirdQuartile"),
    COMPLETE("complete"),
    SKIP("skip"),
    SHOW_AD("show_ad"),
    LOADED("loaded"),
    IMPRESSION("Impression"),
    VIDEO_CLICK("ClickTracking"),
    COMPANION_CLICK("CompanionClickTracking"),
    MUTE("mute"),
    UNMUTE("unmute"),
    PAUSE("pause"),
    RESUME("resume"),
    CREATIVEVIEW("creativeView"),
    FULLSCREEN("fullscreen"),
    ACCEPT_INVITATION_LINEAR("acceptInvitationLinear"),
    ACCEPT_INVITATION("acceptInvitation"),
    CLOSE_LINEAR("closeLinear"),
    CLOSE("close"),
    REWIND("rewind"),
    EXIT_FULLSCREEN("exitFullscreen"),
    ERROR("Error"),
    BREAK_START("breakStart"),
    BREAK_END("breakEnd"),
    VERIFICATION_NOT_EXECUTED("verificationNotExecuted"),
    PLAYER_EXPAND("playerExpand"),
    PLAYER_COLLAPSE("playerCollapse"),
    NOT_USED("notUsed"),
    OTHER_AD_INTERACTION("otherAdInteraction"),
    AD_EXPAND("adExpand"),
    AD_COLLAPSE("adCollapse"),
    MINIMIZE("minimize"),
    OVERLAY_VIEW_DURATION("overlayViewDuration"),
    VOLUME_CHANGE("volumeChange");

    companion object{
        fun getType(value: String): EventName? {
            when (value) {
                START.value -> return START
                PROGRESS.value -> return PROGRESS
                FIRST_QUARTILE.value -> return FIRST_QUARTILE
                MIDPOINT.value -> return MIDPOINT
                THIRD_QUARTILE.value -> return THIRD_QUARTILE
                COMPLETE.value -> return COMPLETE
                MUTE.value -> return MUTE
                UNMUTE.value -> return UNMUTE
                PAUSE.value -> return PAUSE
                RESUME.value -> return RESUME
                CREATIVEVIEW.value -> return CREATIVEVIEW
                FULLSCREEN.value -> return FULLSCREEN
                ACCEPT_INVITATION_LINEAR.value -> return ACCEPT_INVITATION_LINEAR
                ACCEPT_INVITATION.value -> return ACCEPT_INVITATION
                CLOSE_LINEAR.value  -> return CLOSE_LINEAR
                CLOSE.value  -> return CLOSE
                REWIND.value -> return REWIND
                EXIT_FULLSCREEN.value -> return EXIT_FULLSCREEN
                SKIP.value -> return SKIP
                SHOW_AD.value -> return SHOW_AD
                LOADED.value -> return LOADED
                IMPRESSION.value -> return IMPRESSION
                ERROR.value -> return ERROR
                BREAK_START.value -> return BREAK_START
                BREAK_END.value -> return BREAK_END
                PLAYER_EXPAND.value -> return PLAYER_EXPAND
                PLAYER_COLLAPSE.value -> return PLAYER_COLLAPSE
                NOT_USED.value -> return NOT_USED
                OTHER_AD_INTERACTION.value -> return OTHER_AD_INTERACTION
                AD_EXPAND.value -> return AD_EXPAND
                AD_COLLAPSE.value -> return AD_COLLAPSE
                MINIMIZE.value -> return MINIMIZE
                VOLUME_CHANGE.value -> return VOLUME_CHANGE
                OVERLAY_VIEW_DURATION.value -> OVERLAY_VIEW_DURATION
            }
            Log.e("EventNameMapper", "Unidentified event name $value")
            return null
        }

        fun getType(value: AdEvent.AdEventType): EventName? {
            return when (value) {
                AdEvent.AdEventType.STARTED -> START
                AdEvent.AdEventType.FIRST_QUARTILE -> FIRST_QUARTILE
                AdEvent.AdEventType.MIDPOINT -> MIDPOINT
                AdEvent.AdEventType.THIRD_QUARTILE -> THIRD_QUARTILE
                AdEvent.AdEventType.COMPLETED -> COMPLETE
                AdEvent.AdEventType.PAUSED -> PAUSE
                AdEvent.AdEventType.RESUMED -> RESUME
                AdEvent.AdEventType.SKIPPED -> SKIP
                AdEvent.AdEventType.LOADED -> LOADED
                AdEvent.AdEventType.CREATIVE_VIEW -> CREATIVEVIEW
                AdEvent.AdEventType.VOLUME_CHANGE -> VOLUME_CHANGE
                AdEvent.AdEventType.CLICKED -> VIDEO_CLICK
                AdEvent.AdEventType.COMPANION_CLICKED -> COMPANION_CLICK
                else -> null
            }
        }
    }


}


open class TrackingEvent(val name: EventName, val trackingUrl: String, val allowMultiple : Boolean = false) {
   private var trackingCount = 0
    fun onEventTracked() {
        trackingCount++
    }

    fun isTrackingAllowed(): Boolean {
        return !TextUtils.isEmpty(trackingUrl) && (trackingCount == 0 || allowMultiple)
    }

    companion object{
        const val TRACKING_EVENTS_XML_TAG = "TrackingEvents"
        const val TRACKING_XML_TAG = "Tracking"
    }
}

class ClickEvent(name: EventName, trackingUrl: String) : TrackingEvent(name, trackingUrl, true) {
    constructor(name: EventName, id: String, trackingUrl: String) : this(name, trackingUrl)
}
class ErrorEvent(name: EventName, trackingUrl: String) : TrackingEvent(name, trackingUrl, false)
class ImpressionEvent(name: EventName, trackingUrl: String) : TrackingEvent(name, trackingUrl, false)