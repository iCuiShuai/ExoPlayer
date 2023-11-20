/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mxplay.mediaads.exo;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.mxplay.mediaads.exo.OmaUtil.getImaLooper;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.mxplay.interactivemedia.api.AdDisplayContainer;
import com.mxplay.interactivemedia.api.AdsRenderingSettings;
import com.mxplay.interactivemedia.api.AdsRequest;
import com.mxplay.interactivemedia.api.FriendlyObstruction;
import com.mxplay.interactivemedia.api.FriendlyObstructionPurpose;
import com.mxplay.interactivemedia.api.OmaSdkFactory;
import com.mxplay.interactivemedia.api.player.VideoAdPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * {@link AdsLoader} using the IMA SDK. All methods must be called on the main thread.
 *
 * <p>The player instance that will play the loaded ads must be set before playback using {@link
 * #setPlayer(Player)}. If the ads loader is no longer required, it must be released by calling
 * {@link #release()}.
 *
 * <p>See https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
 * information on compatible ad tag formats. Pass the ad tag URI when setting media item playback
 * properties (if using the media item API) or as a {@link DataSpec} when constructing the {@link
 * AdsMediaSource} (if using media sources directly). For the latter case, please note that this
 * implementation delegates loading of the data spec to the IMA SDK, so range and headers
 * specifications will be ignored in ad tag URIs. Literal ads responses can be encoded as data
 * scheme data specs, for example, by constructing the data spec using a URI generated via {@link
 * Util#getDataUriForString(String, String)}.
 *
 * <p>The IMA SDK can report obstructions to the ad view for accurate viewability measurement. This
 * means that any overlay views that obstruct the ad overlay but are essential for playback need to
 * be registered via the {@link AdViewProvider} passed to the {@link AdsMediaSource}. See the <a
 * href="https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/omsdk">IMA
 * SDK Open Measurement documentation</a> for more information.
 */
public final class MxMediaAdLoader implements Player.EventListener, AdsLoader {

  private final Configuration configuration;
  private final Context context;
  private final OmaUtil.OmaFactory omaFactory;
  private final HashMap<Object, MxAdTagLoader> adTagLoaderByAdsId;
  private final HashMap<AdsMediaSource, MxAdTagLoader> adTagLoaderByAdsMediaSource;
  private final Timeline.Period period;
  private final Timeline.Window window;

  private boolean wasSetPlayerCalled;
  @Nullable private Player nextPlayer;
  private List<String> supportedMimeTypes;
  @Nullable private Player player;
  @Nullable private MxAdTagLoader currentMxAdTagLoader;

  public MxMediaAdLoader(
      Context context, Configuration configuration, OmaUtil.OmaFactory omaFactory) {
    this.context = context.getApplicationContext();
    this.configuration = configuration;
    this.omaFactory = omaFactory;
    supportedMimeTypes = ImmutableList.of();
    adTagLoaderByAdsId = new HashMap<>();
    adTagLoaderByAdsMediaSource = new HashMap<>();
    period = new Timeline.Period();
    window = new Timeline.Window();
  }


  /**
   * Returns the {@link AdDisplayContainer} used by this loader, or {@code null} if ads have not
   * been requested yet.
   *
   * <p>Note: any video controls overlays registered via {@link
   * AdDisplayContainer#registerFriendlyObstruction(FriendlyObstruction)} will be unregistered
   * automatically when the media source detaches from this instance. It is therefore necessary to
   * re-register views each time the ads loader is reused. Alternatively, provide overlay views via
   * the {@link AdViewProvider} when creating the media source to benefit from automatic
   * registration.
   */
  @Nullable
  public AdDisplayContainer getAdDisplayContainer() {
    return currentMxAdTagLoader != null ? currentMxAdTagLoader.getAdDisplayContainer() : null;
  }

  /**
   * Requests ads, if they have not already been requested. Must be called on the main thread.
   *
   * <p>Ads will be requested automatically when the player is prepared if this method has not been
   * called, so it is only necessary to call this method if you want to request ads before preparing
   * the player.
   *
   * @param adTagDataSpec The data specification of the ad tag to load. See class javadoc for
   *     information about compatible ad tag formats.
   * @param adsId A opaque identifier for the ad playback state across start/stop calls.
   * @param adViewGroup A {@link ViewGroup} on top of the player that will show any ad UI, or {@code
   *     null} if playing audio-only ads.
   */
  public void requestAds(DataSpec adTagDataSpec, Object adsId, @Nullable ViewGroup adViewGroup) {
    if (!adTagLoaderByAdsId.containsKey(adsId)) {
      MxAdTagLoader mxAdTagLoader =
          new MxAdTagLoader(
              context,
              configuration,
              omaFactory,
              supportedMimeTypes,
              adTagDataSpec,
              adsId,
              adViewGroup);
      adTagLoaderByAdsId.put(adsId, mxAdTagLoader);
    }
  }

  /**
   * Skips the current ad.
   *
   * <p>This method is intended for apps that play audio-only ads and so need to provide their own
   * UI for users to skip skippable ads. Apps showing video ads should not call this method, as the
   * IMA SDK provides the UI to skip ads in the ad view group passed via {@link AdViewProvider}.
   */
  public void skipAd() {
    if (currentMxAdTagLoader != null) {
      currentMxAdTagLoader.skipAd();
    }
  }


  // AdsLoader implementation.

  @Override
  public void setPlayer(@Nullable Player player) {
    checkState(Looper.myLooper() == getImaLooper());
    checkState(player == null || player.getApplicationLooper() == getImaLooper());
    nextPlayer = player;
    wasSetPlayerCalled = true;
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
    List<String> supportedMimeTypes = new ArrayList<>();
    String hlsMimeType = null;
    for (@C.ContentType int contentType : contentTypes) {
      // IMA does not support Smooth Streaming ad media.
     if (contentType == C.TYPE_HLS) {
       hlsMimeType = MimeTypes.APPLICATION_M3U8;
      } else if (contentType == C.TYPE_DASH) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_MPD);
      } else if (contentType == C.TYPE_OTHER) {
        supportedMimeTypes.addAll(
            Arrays.asList(
                MimeTypes.VIDEO_MP4,
                MimeTypes.VIDEO_WEBM,
                MimeTypes.VIDEO_H263,
                MimeTypes.AUDIO_MP4,
                MimeTypes.AUDIO_MPEG));
      }
    }
    if (hlsMimeType != null){
      supportedMimeTypes.add(0, hlsMimeType);
    }
    this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes);
  }

  @Override
  public void start(
      AdsMediaSource adsMediaSource,
      DataSpec adTagDataSpec,
      Object adsId,
      AdViewProvider adViewProvider,
      EventListener eventListener) {
    checkState(
        wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
    if (adTagLoaderByAdsMediaSource.isEmpty()) {
      player = nextPlayer;
      @Nullable Player player = this.player;
      if (player == null) {
        return;
      }
      player.addListener(this);
    }

    @Nullable MxAdTagLoader mxAdTagLoader = adTagLoaderByAdsId.get(adsId);
    if (mxAdTagLoader == null) {
      requestAds(adTagDataSpec, adsId, adViewProvider.getAdViewGroup());
      mxAdTagLoader = adTagLoaderByAdsId.get(adsId);
    }
    adTagLoaderByAdsMediaSource.put(adsMediaSource, checkNotNull(mxAdTagLoader));
    mxAdTagLoader.addListenerWithAdView(eventListener, adViewProvider);
    maybeUpdateCurrentAdTagLoader();
  }

  @Override
  public void stop(AdsMediaSource adsMediaSource, EventListener eventListener) {
    @Nullable MxAdTagLoader removedMxAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource);
    maybeUpdateCurrentAdTagLoader();
    if (removedMxAdTagLoader != null) {
      removedMxAdTagLoader.removeListener(eventListener);
    }

    if (player != null && adTagLoaderByAdsMediaSource.isEmpty()) {
      player.removeListener(this);
      player = null;
    }
  }

  @Override
  public void release() {
    if (player != null) {
      player.removeListener(this);
      player = null;
      maybeUpdateCurrentAdTagLoader();
    }
    nextPlayer = null;

    for (MxAdTagLoader mxAdTagLoader : adTagLoaderByAdsMediaSource.values()) {
      mxAdTagLoader.release();
    }
    adTagLoaderByAdsMediaSource.clear();

    for (MxAdTagLoader mxAdTagLoader : adTagLoaderByAdsId.values()) {
      mxAdTagLoader.release();
    }
    adTagLoaderByAdsId.clear();
  }

  @Override
  public void handlePrepareComplete(
      AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup) {
    if (player == null) {
      return;
    }
    checkNotNull(adTagLoaderByAdsMediaSource.get(adsMediaSource))
        .handlePrepareComplete(adGroupIndex, adIndexInAdGroup);
  }

  @Override
  public void handlePrepareError(
      AdsMediaSource adsMediaSource,
      int adGroupIndex,
      int adIndexInAdGroup,
      IOException exception) {
    if (player == null) {
      return;
    }
    checkNotNull(adTagLoaderByAdsMediaSource.get(adsMediaSource))
        .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception);
  }

  @Override
  public Uri getAdUri(AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup) {
    return adTagLoaderByAdsMediaSource.get(adsMediaSource).getAdUri(adGroupIndex, adIndexInAdGroup);
  }

  // Player.Listener implementation.

  @Override
  public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
    if (timeline.isEmpty()) {
      // The player is being reset or contains no media.
      return;
    }
    maybeUpdateCurrentAdTagLoader();
    maybePreloadNextPeriodAds();
  }

