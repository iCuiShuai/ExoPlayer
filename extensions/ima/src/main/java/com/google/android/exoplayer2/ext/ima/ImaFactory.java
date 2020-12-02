package com.google.android.exoplayer2.ext.ima;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;

/** Factory for objects provided by the IMA SDK. */
  @VisibleForTesting
  /* package */ interface ImaFactory {
    /** @see ImaSdkSettings */
    ImaSdkSettings createImaSdkSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRenderingSettings() */
    AdsRenderingSettings createAdsRenderingSettings();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdDisplayContainer() */
    AdDisplayContainer createAdDisplayContainer();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsRequest() */
    AdsRequest createAdsRequest();
    /** @see com.google.ads.interactivemedia.v3.api.ImaSdkFactory#createAdsLoader(Context, ImaSdkSettings, AdDisplayContainer) */
    com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
            Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer);
  }
