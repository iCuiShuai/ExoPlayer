package com.mxplay.offlineads.exo.oma.internal;

import com.mxplay.offlineads.exo.oma.ContentProgressProvider;

public class AdsRequest {

  private Object userRequestContext;
  private ContentProgressProvider contentProgressProvider;
  private String adsResponse;

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

}
