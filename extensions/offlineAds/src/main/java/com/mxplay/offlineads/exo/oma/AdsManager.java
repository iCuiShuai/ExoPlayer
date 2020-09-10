

package com.mxplay.offlineads.exo.oma;

import java.util.List;

public interface AdsManager extends BaseManager {
    void start();

    List<Float> getAdCuePoints();

    void pause();

    void resume();

    void skip();

    void discardAdBreak();

    /** @deprecated */
    @Deprecated
    void requestNextAdBreak();

}
