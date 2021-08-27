package com.mxplay.adloader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IAdTagProvider {
    void registerTagListener(@NonNull Listener listener);
    @Nullable AdTagData getAdTagData();
     interface Listener{
        void onTagReceived(@NonNull AdTagData adTagData);
    }
}
