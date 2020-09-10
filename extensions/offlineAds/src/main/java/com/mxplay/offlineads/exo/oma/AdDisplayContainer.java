package com.mxplay.offlineads.exo.oma;


import android.view.ViewGroup;

public class AdDisplayContainer {
    private VideoAdPlayer videoAdPlayer;
    private ViewGroup adContainer;

    public VideoAdPlayer getPlayer() {
        return videoAdPlayer;
    }


    public void setPlayer(VideoAdPlayer videoAdPlayer) {
        this.videoAdPlayer = videoAdPlayer;
    }
    public ViewGroup getAdContainer() {
        return adContainer;
    }

    public void setAdContainer(ViewGroup adContainer) {
        this.adContainer = adContainer;
    }
    public void destroy() {
    }
}
