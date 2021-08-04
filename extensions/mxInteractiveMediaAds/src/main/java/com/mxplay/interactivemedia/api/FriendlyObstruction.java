package com.mxplay.interactivemedia.api;

import android.view.View;

public interface FriendlyObstruction {
    View getView();

    FriendlyObstructionPurpose getPurpose();

    String getDetailedReason();
}