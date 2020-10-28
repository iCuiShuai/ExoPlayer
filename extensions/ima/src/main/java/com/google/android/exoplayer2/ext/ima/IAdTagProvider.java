package com.google.android.exoplayer2.ext.ima;

import android.net.Uri;

import androidx.annotation.NonNull;

public interface IAdTagProvider {
    void registerTagListener(@NonNull Listener listener);
     interface Listener{
        void onTagReceived(Uri adTag);
    }
}
