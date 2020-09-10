package com.mxplay.offlineads.exo.oma.internal;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.mxplay.offlineads.exo.mappers.Mapper;
import com.mxplay.offlineads.exo.oma.Ad;
import com.mxplay.offlineads.exo.oma.AdDisplayContainer;
import com.mxplay.offlineads.exo.oma.AdError;
import com.mxplay.offlineads.exo.oma.AdErrorEvent;
import com.mxplay.offlineads.exo.oma.AdGroup;
import com.mxplay.offlineads.exo.oma.AdsLoader;
import com.mxplay.offlineads.exo.oma.AdsManagerLoadedEvent;
import com.mxplay.offlineads.exo.vast.model.VMAPModel;
import com.mxplay.offlineads.exo.vast.processor.VMAPProcessor;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdLoaderImpl extends
    AsyncTask<AdsRequest, Void, AdLoaderImpl.AdLoaderResponse> implements AdsLoader {

  private final Context context;
  private final AdDisplayContainer adDisplayContainer;

  static final class AdLoaderResponse {

    private AdErrorEvent adErrorEvent;
    private AdsManagerLoadedEvent adsManagerLoadedEvent;

    public AdLoaderResponse(AdErrorEvent adErrorEvent) {
      this.adErrorEvent = adErrorEvent;
    }

    public AdLoaderResponse(AdsManagerLoadedEvent adsManagerLoadedEvent) {
      this.adsManagerLoadedEvent = adsManagerLoadedEvent;
    }
  }

  private final Set<AdsLoadedListener> adsLoadedListeners = Collections
      .synchronizedSet(new HashSet<>());
  private final Set<AdErrorEvent.AdErrorListener> adErrorListeners = Collections
      .synchronizedSet(new HashSet<>());

  public AdLoaderImpl(Context context, AdDisplayContainer adDisplayContainer) {
    this.context = context;
    this.adDisplayContainer = adDisplayContainer;
  }


  @Override
  public void contentComplete() {

  }

  @Override
  public void requestAds(AdsRequest adsRequest) {
    if (getStatus() != Status.PENDING){
      return;
    }
    execute(adsRequest);
  }

  @Override
  public void addAdsLoadedListener(AdsLoadedListener adsLoadedListener) {
    synchronized (adsLoadedListeners) {
      adsLoadedListeners.add(adsLoadedListener);
    }
  }

  @Override
  public void removeAdsLoadedListener(AdsLoadedListener adsLoadedListener) {
    synchronized (adsLoadedListeners) {
      adsLoadedListeners.remove(adsLoadedListener);
    }
  }

  @Override
  public void addAdErrorListener(AdErrorEvent.AdErrorListener adErrorListener) {
    synchronized (adErrorListeners) {
      adErrorListeners.add(adErrorListener);
    }
  }

  @Override
  public void removeAdErrorListener(AdErrorEvent.AdErrorListener adErrorListener) {
    synchronized (adErrorListeners) {
      adErrorListeners.remove(adErrorListener);
    }
  }

  @Override
  protected AdLoaderResponse doInBackground(AdsRequest... adsRequests) {
    AdsRequest adsRequest = adsRequests[0];
    try {
      VMAPModel vmapModel = new VMAPProcessor().process(adsRequest.getAdsResponse());
      List<AdGroup> adGroups = new Mapper().toAdGroups(vmapModel.getAdBreaks());
      if (adGroups.isEmpty()){
        return new AdLoaderResponse(new AdErrorEvent(new AdError(AdError.AdErrorType.LOAD,
                AdError.AdErrorCode.VAST_EMPTY_RESPONSE,"Empty vast response"),
                adsRequest.getUserRequestContext()));
      }
      AdsManagerImpl adsManager = new AdsManagerImpl(context, adDisplayContainer, adGroups, adsRequest.getContentProgressProvider(), adsRequest.getUserRequestContext());
      return new AdLoaderResponse(
          new AdsManagerLoadedEvent(adsManager, adsRequest.getUserRequestContext()));
    } catch (Exception e) {
      Log.e("AdLoaderImpl" ,"",  e);
      return new AdLoaderResponse(new AdErrorEvent(new AdError(AdError.AdErrorType.LOAD,
          AdError.AdErrorCode.FAILED_TO_REQUEST_ADS, e.getMessage()),
          adsRequest.getUserRequestContext()));
    }
  }

  @Override
  protected void onPostExecute(AdLoaderResponse adLoaderResponse) {
    super.onPostExecute(adLoaderResponse);
    if (adLoaderResponse.adErrorEvent != null) {
      synchronized (adErrorListeners) {
        for (AdErrorEvent.AdErrorListener listener :
            adErrorListeners) {
          listener.onAdError(adLoaderResponse.adErrorEvent);
        }
      }
    } else {
      synchronized (adsLoadedListeners) {
        for (AdsLoadedListener listener :
            adsLoadedListeners) {
          listener.onAdsManagerLoaded(adLoaderResponse.adsManagerLoadedEvent);
        }
      }
    }
  }
}
