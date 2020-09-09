package com.google.android.exoplayer2.ext.ima;

import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;

public class AdsLoadRequestTimeoutEvent implements AdErrorEvent {

  private Object userRequestCtx;

  public AdsLoadRequestTimeoutEvent(Object userRequestCtx) {
    this.userRequestCtx = userRequestCtx;
  }

  @Override
  public AdError getError() {
    return new AdError(AdError.AdErrorType.LOAD, AdError.AdErrorCode.FAILED_TO_REQUEST_ADS, "Failed to request ads timeout over");
  }

  @Override
  public Object getUserRequestContext() {
    return userRequestCtx;
  }
}
