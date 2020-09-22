package com.mxplay.offlineads.exo;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.mxplay.offlineads.exo.oma.Ad;
import com.mxplay.offlineads.exo.oma.AdDisplayContainer;
import com.mxplay.offlineads.exo.oma.AdError;
import com.mxplay.offlineads.exo.oma.AdErrorEvent;
import com.mxplay.offlineads.exo.oma.AdEvent;
import com.mxplay.offlineads.exo.oma.AdPodInfo;
import com.mxplay.offlineads.exo.oma.AdsManager;
import com.mxplay.offlineads.exo.oma.AdsManagerLoadedEvent;
import com.mxplay.offlineads.exo.oma.AdsRenderingSettings;
import com.mxplay.offlineads.exo.oma.ContentProgressProvider;
import com.mxplay.offlineads.exo.oma.VideoAdPlayer;
import com.mxplay.offlineads.exo.oma.VideoProgressUpdate;
import com.mxplay.offlineads.exo.oma.internal.AdLoaderImpl;
import com.mxplay.offlineads.exo.oma.internal.AdsRequest;
import com.mxplay.offlineads.exo.tracking.IVideoAdTracker;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class OmaAdLoader
    implements Player.EventListener,
    AdsLoader,
    VideoAdPlayer,
    ContentProgressProvider,
    AdErrorEvent.AdErrorListener,
    com.mxplay.offlineads.exo.oma.AdsLoader.AdsLoadedListener,
    AdEvent.AdEventListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ima");
  }

  private IVideoAdTracker adTracker;
  private long preloadDuration = MAXIMUM_PRELOAD_DURATION_MS;
  private long adLoadedTime;
  private long startLoadMediaTime;
  private long startRequestTime = 0;
  private int lastAdGroupIndex = -1;
  private int lastRealStartTime = -1;
  private int lastPlayAdGroupIndex = -1;
  private int lastLoadMediaAdGroupIndex = -1;
  private int lastStartRequestAdGroupIndex = -1;

  /** Builder for {@link OmaAdLoader}. */
  public static final class Builder {

    private final Context context;


    @Nullable private AdEvent.AdEventListener adEventListener;
    private OmaFactory omaFactory;

    /**
     * Creates a new builder for {@link OmaAdLoader}.
     *
     * @param context The context;
     */
    public Builder(Context context) {
      this.context = Assertions.checkNotNull(context);
      omaFactory = new DefaultOmaFactory();
    }




    /**
     * Returns a new {@link OmaAdLoader} with the specified sideloaded ads response.
     *
     * @param adsResponse The sideloaded VAST, VMAP, or ad rules response to be used instead of
     *     making a request via an ad tag URL.
     * @return The new {@link OmaAdLoader}.
     */
    public OmaAdLoader buildForAdsResponse(String adsResponse) {
      return new OmaAdLoader(
          context,
          adsResponse,
          adEventListener,
          omaFactory);
    }
  }

  private static final boolean DEBUG = false;
  private static final String TAG = "OmaAdsLoader";

  /** The value used in {@link VideoProgressUpdate}s to indicate an unset duration. */
  private static final long IMA_DURATION_UNSET = -1L;

  /**
   * Threshold before the end of content at which IMA is notified that content is complete if the
   * player buffers, in milliseconds.
   */
  private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;

  /** The maximum duration before an ad break that IMA may start preloading the next ad. */
  private static final long MAXIMUM_PRELOAD_DURATION_MS = 8000;


  /** The state of ad playback. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED})
  private @interface ImaAdState {}
  /**
   * The ad playback state when IMA is not playing an ad.
   */
  private static final int IMA_AD_STATE_NONE = 0;
  /**
   * The ad playback state when IMA has called {@link #playAd()} and not {@link #pauseAd()}.
   */
  private static final int IMA_AD_STATE_PLAYING = 1;
  /**
   * The ad playback state when IMA has called {@link #pauseAd()} while playing an ad.
   */
  private static final int IMA_AD_STATE_PAUSED = 2;

  @Nullable private final String adsResponse;
  @Nullable private AdEvent.AdEventListener adEventListener;
  private final OmaFactory omaFactory;
  private final Timeline.Period period;
  private final List<VideoAdPlayerCallback> adCallbacks;
  private final AdDisplayContainer adDisplayContainer;
  private final com.mxplay.offlineads.exo.oma.AdsLoader adsLoader;

  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  private Object pendingAdRequestContext;
  @Nullable private EventListener eventListener;
  @Nullable private Player player;
  private VideoProgressUpdate lastContentProgress;
  private VideoProgressUpdate lastAdProgress;

  private AdsManager adsManager;
  private boolean initializedAdsManager;
  private AdsMediaSource.AdLoadException pendingAdLoadError;
  private Timeline timeline;
  private long contentDurationMs;
  private int podIndexOffset;
  private AdPlaybackState adPlaybackState;


  public void setAdTracker(IVideoAdTracker adTracker) {
    this.adTracker = adTracker;
  }

  public void setPreloadDuration(long preloadDurationMs) {
    if (preloadDurationMs < 0) {
      return;
    }
    this.preloadDuration = preloadDurationMs;
  }
  // Fields tracking IMA's state.

  /** The expected ad group index that IMA should load next. */
  private int expectedAdGroupIndex;
  /** The index of the current ad group that IMA is loading. */
  private int adGroupIndex;
  /** Whether IMA has sent an ad event to pause content since the last resume content event. */
  private boolean imaPausedContent;
  /** The current ad playback state. */
  private @ImaAdState int imaAdState;

  private boolean sentContentComplete;

  // Fields tracking the player/loader state.

  /** Whether the player is playing an ad. */
  private boolean playingAd;
  /**
   * If the player is playing an ad, stores the ad index in its ad group. {@link C#INDEX_UNSET}
   * otherwise.
   */
  private int playingAdIndexInAdGroup;
  /**
   * Whether there's a pending ad preparation error which IMA needs to be notified of when it
   * transitions from playing content to playing the ad.
   */
  private boolean shouldNotifyAdPrepareError;
  /**
   * If a content period has finished but IMA has not yet called {@link #playAd()}, stores the value
   * of {@link SystemClock#elapsedRealtime()} when the content stopped playing. This can be used to
   * determine a fake, increasing content position. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressElapsedRealtimeMs;
  /**
   * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
   * content progress should increase. {@link C#TIME_UNSET} otherwise.
   */
  private long fakeContentProgressOffsetMs;
  /** Stores the pending content position when a seek operation was intercepted to play an ad. */
  private long pendingContentPositionMs;
  /** Whether {@link #getContentProgress()} has sent {@link #pendingContentPositionMs} to IMA. */
  private boolean sentPendingContentPositionMs;



  private OmaAdLoader(
      Context context,
      @Nullable String adsResponse,
      @Nullable AdEvent.AdEventListener adEventListener,
      OmaFactory omaFactory) {
    Assertions.checkArgument(adsResponse != null);
    this.adsResponse = adsResponse;
    this.adEventListener = adEventListener;
    this.omaFactory = omaFactory;
    period = new Timeline.Period();
    adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
    adDisplayContainer = omaFactory.createAdDisplayContainer();
    adDisplayContainer.setPlayer(/* videoAdPlayer= */ this);
    adsLoader = omaFactory.createAdsLoader(context, adDisplayContainer);
    adsLoader.addAdErrorListener(/* adErrorListener= */ this);
    adsLoader.addAdsLoadedListener(/* adsLoadedListener= */ this);
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
    fakeContentProgressOffsetMs = C.TIME_UNSET;
    pendingContentPositionMs = C.TIME_UNSET;
    adGroupIndex = C.INDEX_UNSET;
    contentDurationMs = C.TIME_UNSET;
    timeline = Timeline.EMPTY;
  }

  /**
   * Returns the underlying {@code com.google.ads.interactivemedia.v3.api.AdsLoader} wrapped by
   * this instance.
   */
  public com.mxplay.offlineads.exo.oma.AdsLoader getAdsLoader() {
    return adsLoader;
  }

  /**
   * Returns the {@link AdDisplayContainer} used by this loader.
   *
   * <p>Note: any video controls overlays registered via
   * the media source detaches from this instance. It is therefore necessary to re-register views
   * each time the ads loader is reused. Alternatively, provide overlay views via the {@link
   * AdsLoader.AdViewProvider} when creating the media source to benefit from automatic
   * registration.
   */
  public AdDisplayContainer getAdDisplayContainer() {
    return adDisplayContainer;
  }


  /**
   * Requests ads, if they have not already been requested. Must be called on the main thread.
   *
   * <p>Ads will be requested automatically when the player is prepared if this method has not been
   * called, so it is only necessary to call this method if you want to request ads before preparing
   * the player.
   *
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI.
   */
  public void requestAds(ViewGroup adViewGroup) {
    if (adPlaybackState != null || adsManager != null || pendingAdRequestContext != null) {
      // Ads have already been requested.
      return;
    }
    adDisplayContainer.setAdContainer(adViewGroup);
    pendingAdRequestContext = new Object();
    AdsRequest  request = omaFactory.createAdsRequest();
    request.setAdsResponse(adsResponse);
    request.setContentProgressProvider(this);
    request.setUserRequestContext(pendingAdRequestContext);
    adsLoader.requestAds(request);
    updateStartRequestTime(false);
  }

  // AdsLoader implementation.

  @Override
  public void setPlayer(@Nullable Player player) {
    Assertions.checkState(Looper.getMainLooper() == Looper.myLooper());
    Assertions.checkState(
        player == null || player.getApplicationLooper() == Looper.getMainLooper());
    nextPlayer = player;
    wasSetPlayerCalled = true;
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
  }

  @Override
  public void start(EventListener eventListener, AdViewProvider adViewProvider) {
    Assertions.checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    player = nextPlayer;
    if (player == null) {
      return;
    }
    updateStartRequestTime(false);
    this.eventListener = eventListener;
    lastAdProgress = null;
    lastContentProgress = null;
    ViewGroup adViewGroup = adViewProvider.getAdViewGroup();
    adDisplayContainer.setAdContainer(adViewGroup);
    player.addListener(this);
    maybeNotifyPendingAdLoadError();
    if (adsManager != null){
      adsManager.resume();
    }
    if (adPlaybackState != null) {
      // Pass the ad playback state to the player, and resume ads if necessary.
      eventListener.onAdPlaybackState(adPlaybackState);
    } else if (adsManager != null) {
      adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
      updateAdPlaybackState();
    } else {
      // Ads haven't loaded yet, so request them.
      requestAds(adViewGroup);
    }
  }

  private void updateStartRequestTime(boolean force) {
    int adGroupIndex = getAdGroupIndex();
    if (lastStartRequestAdGroupIndex != adGroupIndex && (adGroupIndex != C.INDEX_UNSET || force)) {
      lastStartRequestAdGroupIndex = getAdGroupIndex();
      startRequestTime = System.currentTimeMillis();
    }
  }

  @Override
  public void stop() {
    if (player == null) {
      return;
    }
    if (adsManager != null) {
      if (imaPausedContent) {
        adPlaybackState =
            adPlaybackState.withAdResumePositionUs(
                playingAd ? C.msToUs(player.getCurrentPosition()) : 0);
      }
      adsManager.pause();
    }
    lastAdProgress = getAdProgress();
    lastContentProgress = getContentProgress();
    player.removeListener(this);
    player = null;
    eventListener = null;
  }

  @Override
  public void release() {
    pendingAdRequestContext = null;
    if (adsManager != null) {
      adsManager.removeAdErrorListener(this);
      adsManager.removeAdEventListener(this);
      if (adEventListener != null) {
        adsManager.removeAdEventListener(adEventListener);
      }
      adsManager.destroy();
      adsManager = null;
    }
    adsLoader.removeAdsLoadedListener(/* adsLoadedListener= */ this);
    adsLoader.removeAdErrorListener(/* adErrorListener= */ this);
    imaPausedContent = false;
    imaAdState = IMA_AD_STATE_NONE;
    pendingAdLoadError = null;
    adPlaybackState = AdPlaybackState.NONE;
    updateAdPlaybackState();
    adEventListener = null;
    adsLoader.cancelAdRequest();
  }

  @Override
  public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
    if (player == null) {
      return;
    }
    try {
      handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception);
    } catch (Exception e) {
      maybeNotifyInternalError("handlePrepareError", e);
    }
  }

  // com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener implementation.

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
    if (!Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.getUserRequestContext())) {
      adsManager.destroy();
      return;
    }
    pendingAdRequestContext = null;
    this.adsManager = adsManager;
    adsManager.addAdErrorListener(this);
    adsManager.addAdEventListener(this);
    if (adEventListener != null) {
      adsManager.addAdEventListener(adEventListener);
    }
    if (player != null) {
      // If a player is attached already, start playback immediately.
      try {
        adPlaybackState = new AdPlaybackState(getAdGroupTimesUs(adsManager.getAdCuePoints()));
        updateAdPlaybackState();
      } catch (Exception e) {
        maybeNotifyInternalError("onAdsManagerLoaded", e);
      }
    }
  }

  // AdEvent.AdEventListener implementation.

  @Override
  public void onAdEvent(AdEvent adEvent) {
    AdEvent.AdEventType adEventType = adEvent.getType();
    if (DEBUG) {
      Log.d(TAG, "onAdEvent: " + adEventType);
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring AdEvent after release: " + adEvent);
      return;
    }
    try {
      handleAdEvent(adEvent);
    } catch (Exception e) {
      maybeNotifyInternalError("onAdEvent", e);
    }
  }

  // AdErrorEvent.AdErrorListener implementation.

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    AdError error = adErrorEvent.getError();
    if (DEBUG) {
      Log.d(TAG, "onAdError", error);
    }
    if (adsManager == null) {
      // No ads were loaded, so allow playback to start without any ads.
      pendingAdRequestContext = null;
      adPlaybackState = new AdPlaybackState();
      updateAdPlaybackState();
      if (adTracker != null) {
        adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(adGroupIndex, startRequestTime, error, getAdGroupCount()));
      }
    } else if (isAdGroupLoadError(error)) {
      try {
        handleAdGroupLoadError(error);
      } catch (Exception e) {
        maybeNotifyInternalError("onAdError", e);
      }
    }
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdsMediaSource.AdLoadException.createForAllAds(error);
    }
    maybeNotifyPendingAdLoadError();
  }

  private long getAdPositionInSec(int adGroupIndex) {
    if(getAdGroupCount() > 0){
      long[] adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
      if(adGroupIndex >= 0 && adGroupIndex < adGroupTimesUs.length){
        long adGroupTime = adGroupTimesUs[adGroupIndex];
        if(adGroupTime == C.TIME_END_OF_SOURCE){
          return adGroupTime;
        }
        return adGroupTime/C.MICROS_PER_SECOND;
      }
    }
    return -1;
  }

  private int getAdGroupIndex () {
    return this.adGroupIndex == C.INDEX_UNSET ? expectedAdGroupIndex : this.adGroupIndex;
  }

  // ContentProgressProvider implementation.

  @Override
  public VideoProgressUpdate getContentProgress() {
    if (player == null) {
      return lastContentProgress;
    }
    boolean hasContentDuration = contentDurationMs != C.TIME_UNSET;
    long contentPositionMs;
    if (pendingContentPositionMs != C.TIME_UNSET && !sentPendingContentPositionMs) {
      sentPendingContentPositionMs = true;
      contentPositionMs = pendingContentPositionMs;
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
      long elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs;
      contentPositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs;
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    } else if (imaAdState == IMA_AD_STATE_NONE && !playingAd && hasContentDuration) {
      contentPositionMs = player.getCurrentPosition();
      // Update the expected ad group index for the current content position. The update is delayed
      // until MAXIMUM_PRELOAD_DURATION_MS before the ad so that an ad group load error delivered
      // just after an ad group isn't incorrectly attributed to the next ad group.
      int nextAdGroupIndex =
          adPlaybackState.getAdGroupIndexAfterPositionUs(
              C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
      if (nextAdGroupIndex != expectedAdGroupIndex && nextAdGroupIndex != C.INDEX_UNSET) {
        long nextAdGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[nextAdGroupIndex]);
        if (nextAdGroupTimeMs == C.TIME_END_OF_SOURCE) {
          nextAdGroupTimeMs = contentDurationMs;
        }
        if (nextAdGroupTimeMs - contentPositionMs < preloadDuration) {
          expectedAdGroupIndex = nextAdGroupIndex;
        }
      }
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
    long contentDurationMs = hasContentDuration ? this.contentDurationMs : IMA_DURATION_UNSET;
    tryUpdateStartRequestTime(contentPositionMs, contentDurationMs);
    return new VideoProgressUpdate(contentPositionMs, contentDurationMs);
  }

  private void tryUpdateStartRequestTime(long contentPositionMs, long contentDurationMs) {
    if (contentPositionMs < 0 || contentDurationMs < 0) {
      return;
    }
    if (adsManager == null) {
      return;
    }
    List<Float> cuePoints = adsManager.getAdCuePoints();
    if (cuePoints == null || cuePoints.isEmpty()) {
      return;
    }
    long contentPosition = TimeUnit.MILLISECONDS.toSeconds(contentPositionMs);
    for (Float startTime : cuePoints) {
      if (TimeUnit.MILLISECONDS.toSeconds(contentPositionMs) > startTime) {
        continue;
      }
      int realStartTime;
      if (startTime == -1.0) {
        realStartTime = (int) (contentPosition - 4);
      } else {
        realStartTime = (int) (startTime - 4);
      }
      if (realStartTime == contentPosition && lastRealStartTime != realStartTime) {
        lastRealStartTime = realStartTime;
        updateStartRequestTime(true);
        return;
      }
    }
  }

  // VideoAdPlayer implementation.

  @Override
  public VideoProgressUpdate getAdProgress() {
    if (player == null) {
      return lastAdProgress;
    } else if (imaAdState != IMA_AD_STATE_NONE && playingAd) {
      long adDuration = player.getDuration();
      return adDuration == C.TIME_UNSET ? VideoProgressUpdate.VIDEO_TIME_NOT_READY
          : new VideoProgressUpdate(player.getCurrentPosition(), adDuration);
    } else {
      return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
    }
  }

  @Override
  public void loadAd(String adUriString) {
    try {
      if (DEBUG) {
        Log.d(TAG, "loadAd in ad group " + adGroupIndex +" content "+ getContentProgress().getCurrentTime());
      }
      if (adsManager == null) {
        Log.w(TAG, "Ignoring loadAd after release");
        return;
      }
      if (lastLoadMediaAdGroupIndex != getAdGroupIndex()) {
        lastLoadMediaAdGroupIndex = getAdGroupIndex();
        startLoadMediaTime = System.currentTimeMillis();
      }
      if (adGroupIndex == C.INDEX_UNSET) {
        Log.w(
            TAG,
            "Unexpected loadAd without LOADED event; assuming ad group index is actually "
                + expectedAdGroupIndex);
        adGroupIndex = expectedAdGroupIndex;
        adsManager.start();
      }
      int adIndexInAdGroup = getAdIndexInAdGroupToLoad(adGroupIndex);
      if (adIndexInAdGroup == C.INDEX_UNSET) {
        Log.w(TAG, "Unexpected loadAd in an ad group with no remaining unavailable ads "+adGroupIndex);
        return;
      }
      adPlaybackState =
          adPlaybackState.withAdUri(adGroupIndex, adIndexInAdGroup, Uri.parse(adUriString));
      updateAdPlaybackState();
    } catch (Exception e) {
      maybeNotifyInternalError("loadAd", e);
    }
  }

  @Override
  public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.add(videoAdPlayerCallback);
  }

  @Override
  public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
    adCallbacks.remove(videoAdPlayerCallback);
  }

  @Override
  public void playAd() {
    if (DEBUG) {
      Log.d(TAG, "playAd "+ getContentProgress().getCurrentTime());
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring playAd after release");
      return;
    }
    switch (imaAdState) {
      case IMA_AD_STATE_PLAYING:
        // IMA does not always call stopAd before resuming content.
        // See [Internal: b/38354028, b/63320878].
        Log.w(TAG, "Unexpected playAd without stopAd");
        break;
      case IMA_AD_STATE_NONE:
        // IMA is requesting to play the ad, so stop faking the content position.
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
        fakeContentProgressOffsetMs = C.TIME_UNSET;
        imaAdState = IMA_AD_STATE_PLAYING;
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onPlay();
        }
        if (shouldNotifyAdPrepareError) {
          shouldNotifyAdPrepareError = false;
          for (int i = 0; i < adCallbacks.size(); i++) {
            adCallbacks.get(i).onError();
          }
        }
        break;
      case IMA_AD_STATE_PAUSED:
        imaAdState = IMA_AD_STATE_PLAYING;
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onResume();
        }
        break;
      default:
        throw new IllegalStateException();
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected playAd while detached");
    } else if (!player.getPlayWhenReady()) {
      adsManager.pause();
    }
    if (lastPlayAdGroupIndex != getAdGroupIndex()) {
      lastPlayAdGroupIndex = getAdGroupIndex();
      if (adTracker != null) {
        adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_SUCCESS, IVideoAdTracker.buildSuccessParams(adLoadedTime, startRequestTime, startLoadMediaTime, adGroupIndex, getAdGroupCount()));
      }
    }
  }

  @Override
  public void stopAd() {
    if (DEBUG) {
      Log.d(TAG, "stopAd contentProgress "+ getContentProgress() + " Ad progress "+ adsManager.getCurrentAd().getAdId()+" " +adsManager.getAdProgress().toString());
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring stopAd after release");
      return;
    }
    if (player == null) {
      // Sometimes messages from IMA arrive after detaching the player. See [Internal: b/63801642].
      Log.w(TAG, "Unexpected stopAd while detached");
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      Log.w(TAG, "Unexpected stopAd");
      return;
    }
    try {
      stopAdInternal();
    } catch (Exception e) {
      maybeNotifyInternalError("stopAd", e);
    }
  }

  @Override
  public void pauseAd() {
    if (DEBUG) {
      Log.d(TAG, "pauseAd");
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // This method is called after content is resumed.
      return;
    }
    imaAdState = IMA_AD_STATE_PAUSED;
    for (int i = 0; i < adCallbacks.size(); i++) {
      adCallbacks.get(i).onPause();
    }
  }

  @Override
  public void resumeAd() {
    // This method is never called. See [Internal: b/18931719].
    maybeNotifyInternalError("resumeAd", new IllegalStateException("Unexpected call to resumeAd"));
  }

  // Player.EventListener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    Assertions.checkArgument(timeline.getPeriodCount() == 1);
    this.timeline = timeline;
    long contentDurationUs = timeline.getPeriod(0, period).durationUs;
    contentDurationMs = C.usToMs(contentDurationUs);
    if (contentDurationUs != C.TIME_UNSET) {
      adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
    }
    if (!initializedAdsManager && adsManager != null) {
      initializedAdsManager = true;
      initializeAdsManager();
    }
    onPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
    if (adsManager == null) {
      return;
    }

    if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
      adsManager.pause();
      return;
    }

    if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
      adsManager.resume();
      return;
    }

    if (imaAdState == IMA_AD_STATE_NONE && playbackState == Player.STATE_BUFFERING
        && playWhenReady) {
      checkForContentComplete();
    } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onEnded();
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlayerStateChanged");
      }
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    if (imaAdState != IMA_AD_STATE_NONE) {
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError();
      }
    }
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    if (adsManager == null) {
      return;
    }
    if (!playingAd && !player.isPlayingAd()) {
      checkForContentComplete();
      if (sentContentComplete) {
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
          if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
          }
        }
        updateAdPlaybackState();
      } else if (!timeline.isEmpty()) {
        long positionMs = player.getCurrentPosition();
        timeline.getPeriod(0, period);
        int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
        if (newAdGroupIndex != C.INDEX_UNSET) {
          sentPendingContentPositionMs = false;
          pendingContentPositionMs = positionMs;
          if (newAdGroupIndex != adGroupIndex) {
            shouldNotifyAdPrepareError = false;
          }
        }
      }
    }

    updateImaStateForPlayerState();
  }

  // Internal methods.

  private void initializeAdsManager() {
    AdsRenderingSettings adsRenderingSettings = new AdsRenderingSettings();
    // Skip ads based on the start position as required.
    long[] adGroupTimesUs = getAdGroupTimesUs(adsManager.getAdCuePoints());
    long contentPositionMs = player.getContentPosition();
    int adGroupIndexForPosition =
        adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs));
    if (adGroupIndexForPosition > 0 && adGroupIndexForPosition != C.INDEX_UNSET) {
      // Skip any ad groups before the one at or immediately before the playback position.
      for (int i = 0; i < adGroupIndexForPosition; i++) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
      }
      // Play ads after the midpoint between the ad to play and the one before it, to avoid issues
      // with rounding one of the two ad times.
      long adGroupForPositionTimeUs = adGroupTimesUs[adGroupIndexForPosition];
      long adGroupBeforeTimeUs = adGroupTimesUs[adGroupIndexForPosition - 1];
      double midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforeTimeUs) / 2d;
      adsRenderingSettings.setPlayAdsAfterTime(midpointTimeUs / C.MICROS_PER_SECOND);
    }

    // IMA indexes any remaining midroll ad pods from 1. A preroll (if present) has index 0.
    // Store an index offset as we want to index all ads (including skipped ones) from 0.
    if (adGroupIndexForPosition == 0 && adGroupTimesUs[0] == 0) {
      // We are playing a preroll.
      podIndexOffset = 0;
    } else if (adGroupIndexForPosition == C.INDEX_UNSET) {
      // There's no ad to play which means there's no preroll.
      podIndexOffset = -1;
    } else {
      // We are playing a midroll and any ads before it were skipped.
      podIndexOffset = adGroupIndexForPosition - 1;
    }

    if (adGroupIndexForPosition != C.INDEX_UNSET && hasMidrollAdGroups(adGroupTimesUs)) {
      // Provide the player's initial position to trigger loading and playing the ad.
      pendingContentPositionMs = contentPositionMs;
    }

    adsManager.init(adsRenderingSettings);
    updateAdPlaybackState();
    if (DEBUG) {
      Log.d(TAG, "Initialized with ads rendering settings: " + adsRenderingSettings);
    }
  }

  private void handleAdEvent(AdEvent adEvent) {
    Ad ad = adEvent.getAd();
    switch (adEvent.getType()) {
      case LOADED:
        // The ad position is not always accurate when using preloading. See [Internal: b/62613240].
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        int podIndex = adPodInfo.getPodIndex();
        adGroupIndex =
            podIndex == -1 ? (adPlaybackState.adGroupCount - 1) : (podIndex + podIndexOffset);
        int adPosition = adPodInfo.getAdPosition();
        int adCount = adPodInfo.getTotalAds();
        if (lastAdGroupIndex != getAdGroupIndex()) {
          lastAdGroupIndex = getAdGroupIndex();
          adLoadedTime = System.currentTimeMillis();
        }
        adsManager.start();
        if (DEBUG) {
          Log.d(TAG, "Loaded ad Pod " + adPodInfo.toString());
          Log.d(TAG, "Loaded ad **  " + ad.toString());
          Log.d(TAG, "Loaded ad " + adPosition + " of " + adCount + " in group " + adGroupIndex);
        }

        int oldAdCount = adPlaybackState.adGroups[adGroupIndex].count;
        if (adCount != oldAdCount) {
          if (oldAdCount == C.LENGTH_UNSET) {
            adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, adCount);
            updateAdPlaybackState();
          } else {
            // IMA sometimes unexpectedly decreases the ad count in an ad group.
            Log.w(TAG, "Unexpected ad count in LOADED, " + adCount + ", expected " + oldAdCount);
          }
        }
        if (adGroupIndex != expectedAdGroupIndex) {
          Log.w(
              TAG,
              "Expected ad group index "
                  + expectedAdGroupIndex
                  + ", actual ad group index "
                  + adGroupIndex);
          expectedAdGroupIndex = adGroupIndex;
        }
        break;
      case CONTENT_PAUSE_REQUESTED:
        // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
        // before sending CONTENT_RESUME_REQUESTED.
        imaPausedContent = true;
        pauseContentInternal();
        break;
      case TAPPED:
        if (eventListener != null) {
          eventListener.onAdTapped();
        }
        break;
      case CLICKED:
        if (eventListener != null) {
          eventListener.onAdClicked();
        }
        break;
      case CONTENT_RESUME_REQUESTED:
        imaPausedContent = false;
        resumeContentInternal();
        break;
      case LOG:
        Map<String, String> adData = adEvent.getAdData();
        String message = "AdEvent: " + adData;
        Log.i(TAG, message);
        if ("adLoadError".equals(adData.get("type"))) {
          Exception e = new IOException(message);
          handleAdGroupLoadError(e);
        }
        break;
      case STARTED:
      case ALL_ADS_COMPLETED:
      default:
        break;
    }
  }

  private void updateImaStateForPlayerState() {
    boolean wasPlayingAd = playingAd;
    int oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup;
    playingAd = player.isPlayingAd();
    playingAdIndexInAdGroup = playingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
    boolean adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup;
    if (adFinished) {
      // IMA is waiting for the ad playback to finish so invoke the callback now.
      // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onEnded();
      }
      if (DEBUG) {
        Log.d(TAG, "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity");
      }
    }
    if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
      int adGroupIndex = player.getCurrentAdGroupIndex();
      // IMA hasn't called playAd yet, so fake the content position.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
    }
  }

  private void resumeContentInternal() {
    if (imaAdState != IMA_AD_STATE_NONE) {
      imaAdState = IMA_AD_STATE_NONE;
      if (DEBUG) {
        Log.d(TAG, "Unexpected CONTENT_RESUME_REQUESTED without stopAd");
      }
    }
    if (adGroupIndex != C.INDEX_UNSET) {
      adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex);
      adGroupIndex = C.INDEX_UNSET;
      updateAdPlaybackState();
    }
  }

  private void pauseContentInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    if (sentPendingContentPositionMs) {
      pendingContentPositionMs = C.TIME_UNSET;
      sentPendingContentPositionMs = false;
    }
  }

  private void stopAdInternal() {
    imaAdState = IMA_AD_STATE_NONE;
    int adIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
    // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
    adPlaybackState =
        adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0);
    updateAdPlaybackState();
    if (!playingAd) {
      adGroupIndex = C.INDEX_UNSET;
    }
  }

  private void handleAdGroupLoadError(Exception error) {
    int adGroupIndex =
        this.adGroupIndex == C.INDEX_UNSET ? expectedAdGroupIndex : this.adGroupIndex;
    if (adGroupIndex == C.INDEX_UNSET) {
      // Drop the error, as we don't know which ad group it relates to.
      return;
    }
    if (adTracker != null) {
      adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(adGroupIndex, startRequestTime, error, getAdGroupCount()));
    }
    AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
    if (adGroup.count == C.LENGTH_UNSET) {
      adPlaybackState =
          adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.length));
      adGroup = adPlaybackState.adGroups[adGroupIndex];
    }
    for (int i = 0; i < adGroup.count; i++) {
      if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
        if (DEBUG) {
          Log.d(TAG, "Removing ad " + i + " in ad group " + adGroupIndex);
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i);
      }
    }
    updateAdPlaybackState();
    if (pendingAdLoadError == null) {
      pendingAdLoadError = AdsMediaSource.AdLoadException.createForAdGroup(error, adGroupIndex);
    }
    pendingContentPositionMs = C.TIME_UNSET;
    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
  }

  private void handleAdPrepareError(int adGroupIndex, int adIndexInAdGroup, Exception exception) {
    if (DEBUG) {
      Log.d(
          TAG, "Prepare error for ad " + adIndexInAdGroup + " in group " + adGroupIndex, exception);
    }
    if (adsManager == null) {
      Log.w(TAG, "Ignoring ad prepare error after release");
      return;
    }
    if (imaAdState == IMA_AD_STATE_NONE) {
      // Send IMA a content position at the ad group so that it will try to play it, at which point
      // we can notify that it failed to load.
      fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime();
      fakeContentProgressOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
      if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
        fakeContentProgressOffsetMs = contentDurationMs;
      }
      shouldNotifyAdPrepareError = true;
    } else {
      // We're already playing an ad.
      if (adIndexInAdGroup > playingAdIndexInAdGroup) {
        // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
        // which means that the ad after will load (if any).
        for (int i = 0; i < adCallbacks.size(); i++) {
          adCallbacks.get(i).onEnded();
        }
      }
      playingAdIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
      for (int i = 0; i < adCallbacks.size(); i++) {
        adCallbacks.get(i).onError();
      }
    }
    adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
    updateAdPlaybackState();
    if (adTracker != null) {
      adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(adGroupIndex, adIndexInAdGroup, startRequestTime, "Prepare Error", exception, getAdGroupCount()));
    }
  }

  private int getAdGroupCount() {
    return adsManager != null && adsManager.getAdCuePoints() != null ? adsManager.getAdCuePoints().size() : -1;
  }

  private void checkForContentComplete() {
    if (contentDurationMs != C.TIME_UNSET && pendingContentPositionMs == C.TIME_UNSET
        && player.getContentPosition() + END_OF_CONTENT_POSITION_THRESHOLD_MS >= contentDurationMs
        && !sentContentComplete) {
      adsLoader.contentComplete();
      if (DEBUG) {
        Log.d(TAG, "adsLoader.contentComplete");
      }
      sentContentComplete = true;
      // After sending content complete IMA will not poll the content position, so set the expected
      // ad group index.
      expectedAdGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(C.msToUs(contentDurationMs));
    }
  }

  private void updateAdPlaybackState() {
    // Ignore updates while detached. When a player is attached it will receive the latest state.
    if (eventListener != null) {
      eventListener.onAdPlaybackState(adPlaybackState);
    }
  }

  /**
   * Returns the next ad index in the specified ad group to load, or {@link C#INDEX_UNSET} if all
   * ads in the ad group have loaded.
   */
  private int getAdIndexInAdGroupToLoad(int adGroupIndex) {
    @AdPlaybackState.AdState int[] states = adPlaybackState.adGroups[adGroupIndex].states;
    int adIndexInAdGroup = 0;
    // IMA loads ads in order.
    while (adIndexInAdGroup < states.length
        && states[adIndexInAdGroup] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
      adIndexInAdGroup++;
    }
    return adIndexInAdGroup == states.length ? C.INDEX_UNSET : adIndexInAdGroup;
  }

  private void maybeNotifyPendingAdLoadError() {
    if (pendingAdLoadError != null && eventListener != null) {
      eventListener.onAdLoadError(pendingAdLoadError, new DataSpec(Uri.parse("VAST_RESPONSE")));
      pendingAdLoadError = null;
    }
  }

  private void maybeNotifyInternalError(String name, Exception cause) {
    String message = "Internal error in " + name;
    Log.e(TAG, message, cause);
    // We can't recover from an unexpected error in general, so skip all remaining ads.
    if (adPlaybackState == null) {
      adPlaybackState = AdPlaybackState.NONE;
    } else {
      for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
        adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
      }
    }
    updateAdPlaybackState();
    if (eventListener != null) {
      eventListener.onAdLoadError(
          AdsMediaSource.AdLoadException.createForUnexpected(new RuntimeException(message, cause)),
          new DataSpec(Uri.parse("VAST_RESPONSE")));
    }
    if (adTracker != null) {
      adTracker.trackEvent(IVideoAdTracker.EVENT_INTERNAL_ERROR, IVideoAdTracker.buildFailedParams(adGroupIndex, -1, startRequestTime, name, cause, getAdGroupCount()));
    }
  }

  private static long[] getAdGroupTimesUs(List<Float> cuePoints) {
    if (cuePoints.isEmpty()) {
      // If no cue points are specified, there is a preroll ad.
      return new long[] {0};
    }

    int count = cuePoints.size();
    long[] adGroupTimesUs = new long[count];
    int adGroupIndex = 0;
    for (int i = 0; i < count; i++) {
      double cuePoint = cuePoints.get(i);
      if (cuePoint == -1.0) {
        adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE;
      } else {
        adGroupTimesUs[adGroupIndex++] = (long) (C.MICROS_PER_SECOND * cuePoint);
      }
    }
    // Cue points may be out of order, so sort them.
    Arrays.sort(adGroupTimesUs, 0, adGroupIndex);
    return adGroupTimesUs;
  }

  private static boolean isAdGroupLoadError(AdError adError) {
    // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
    // a single ad, ad group or the whole timeline.
    return adError.getErrorCode() == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
        || adError.getErrorCode() == AdError.AdErrorCode.UNKNOWN_ERROR;
  }

  private static boolean hasMidrollAdGroups(long[] adGroupTimesUs) {
    int count = adGroupTimesUs.length;
    if (count == 1) {
      return adGroupTimesUs[0] != 0 && adGroupTimesUs[0] != C.TIME_END_OF_SOURCE;
    } else if (count == 2) {
      return adGroupTimesUs[0] != 0 || adGroupTimesUs[1] != C.TIME_END_OF_SOURCE;
    } else {
      // There's at least one midroll ad group, as adGroupTimesUs is never empty.
      return true;
    }
  }

  /** Factory for objects provided by the IMA SDK. */
  interface OmaFactory {

    AdDisplayContainer createAdDisplayContainer();

    AdsRequest createAdsRequest();

    com.mxplay.offlineads.exo.oma.AdsLoader createAdsLoader(
        Context context, AdDisplayContainer adDisplayContainer);
  }

  private static final class DefaultOmaFactory implements OmaFactory {

    @Override
    public AdDisplayContainer createAdDisplayContainer() {
      return new AdDisplayContainer();
    }

    @Override
    public AdsRequest createAdsRequest() {
      return new AdsRequest();
    }

    @Override
    public com.mxplay.offlineads.exo.oma.AdsLoader createAdsLoader(
        Context context, AdDisplayContainer adDisplayContainer) {
      return new AdLoaderImpl(context, adDisplayContainer);
    }
  }
}
