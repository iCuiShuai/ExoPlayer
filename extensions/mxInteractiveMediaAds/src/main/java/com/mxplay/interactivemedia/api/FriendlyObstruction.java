package com.mxplay.interactivemedia.api;

import android.view.View;

import androidx.annotation.Nullable;

public interface FriendlyObstruction {
    View getView();

    FriendlyObstructionPurpose getPurpose();

    @Nullable String getDetailedReason();
}