//  @Override
//  public void onPositionDiscontinuity(
//      Player.PositionInfo oldPosition,
//      Player.PositionInfo newPosition,
//      @Player.DiscontinuityReason int reason) {
//    maybeUpdateCurrentAdTagLoader();
//    maybePreloadNextPeriodAds();
//  }

  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    maybePreloadNextPeriodAds();
  }

  @Override
  public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    maybePreloadNextPeriodAds();
  }

  // Internal methods.

  private void maybeUpdateCurrentAdTagLoader() {
    @Nullable MxAdTagLoader oldMxAdTagLoader = currentMxAdTagLoader;
    @Nullable MxAdTagLoader newMxAdTagLoader = getCurrentAdTagLoader();
    if (!Util.areEqual(oldMxAdTagLoader, newMxAdTagLoader)) {
      if (oldMxAdTagLoader != null) {
        oldMxAdTagLoader.deactivate();
      }
      currentMxAdTagLoader = newMxAdTagLoader;
      if (newMxAdTagLoader != null) {
        newMxAdTagLoader.activate(checkNotNull(player));
      }
    }
  }

  @Nullable
  private MxAdTagLoader getCurrentAdTagLoader() {
    @Nullable Player player = this.player;
    if (player == null) {
      return null;
    }
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return null;
    }
    int periodIndex = player.getCurrentPeriodIndex();
    @Nullable Object adsId = timeline.getPeriod(periodIndex, period).getAdsId();
    if (adsId == null) {
      return null;
    }
    @Nullable MxAdTagLoader mxAdTagLoader = adTagLoaderByAdsId.get(adsId);
    if (mxAdTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(mxAdTagLoader)) {
      return null;
    }
    return mxAdTagLoader;
  }

  private void maybePreloadNextPeriodAds() {
    @Nullable Player player = this.player;
    if (player == null) {
      return;
    }
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int nextPeriodIndex =
        timeline.getNextPeriodIndex(
            player.getCurrentPeriodIndex(),
            period,
            window,
            player.getRepeatMode(),
            player.getShuffleModeEnabled());
    if (nextPeriodIndex == C.INDEX_UNSET) {
      return;
    }
    timeline.getPeriod(nextPeriodIndex, period);
    @Nullable Object nextAdsId = period.getAdsId();
    if (nextAdsId == null) {
      return;
    }
    @Nullable MxAdTagLoader nextMxAdTagLoader = adTagLoaderByAdsId.get(nextAdsId);
    if (nextMxAdTagLoader == null || nextMxAdTagLoader == currentMxAdTagLoader) {
      return;
    }
    long periodPositionUs =
        timeline.getPeriodPosition(
                window, period, period.windowIndex, /* windowPositionUs= */ C.TIME_UNSET)
            .second;
    nextMxAdTagLoader.maybePreloadAds(C.usToMs(periodPositionUs), C.usToMs(period.durationUs));
  }


  public static final class DefaultOmaFactory implements OmaUtil.OmaFactory {

    @Nullable
    private AdsRenderingSettings.BandwidthMeter bandwidthMeter;

    public DefaultOmaFactory(@Nullable AdsRenderingSettings.BandwidthMeter bandwidthMeter) {
      this.bandwidthMeter = bandwidthMeter;
    }

    @Override
    public AdsRenderingSettings createAdsRenderingSettings() {
      AdsRenderingSettings adsRenderingSettings = OmaSdkFactory.getInstance().createAdsRenderingSettings();
      if (bandwidthMeter != null) adsRenderingSettings.setBandwidthMeter(bandwidthMeter);
      return adsRenderingSettings;
    }

    @Override
    @NonNull
    public AdDisplayContainer createAdDisplayContainer(ViewGroup container, VideoAdPlayer player, boolean detectObstruction) {
      return OmaSdkFactory.createAdDisplayContainer(container, player, detectObstruction);
    }

    @Override
    @NonNull
    public AdDisplayContainer createAudioAdDisplayContainer(Context context, VideoAdPlayer player) {
      return OmaSdkFactory.createAudioAdDisplayContainer(context, player);
    }

    // The reasonDetail parameter to createFriendlyObstruction is annotated @Nullable but the
    // annotation is not kept in the obfuscated dependency.
    @SuppressWarnings("nullness:argument.type.incompatible")
    @Override
    public FriendlyObstruction createFriendlyObstruction(
        View view,
        FriendlyObstructionPurpose friendlyObstructionPurpose,
        @Nullable String reasonDetail) {
      return OmaSdkFactory.getInstance()
          .createFriendlyObstruction(view, friendlyObstructionPurpose, reasonDetail);
    }

    @NotNull
    @Override
    public AdsRequest createAdsRequest() {
      return OmaSdkFactory.getInstance().createAdsRequest();
    }

    @Nullable
    @Override
    public com.mxplay.interactivemedia.api.AdsLoader createAdsLoader(
        @NonNull Context context, @Nullable AdDisplayContainer adDisplayContainer,
        @NotNull Configuration configuration) {

      return OmaSdkFactory.getInstance()
          .createAdsLoader(context,configuration.getMxMediaSdkConfig(), adDisplayContainer);
    }
  }
}
