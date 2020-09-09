//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.offlineads.exo.oma;

public interface BaseManager extends AdProgressProvider {
    void init();

    void init(AdsRenderingSettings var1);

    void destroy();

    Ad getCurrentAd();

    boolean isCustomPlaybackUsed();

    void addAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void removeAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void addAdEventListener(AdEvent.AdEventListener var1);

    void removeAdEventListener(AdEvent.AdEventListener var1);

    AdProgressInfo getAdProgressInfo();
}
