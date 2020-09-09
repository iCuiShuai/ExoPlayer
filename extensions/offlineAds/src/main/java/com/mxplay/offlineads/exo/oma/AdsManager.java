//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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

    void clicked();

    void focusSkipButton();
}
