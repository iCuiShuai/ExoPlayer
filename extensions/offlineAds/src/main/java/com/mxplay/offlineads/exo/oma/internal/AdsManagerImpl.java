package com.mxplay.offlineads.exo.oma.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.mxplay.offlineads.exo.oma.AdImpl;
import com.mxplay.offlineads.exo.oma.AdPodInfo;
import com.mxplay.offlineads.exo.oma.AdPodInfoImpl;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdsManagerImpl implements AdsManager, View.OnClickListener {

  private static final int DELAY_MILLIS = 500;
  private static final long PRELOAD_TIME_OFFSET = DELAY_MILLIS + 100;
  private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;
  private static final long END_OF_AD_POSITION_THRESHOLD_MS = 500;


  private static final int PRE_ROLL_POD_INDEX = 0;
  private static final int POST_ROLL_POD_INDEX = -1;

  private static final boolean DEBUG = false;
  public static final String TAG = "OmaAdsManager";
  private static final int DELAY_MILLIS_AD_RUNNING = 200;
  private static final Map<String, String> EMPTY_AD_DATA =  new HashMap<>();



  private final Context context;
  private final AdDisplayContainer adDisplayContainer;
  private final List<AdGroup> ads;
  private final Object userRequestContext;

  private final List<Float> cuePoints = new ArrayList<>();
  private ContentProgressProvider contentProgressProvider;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Map<Ad, AdEvent.AdEventType> state = new HashMap<>();
  private Ad currentAd;
  private VideoProgressUpdate lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
  private TextView adProgressText;
  private Button skipButton;


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
    this.contentProgressProvider = contentProgressProvider;
    initCuePoints();
    initViews();
  }

  private void initCuePoints(){
    for (int i = 0; i < ads.size(); i++) {
      cuePoints.add(ads.get(i).getStartTime());
    }
  }

  private void initViews(){
    adProgressText = adDisplayContainer.getAdContainer().findViewById(R.id.adCounter);
    skipButton = adDisplayContainer.getAdContainer().findViewById(R.id.skipButton);
    skipButton.setOnClickListener(this);
  }

  private Ad processNextAd(long currentTime) {
    Ad nextAd = getNextAd(currentTime);
    if (DEBUG) {
      Log.d(TAG, " processAd "+ (nextAd != null ? nextAd.getAdId() : ""));
    }
    if (nextAd != null){
      if (isValidAd(nextAd)){
        triggerEvent(nextAd);
      }else {
        onAdError(new AdErrorEvent(new AdError(AdError.AdErrorType.PLAY, AdError.AdErrorCode.INTERNAL_ERROR, "Invlid ad "+ nextAd
            .toString()), userRequestContext));
      }

    }
    return nextAd;
  }

  private boolean triggerEvent(Ad ad){
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player == null) return false;

    AdEvent.AdEventType adEventType = state.get(ad);
    if (DEBUG) {
      Log.d(TAG, " trigger Event "+ (adEventType != null ? adEventType.name() : "")+ " ::: ");
    }

    if (adEventType == null){
      setAdState(ad, AdEvent.AdEventType.LOADED);
      onEvent(new AdEventImpl(AdEvent.AdEventType.LOADED, ad, EMPTY_AD_DATA));
      setAdState(ad, AdEvent.AdEventType.STARTED);
      player.loadAd(ad.getMediaUrl());
      onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED, ad, EMPTY_AD_DATA));
      player.playAd();
      onEvent(new AdEventImpl(AdEvent.AdEventType.STARTED, ad, EMPTY_AD_DATA));
      return true;
    }
    return false;
  }

  private Ad stopAd(Ad ad){
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player == null)
      return null;
    AdEvent.AdEventType adEventType = state.get(ad);
    if (adEventType == AdEvent.AdEventType.AD_PROGRESS || adEventType == AdEvent.AdEventType.RESUMED) {
      player.stopAd();
      markAdCompleted(ad);
      long playerPosition = (long) (ad.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND);
      return processNextAd(playerPosition);
    }
    return null;
  }


  private void skipAd(Ad ad){
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player == null)
      return;
    AdEvent.AdEventType adEventType = state.get(ad);
    if (adEventType == AdEvent.AdEventType.AD_PROGRESS) {
      setAdState(ad, AdEvent.AdEventType.SKIPPED);
      player.stopAd();
      onEvent(new AdEventImpl(AdEvent.AdEventType.SKIPPED, ad, EMPTY_AD_DATA));
      player.pauseAd();
      long playerPosition = (long) (ad.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND);
      currentAd = processNextAd(playerPosition);
      if (currentAd != null){
        scheduleUpdate(4 * DELAY_MILLIS);
      }else {
        onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, ad, EMPTY_AD_DATA));
        onEvent(new AdEventImpl(AdEvent.AdEventType.ALL_ADS_COMPLETED, ad, EMPTY_AD_DATA));
      }

    }
  }



  private void markAdCompleted(Ad ad) {
    setAdState(ad, AdEvent.AdEventType.COMPLETED);
    onEvent(new AdEventImpl(AdEvent.AdEventType.COMPLETED, ad, EMPTY_AD_DATA));
    if (ad.getAdPodInfo().getAdPosition() == ad.getAdPodInfo().getTotalAds()) {
      onEvent(new AdEventImpl(AdEvent.AdEventType.ALL_ADS_COMPLETED, ad, EMPTY_AD_DATA));
      onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, ad, EMPTY_AD_DATA));
    }
  }

  @Override
  public void start() {
    scheduleUpdate(DELAY_MILLIS);

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
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player != null && (isCurrentState(currentAd, AdEvent.AdEventType.STARTED)
        || isCurrentState(currentAd, AdEvent.AdEventType.AD_PROGRESS)
        || isCurrentState(currentAd, AdEvent.AdEventType.RESUMED))){
      setAdState(currentAd, AdEvent.AdEventType.PAUSED);
      player.pauseAd();
      onEvent(new AdEventImpl(AdEvent.AdEventType.PAUSED, currentAd, EMPTY_AD_DATA));
    }
  }

  private void setAdState(Ad currentAd, AdEvent.AdEventType paused) {
    state.put(currentAd, paused);
  }

  @Override
  public void resume() {
    VideoAdPlayer player = adDisplayContainer.getPlayer();
    if (player != null && (isCurrentState(currentAd, AdEvent.AdEventType.PAUSED)
        || isCurrentState(currentAd, AdEvent.AdEventType.STARTED))){
      player.playAd();
      setAdState(currentAd, AdEvent.AdEventType.RESUMED);
      onEvent(new AdEventImpl(AdEvent.AdEventType.RESUMED, currentAd, EMPTY_AD_DATA));
    }
    scheduleUpdate(DELAY_MILLIS);
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
  public void init() {

  }

  @Override
  public void init(AdsRenderingSettings adsRenderingSettings) {
    if (DEBUG) {
      Log.d(TAG, " Got  AdsRenderingSettings "+ adsRenderingSettings.getPlayAdsAfterTime());
    }

    double playAdsAfterTime = adsRenderingSettings.getPlayAdsAfterTime();
    Iterator<AdGroup> iterator = ads.iterator();
    while (iterator.hasNext()){
      AdGroup next = iterator.next();
      if (next.getStartTime() != C.INDEX_UNSET && next.getStartTime() < playAdsAfterTime){
        iterator.remove();
      }
    }
    int podIndex = 1;
    for (AdGroup adGroup : ads) {
      float startTime = adGroup.getStartTime();
      AdPodInfoImpl adPodInfo = new AdPodInfoImpl();
      if (startTime == C.INDEX_UNSET){ // postroll
        adPodInfo.podIndex = POST_ROLL_POD_INDEX;
      }else if (startTime == 0L){ // preroll
        adPodInfo.podIndex = PRE_ROLL_POD_INDEX;
      }else { // midroll
        adPodInfo.podIndex = podIndex;
      }
      adPodInfo.timeOffset = startTime;
      adPodInfo.totalAds = adGroup.getAds().size();
      for (int position = 1; position <= adGroup.getAds().size() ; position++) {
        AdImpl ad = adGroup.getAds().get(position -1);
        ad.setAdPodInfo(new AdPodInfoImpl(adPodInfo, position));
      }
      if (adPodInfo.getPodIndex() >  0){
        podIndex++;
      }
    }
    long contentPosition;
    VideoProgressUpdate contentProgress = contentProgressProvider.getContentProgress();
    if (contentProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY){
      contentPosition = (long) (contentProgress.getCurrentTime()  * C.MILLIS_PER_SECOND);
    }else {
      contentPosition = (long) (adsRenderingSettings.getPlayAdsAfterTime() * C.MILLIS_PER_SECOND);
    }
    currentAd = processNextAd(contentPosition);
    if (currentAd == null){
      onEvent(new AdEventImpl(AdEvent.AdEventType.CONTENT_RESUME_REQUESTED, null, EMPTY_AD_DATA));
    }
    scheduleUpdate(4 * DELAY_MILLIS);
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
  public VideoProgressUpdate getAdProgress() {
    return lastAdProgress;
  }


  private Runnable updateRunnable = new Runnable() {
    @Override
    public void run() {
      VideoAdPlayer player = adDisplayContainer.getPlayer();
      if (player == null) return ;
      long playerPosition = -1;
      long delay = -1;
      VideoProgressUpdate adProgress = player.getAdProgress();
      if (currentAd != null && !lastAdProgress.equals(adProgress) && isCurrentAdDone(adProgress)){
        try {
          playerPosition = (long) (currentAd.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND);
          currentAd = stopAd(currentAd);
          if (currentAd != null){
            delay = 4 * DELAY_MILLIS;
          }
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
      if (contentProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY) {
        playerPosition = (long) (contentProgress.getCurrentTime() * C.MILLIS_PER_SECOND);
        if (currentAd == null) {
          long pendingContentMs =
                  (long) (contentProgress.getDuration() - contentProgress.getCurrentTime())
                          * C.MILLIS_PER_SECOND;
          if (pendingContentMs > 1000 && pendingContentMs < END_OF_CONTENT_POSITION_THRESHOLD_MS) {
            currentAd = processNextAd(C.TIME_END_OF_SOURCE);
          } else {
            currentAd = processNextAd(playerPosition);
          }
        }
      }
      if (currentAd != null){
        if (DEBUG) {
          Log.d(TAG, " Proceed for next ad  "+ currentAd.getAdId() + " playerPosition "+ playerPosition);
        }
        triggerEvent(currentAd);
      }
      if (delay == -1) delay = adProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY ? DELAY_MILLIS_AD_RUNNING : DELAY_MILLIS;
      scheduleUpdate(delay);
    }
  };

  private boolean isCurrentAdDone(VideoProgressUpdate current) {
   return current != VideoProgressUpdate.VIDEO_TIME_NOT_READY
          && ((current.getDuration() - current.getCurrentTime()) * C.MILLIS_PER_SECOND < END_OF_AD_POSITION_THRESHOLD_MS);
  }

  private void showAdView(VideoProgressUpdate adProgress){
    ViewGroup adContainer = adDisplayContainer.getAdContainer();
    Ad playingAd = currentAd;
    if (playingAd != null && adProgress != VideoProgressUpdate.VIDEO_TIME_NOT_READY){
      adContainer.setVisibility(View.VISIBLE);
      adProgressText.setText(formatAdProgress(currentAd.getAdPodInfo(), adProgress));
      if (playingAd.isSkippable() && adProgress.getDuration() > playingAd.getSkipTimeOffset()) {
        if (adProgress.getCurrentTime() >= playingAd.getSkipTimeOffset()) {
          // Allow skipping.
          skipButton.setText(context.getString(R.string.skip_ad));
          skipButton.setEnabled(true);
          skipButton.setTextSize(16);
        } else {
          String skipString = String.format(
              Locale.US, "You can skip this ad in %d",
              (int) (playingAd.getSkipTimeOffset() -
                  adProgress.getCurrentTime()));
          skipButton.setText(skipString);
          skipButton.setEnabled(false);
          skipButton.setTextSize(12);
        }
        skipButton.setVisibility(View.VISIBLE);
      } else {
        skipButton.setVisibility(View.INVISIBLE);
      }
      setAdState(playingAd, AdEvent.AdEventType.AD_PROGRESS);
      onEvent(new AdEventImpl(AdEvent.AdEventType.AD_PROGRESS, playingAd, EMPTY_AD_DATA));
    }else {
      adContainer.setVisibility(View.GONE);
    }
  }

  private SpannableString formatAdProgress(AdPodInfo podInfo , VideoProgressUpdate  update){
    String adProgress = DateTimeUtils.formatTime(update.getDuration() - update.getCurrentTime());
    String progress;
    if (podInfo.getTotalAds() > 1){
      progress = context.getString(R.string.txt_ad_progress, podInfo.getAdPosition(),
              podInfo.getTotalAds(), adProgress);
    }else {
      progress = context.getString(R.string.txt_ad_progress_without_group, adProgress);
    }
    String prefix = context.getString(R.string.ad_prefix);
    String dot = context.getString(R.string.dot_unicode_char);
    SpannableString spannableString = new SpannableString(prefix+dot+progress);
    spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.dot_color)), prefix.length(), prefix.length()+ dot.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
    return spannableString;
  }

  private void scheduleImmediate(){
    handler.removeCallbacks(updateRunnable);
    handler.post(updateRunnable);
  }
  private void scheduleUpdate(long delayTime){
    handler.removeCallbacks(updateRunnable);
    handler.postDelayed(updateRunnable, delayTime);
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
       handler.postDelayed(() -> {
         markAdCompleted(currentAd);
         long playerPosition = (long) (currentAd.getAdPodInfo().getTimeOffset() * C.MILLIS_PER_SECOND);
         currentAd = processNextAd(playerPosition);
         if (currentAd != null){
           triggerEvent(currentAd);
         }
       }, 100);

      }
    }

    @Override
    public void onBuffering() {

    }
  };

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.skipButton && currentAd != null){
      v.setVisibility(View.INVISIBLE);
      skipAd(currentAd);
    }
  }
}
