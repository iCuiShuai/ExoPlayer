

package com.mxplay.interactivemedia.api;

import com.mxplay.interactivemedia.api.player.AdProgressProvider;

public interface BaseManager extends AdProgressProvider {

    void init(AdsRenderingSettings var1);

    void destroy();

    Ad getCurrentAd();

    void addAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void removeAdErrorListener(AdErrorEvent.AdErrorListener var1);

    void addAdEventListener(AdEvent.AdEventListener var1);

    void removeAdEventListener(AdEvent.AdEventListener var1);
}
