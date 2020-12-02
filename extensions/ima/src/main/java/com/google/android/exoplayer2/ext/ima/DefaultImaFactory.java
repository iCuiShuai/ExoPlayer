package com.google.android.exoplayer2.ext.ima;

import android.content.Context;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;

/** Default {@link ImaFactory} for non-test usage, which delegates to {@link ImaSdkFactory}. */
  public  final class DefaultImaFactory implements ImaFactory {
    @Override
    public ImaSdkSettings createImaSdkSettings() {
      return ImaSdkFactory.getInstance().createImaSdkSettings();
    }

    @Override
    public AdsRenderingSettings createAdsRenderingSettings() {
      return ImaSdkFactory.getInstance().createAdsRenderingSettings();
    }

    @Override
    public AdDisplayContainer createAdDisplayContainer() {
      return ImaSdkFactory.getInstance().createAdDisplayContainer();
    }

    @Override
    public AdsRequest createAdsRequest() {
      return ImaSdkFactory.getInstance().createAdsRequest();
    }

    @Override
    public com.google.ads.interactivemedia.v3.api.AdsLoader createAdsLoader(
            Context context, ImaSdkSettings imaSdkSettings, AdDisplayContainer adDisplayContainer) {
      return ImaSdkFactory.getInstance()
          .createAdsLoader(context, imaSdkSettings, adDisplayContainer);
    }
  }