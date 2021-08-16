//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.mxplay.interactivemedia.api;

import android.view.ViewGroup;

public interface CompanionAdSlot {
    boolean isFilled();

    int getWidth();

    int getHeight();

    void setSize(int width, int height);

    ViewGroup getContainer();

    void setContainer(ViewGroup viewContainer);

    void addClickListener(CompanionAdSlot.ClickListener clickListener);

    void removeClickListener(CompanionAdSlot.ClickListener clickListener);

    public interface ClickListener {
        void onCompanionAdClick();
    }
}
