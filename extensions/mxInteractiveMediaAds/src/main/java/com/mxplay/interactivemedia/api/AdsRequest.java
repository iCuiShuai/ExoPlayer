package com.mxplay.interactivemedia.api;

import com.mxplay.interactivemedia.api.player.ContentProgressProvider;

public class AdsRequest {

  private Object userRequestContext;
  private ContentProgressProvider contentProgressProvider;
  private String adsResponse;
  private int vastLoadTimeoutMs;
  private String adTagUrl;

  public void setUserRequestContext(Object userRequestContext) {
    this.userRequestContext = userRequestContext;
  }

  public Object getUserRequestContext() {
    return userRequestContext;
  }

  public ContentProgressProvider getContentProgressProvider() {
    return contentProgressProvider;
  }

  public void setContentProgressProvider(ContentProgressProvider contentProgressProvider) {
      this.contentProgressProvider = contentProgressProvider;
  }

  public String getAdsResponse() {
    return adsResponse;
  }

  public void setAdsResponse(String adsResponse) {
    this.adsResponse = adsResponse;
  }

  public void setAdTagUrl(String adTagUrl) {
    this.adTagUrl = adTagUrl;
  }

  public void setVastLoadTimeout(int vastLoadTimeoutMs) {
    this.vastLoadTimeoutMs = vastLoadTimeoutMs;
  }

  public int getVastLoadTimeoutMs() {
    return vastLoadTimeoutMs;
  }

  public String getAdTagUrl() {
    return adTagUrl;
  }
}
