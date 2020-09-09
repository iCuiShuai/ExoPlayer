package com.mxplay.offlineads.exo.oma.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ccom.mxplay.offlineads.exo.R;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Log;
import com.mxplay.offlineads.exo.oma.Ad;
import com.mxplay.offlineads.exo.oma.AdDisplayContainer;
import com.mxplay.offlineads.exo.oma.AdError;
import com.mxplay.offlineads.exo.oma.AdErrorEvent;
import com.mxplay.offlineads.exo.oma.AdEvent;
import com.mxplay.offlineads.exo.oma.AdEventImpl;
import com.mxplay.offlineads.exo.oma.AdGroup;
import com.mxplay.offlineads.exo.oma.AdProgressInfo;
import com.mxplay.offlineads.exo.oma.AdsManager;
import com.mxplay.offlineads.exo.oma.AdsRenderingSettings;
import com.mxplay.offlineads.exo.oma.ContentProgressProvider;
import com.mxplay.offlineads.exo.oma.VideoAdPlayer;
import com.mxplay.offlineads.exo.oma.VideoProgressUpdate;
import com.mxplay.offlineads.exo.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdsManagerImpl implements AdsManager {

  private static final long PRELOAD_TIME_OFFSET = 1000;
  private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;

  private static final boolean DEBUG = false;
  public static final String TAG = "OmaAdsManager";
  private static final int DELAY_MILLIS = 300;


  private final Context context;
  private final AdDisplayContainer adDisplayContainer;
  private final List<AdGroup> ads;
  private final Object userRequestContext;

  private final List<Float> cuePoints = new ArrayList<>();
  private ContentProgressProvider contentProgressProvider;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private HashMap<Ad, AdEvent.AdEventType> state = new HashMap<>();
  private Ad currentAd;
  private VideoProgressUpdate lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;


  private final Set<AdEvent.AdEventListener> adsEventListeners = Collections
      .synchronizedSet(new HashSet<>());
  private final Set<AdErrorEvent.AdErrorListener> adErrorListeners = Collections
      .synchronizedSet(new HashSet<>());


  public AdsManagerImpl(Context context,
      AdDisplayContainer adDisplayContainer, List<AdGroup> ads,
      ContentProgressProvider contentProgressProvider, Object userRequestContext) {
    this.context = context;
    this.adDisplayContainer = adDisplayContainer;
    this.adDisplayContainer.getPlayer().addCallback(videoAdPlayerCallback);
    this.ads = ads;
    this.userRequestContext = userRequestContext;
    initCuePoints();
    this.contentProgressProvider = contentProgressProvider;
    scheduleImmediate();
  }

  private void initCuePoints(){
    for (int i = 0; i < ads.size(); i++) {
      cuePoints.add(ads.get(i).getStartTime());
    }
  }

  private void processAd(long currentTime) {
    Ad nextAd = currentAd != null ? currentAd : getNextAd(currentTime);
    if (DEBUG) {
      Log.d(TAG, " processAd "+ (nextAd != null ? nextAd.getAdId() : ""));
    }
    if (nextAd != null){
      if (isValidAd(nextAd)){
        currentAd = nextAd;
        triggerEvent(currentAd, currentTime);
      }else {
        onAdError(new AdErrorEvent(new AdError(AdError.AdErrorType.PLAY, AdError.AdErrorCode.INTERNAL_ERROR, "Invlid ad "+ nextAd
            .toString()), userRequestContext));
      }

    }
  }

  private void triggerEvent(Ad ad, long currentTime){
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player == null) return ;

    AdEvent.AdEventType adEventType = state.get(ad);
    if (DEBUG) {
      Log.d(TAG, " trigger Event "+ (adEventType != null ? adEventType.name() : "")+ " ::: ");
    }

    if (adEventType == null && (ad.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND - currentTime) < (int)(PRELOAD_TIME_OFFSET/2)){
      setAdState(ad, AdEvent.AdEventType.LOADED);
      onEvent(new AdEventImpl(AdEvent.AdEventType.LOADED, ad, new HashMap<>()));
      setAdState(ad, AdEvent.AdEventType.STARTED);
      player.loadAd(ad.getMediaUrl());
      onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED, ad, new HashMap<>()));
      player.playAd();
      onEvent(new AdEventImpl(AdEvent.AdEventType.STARTED, ad, new HashMap<>()));
    }
  }

  private void stopAd(){
    if (currentAd == null) return;
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player == null) return ;
    AdEvent.AdEventType adEventType = state.get(currentAd);
    if (adEventType == AdEvent.AdEventType.AD_PROGRESS) {
      player.stopAd();
      markAdCompleted();
      scheduleImmediate();
    }
  }

  private void markAdCompleted() {
    setAdState(currentAd, AdEvent.AdEventType.COMPLETED);
    onEvent(new AdEventImpl(AdEvent.AdEventType.COMPLETED, currentAd, new HashMap<>()));
    if (currentAd.getAdPodInfo().getAdPosition() == currentAd.getAdPodInfo().getTotalAds()) {
      onEvent(new AdEventImpl(AdEvent.AdEventType.ALL_ADS_COMPLETED, currentAd, new HashMap<>()));
      onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, currentAd, new HashMap<>()));
      currentAd = null;
    }else {
      long playerPosition = (long) (currentAd.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND);
      currentAd = null;
      processAd(playerPosition);
    }
  }

  @Override
  public void start() {
    scheduleUpdate();

  }

  @Override
  public List<Float> getAdCuePoints() {
    return cuePoints;
  }

  private boolean isCurrentState(Ad ad, AdEvent.AdEventType adEventType){
    return  (ad != null && adEventType == state.get(ad));
  }
  @Override
  public void pause() {
    handler.removeCallbacks(updateRunnable);
    if (isCurrentState(currentAd, AdEvent.AdEventType.STARTED) || isCurrentState(currentAd, AdEvent.AdEventType.AD_PROGRESS)){
      setAdState(currentAd, AdEvent.AdEventType.PAUSED);
      onEvent(new AdEventImpl(AdEvent.AdEventType.PAUSED, currentAd, new HashMap<>()));
    }
  }

  private void setAdState(Ad currentAd, AdEvent.AdEventType paused) {
    state.put(currentAd, paused);
  }

  @Override
  public void resume() {
    if (isCurrentState(currentAd, AdEvent.AdEventType.PAUSED)){
      setAdState(currentAd, AdEvent.AdEventType.RESUMED);
      onEvent(new AdEventImpl(AdEvent.AdEventType.RESUMED, currentAd, new HashMap<>()));
    }
    scheduleUpdate();
  }

  @Override
  public void skip() {

  }

  @Override
  public void discardAdBreak() {

  }

  @Override
  public void requestNextAdBreak() {

  }

  @Override
  public void clicked() {

  }

  @Override
  public void focusSkipButton() {

  }

  @Override
  public void init() {

  }

  @Override
  public void init(AdsRenderingSettings var1) {

  }

  @Override
  public void destroy() {
    currentAd = null;
    handler.removeCallbacksAndMessages(null);
    state.clear();
    ads.clear();
    this.adDisplayContainer.getPlayer().removeCallback(videoAdPlayerCallback);
  }



  @Override
  public Ad getCurrentAd() {
    return currentAd;
  }

  @Override
  public boolean isCustomPlaybackUsed() {
    return false;
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
  public void addAdEventListener(AdEvent.AdEventListener adEventListener) {
    synchronized (adsEventListeners){adsEventListeners.add(adEventListener);}
  }

  @Override
  public void removeAdEventListener(AdEvent.AdEventListener adEventListener) {
    synchronized (adsEventListeners){adsEventListeners.remove(adEventListener);}
  }

  @Override
  public AdProgressInfo getAdProgressInfo() {
    return null;
  }

  @Override
  public VideoProgressUpdate getAdProgress() {
    return lastAdProgress;
  }


  private Runnable updateRunnable = new Runnable() {
    @Override
    public void run() {
      VideoAdPlayer player = adDisplayContainer.getPlayer();
      if (player == null) return ;
      VideoProgressUpdate adProgress = player.getAdProgress();
      if (!lastAdProgress.equals(adProgress) && isCurrentAdDone(lastAdProgress, adProgress)){
        try {
          stopAd();
        } catch (Exception e) {
            onAdError(new AdErrorEvent(new AdError(AdError.AdErrorType.PLAY, AdError.AdErrorCode.INTERNAL_ERROR, e.getMessage()), userRequestContext));
           scheduleImmediate();
        }
      }
      lastAdProgress = adProgress;
      showAdView(adProgress);
      VideoProgressUpdate contentProgress = contentProgressProvider.getContentProgress();
      if (DEBUG) {
        Log.d(TAG, " contentProgress "+ contentProgress.toString() + " Ad progress "+ adProgress.toString());
      }
      if (contentProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY){
        long pendingContentMs = (long) (contentProgress.getDuration() - contentProgress.getCurrentTime()) * C.MILLIS_PER_SECOND;
        if (pendingContentMs > 1000 && pendingContentMs < END_OF_CONTENT_POSITION_THRESHOLD_MS){
          processAd(C.TIME_END_OF_SOURCE);
        }else {
          processAd((long) (contentProgress.getCurrentTime() * C.MILLIS_PER_SECOND));
        }
      }
      handler.postDelayed(updateRunnable, 500);
    }
  };

  private boolean isCurrentAdDone(VideoProgressUpdate lastAdProgress, VideoProgressUpdate current) {
   return current != VideoProgressUpdate.VIDEO_TIME_NOT_READY
          && ((current.getCurrentTime() * 100) / current.getDuration()) > 99.0f;
  }

  private void showAdView(VideoProgressUpdate adProgress){
    ViewGroup adContainer = adDisplayContainer.getAdContainer();
    Ad playingAd = currentAd;
    if (playingAd != null && adProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY){
      adContainer.setVisibility(View.VISIBLE);
      TextView adProgressText = adContainer.findViewById(R.id.adCounter);
      // Handle ad counter.
      String adProgressStr = DateTimeUtils.formatTime((adProgress.getDuration() - adProgress.getCurrentTime()));
      String adUiString;
      if (playingAd.getAdPodInfo().getTotalAds() > 1){
        adUiString = context.getString(R.string.ad_progress, playingAd.getAdPodInfo().getAdPosition(),
            playingAd.getAdPodInfo().getTotalAds(), adProgressStr);
      }else {
        adUiString = context.getString(R.string.ad_progress_without_group, adProgressStr);
      }

      adProgressText.setText(adUiString);
      setAdState(playingAd, AdEvent.AdEventType.AD_PROGRESS);
      onEvent(new AdEventImpl(AdEvent.AdEventType.AD_PROGRESS, playingAd, new HashMap<>()));
    }else {
      adContainer.setVisibility(View.GONE);
    }
  }

  private void scheduleImmediate(){
    handler.removeCallbacks(updateRunnable);
    handler.post(updateRunnable);
  }
  private void scheduleUpdate(){
    handler.removeCallbacks(updateRunnable);
    handler.postDelayed(updateRunnable, DELAY_MILLIS);
  }

  private Ad getNextAd(long currentTime){
    // Use a linear search as the array elements may not be increasing due to TIME_END_OF_SOURCE.
    // In practice we expect there to be few ad groups so the search shouldn't be expensive.
    int index = ads.size() - 1;
    while (index >= 0 && isPositionBeforeAdGroup(currentTime, index)) {
      index--;
    }
    if (index >= 0){
      AdGroup adGroup = ads.get(index);
      for (Ad ad : adGroup.getAds()){
        if (!isCurrentState(ad, AdEvent.AdEventType.COMPLETED)
            && !isCurrentState(ad, AdEvent.AdEventType.SKIPPED)){
          return ad;
        }
      }
    }
    return null;
  }

  private boolean isPositionBeforeAdGroup(long positionUs, int adIndex) {
    if (positionUs == C.TIME_END_OF_SOURCE) {
      // The end of the content is at (but not before) any postroll ad, and after any other ads.
      return false;
    }
    AdGroup adGroup = ads.get(adIndex);
    if (adGroup.getStartTime() == C.INDEX_UNSET) {
      return  true;
    } else {
      return positionUs + PRELOAD_TIME_OFFSET <  (adGroup.getStartTime() * C.MILLIS_PER_SECOND);
    }
  }


  private void onAdError(AdErrorEvent adErrorEvent){
    synchronized (adErrorListeners){
      for (AdErrorEvent.AdErrorListener listener: adErrorListeners) {
        listener.onAdError(adErrorEvent);
      }
    }
  }

  private void onEvent(AdEvent adEvent){
    synchronized (adsEventListeners){
      for (AdEvent.AdEventListener listener: adsEventListeners) {
        listener.onAdEvent(adEvent);
      }
    }
  }


  private boolean isValidAd(Ad ad){
    return !TextUtils.isEmpty(ad.getMediaUrl());
  }

  private  VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback = new VideoAdPlayer.VideoAdPlayerCallback() {
    @Override
    public void onPlay() {

    }

    @Override
    public void onVolumeChanged(int var1) {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onLoaded() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void onEnded() {

    }

    @Override
    public void onError() {
      if (DEBUG) {
        Log.d(TAG, " onError  "+ (currentAd != null ? currentAd.getAdId() : ""));
      }
      if (isCurrentState(currentAd, AdEvent.AdEventType.STARTED) || isCurrentState(currentAd, AdEvent.AdEventType.AD_PROGRESS)){
        markAdCompleted();
      }
    }

    @Override
    public void onBuffering() {

    }
  };

}
