package com.mxplay.interactivemedia.api

import android.view.ViewGroup
import com.mxplay.interactivemedia.api.player.VideoAdPlayer
import java.util.*

class AdDisplayContainer {
    private var videoAdPlayer: VideoAdPlayer? = null
    private var adContainer: ViewGroup? = null
    private var companionAdSlots: MutableCollection<CompanionAdSlot>? = null
    private val obstructionList: MutableList<FriendlyObstruction> = LinkedList()

    fun getPlayer(): VideoAdPlayer? {
        return videoAdPlayer
    }


    fun setPlayer(videoAdPlayer: VideoAdPlayer?) {
        this.videoAdPlayer = videoAdPlayer
    }

    fun getAdContainer(): ViewGroup? {
        return adContainer
    }

    fun setAdContainer(adContainer: ViewGroup?) {
        this.adContainer = adContainer
    }

    fun destroy() {
        obstructionList.clear()
        companionAdSlots!!.clear()
    }

    fun setCompanionSlots(companionAdSlots: MutableCollection<CompanionAdSlot>?) {
        this.companionAdSlots = companionAdSlots
    }

    fun unregisterAllFriendlyObstructions() {
        obstructionList.clear()
    }

    fun registerFriendlyObstruction(friendlyObstruction: FriendlyObstruction) {
        obstructionList.add(friendlyObstruction)
    }

    fun getCompanionAdSlots(): Collection<CompanionAdSlot>? {
        return companionAdSlots
    }

    fun getObstructionList(): List<FriendlyObstruction> {
        return obstructionList
    }
}