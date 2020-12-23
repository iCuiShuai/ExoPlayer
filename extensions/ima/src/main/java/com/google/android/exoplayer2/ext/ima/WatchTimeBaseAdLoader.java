package com.google.android.exoplayer2.ext.ima;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.UiElement;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WatchTimeBaseAdLoader implements Player.EventListener, AdsLoader {


    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.ima");
    }

    /**
     * Builder for {@link WatchTimeBaseAdLoader}.
     */
    public static final class Builder {

        /**
         * The default duration in milliseconds for which the player must buffer while preloading an ad
         * group before that ad group is skipped and marked as having failed to load.
         *
         * <p>This value should be large enough not to trigger discarding the ad when it actually might
         * load soon, but small enough so that user is not waiting for too long.
         *
         * @see #setAdPreloadTimeoutMs(long)
         */
        public static final long DEFAULT_AD_PRELOAD_TIMEOUT_MS = 2 * C.MILLIS_PER_SECOND;

        private final Context context;

        @Nullable
        private ImaSdkSettings imaSdkSettings;
        @Nullable
        private AdEvent.AdEventListener adEventListener;
        @Nullable
        private Set<UiElement> adUiElements;
        private long adPreloadTimeoutMs;
        private int vastLoadTimeoutMs;
        private int fakeCuePointsDistanceMs;
        private int mediaBitrate;
        private boolean focusSkipButtonWhenAvailable;
        private boolean playAdBeforeStartPosition;
        private AdLoaderInputs adLoaderInputs;
        private ImaFactory imaFactory;

        /**
         * Creates a new builder for {@link WatchTimeBaseAdLoader}.
         *
         * @param context The context;
         */
        public Builder(Context context) {
            this.context = Assertions.checkNotNull(context);
            adPreloadTimeoutMs = DEFAULT_AD_PRELOAD_TIMEOUT_MS;
            vastLoadTimeoutMs = TIMEOUT_UNSET;
            mediaBitrate = BITRATE_UNSET;
            focusSkipButtonWhenAvailable = true;
            playAdBeforeStartPosition = true;
            imaFactory = new DefaultImaFactory();
            adLoaderInputs = new AdLoaderInputs();
        }

        /**
         * Sets the IMA SDK settings. The provided settings instance's player type and version fields
         * may be overwritten.
         *
         * <p>If this method is not called the default settings will be used.
         *
         * @param imaSdkSettings The {@link ImaSdkSettings}.
         * @return This builder, for convenience.
         */
        public WatchTimeBaseAdLoader.Builder setImaSdkSettings(ImaSdkSettings imaSdkSettings) {
            this.imaSdkSettings = Assertions.checkNotNull(imaSdkSettings);
            return this;
        }

        /**
         * Sets a listener for ad events that will be passed to {@link
         * AdsManager#addAdEventListener(AdEvent.AdEventListener)}.
         *
         * @param adEventListener The ad event listener.
         * @return This builder, for convenience.
         */
        public WatchTimeBaseAdLoader.Builder setAdEventListener(AdEvent.AdEventListener adEventListener) {
            this.adEventListener = Assertions.checkNotNull(adEventListener);
            return this;
        }

        /**
         * Sets the ad UI elements to be rendered by the IMA SDK.
         *
         * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings#setUiElements(Set)
         */
        public WatchTimeBaseAdLoader.Builder setAdUiElements(Set<UiElement> adUiElements) {
            this.adUiElements = new HashSet<>(Assertions.checkNotNull(adUiElements));
            return this;
        }

        /**
         * Sets the duration in milliseconds for which the player must buffer while preloading an ad
         * group before that ad group is skipped and marked as having failed to load. Pass {@link
         * C#TIME_UNSET} if there should be no such timeout. The default value is {@value
         * DEFAULT_AD_PRELOAD_TIMEOUT_MS} ms.
         *
         * <p>The purpose of this timeout is to avoid playback getting stuck in the unexpected case that
         * the IMA SDK does not load an ad break based on the player's reported content position.
         *
         * @param adPreloadTimeoutMs The timeout buffering duration in milliseconds, or {@link
         *                           C#TIME_UNSET} for no timeout.
         * @return This builder, for convenience.
         */
        public WatchTimeBaseAdLoader.Builder setAdPreloadTimeoutMs(long adPreloadTimeoutMs) {
            Assertions.checkArgument(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0);
            this.adPreloadTimeoutMs = adPreloadTimeoutMs;
            return this;
        }

        /**
         * Sets the VAST load timeout, in milliseconds.
         *
         * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRequest#setVastLoadTimeout(float)
         */
        public WatchTimeBaseAdLoader.Builder setVastLoadTimeoutMs(int vastLoadTimeoutMs) {
            Assertions.checkArgument(vastLoadTimeoutMs > 0);
            this.vastLoadTimeoutMs = vastLoadTimeoutMs;
            return this;
        }




        public WatchTimeBaseAdLoader.Builder setFakeCuePointsDistanceMs(int fakeCuePointsDistanceMs) {
            this.fakeCuePointsDistanceMs = fakeCuePointsDistanceMs;
            return this;
        }

        /**
         * Sets the media maximum recommended bitrate for ads, in bps.
         *
         * @param bitrate The media maximum recommended bitrate for ads, in bps.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings#setBitrateKbps(int)
         */
        public WatchTimeBaseAdLoader.Builder setMaxMediaBitrate(int bitrate) {
            Assertions.checkArgument(bitrate > 0);
            this.mediaBitrate = bitrate;
            return this;
        }

        /**
         * Sets whether to focus the skip button (when available) on Android TV devices. The default
         * setting is {@code true}.
         *
         * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
         *                                     Android TV devices.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings#setFocusSkipButtonWhenAvailable(boolean)
         */
        public WatchTimeBaseAdLoader.Builder setFocusSkipButtonWhenAvailable(boolean focusSkipButtonWhenAvailable) {
            this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
            return this;
        }

        /**
         * Sets whether to play an ad before the start position when beginning playback. If {@code
         * true}, an ad will be played if there is one at or before the start position. If {@code
         * false}, an ad will be played only if there is one exactly at the start position. The default
         * setting is {@code true}.
         *
         * @param playAdBeforeStartPosition Whether to play an ad before the start position when
         *                                  beginning playback.
         * @return This builder, for convenience.
         */
        public WatchTimeBaseAdLoader.Builder setPlayAdBeforeStartPosition(boolean playAdBeforeStartPosition) {
            this.playAdBeforeStartPosition = playAdBeforeStartPosition;
            return this;
        }

        @VisibleForTesting
            /* package */ WatchTimeBaseAdLoader.Builder setImaFactory(ImaFactory imaFactory) {
            this.imaFactory = Assertions.checkNotNull(imaFactory);
            return this;
        }

        public WatchTimeBaseAdLoader.Builder setAdLoaderInputs(@NonNull AdLoaderInputs adLoaderInputs) {
            this.adLoaderInputs = adLoaderInputs;
            return this;
        }

        /**
         * Returns a new {@link WatchTimeBaseAdLoader} for the specified ad tag.
         *
         * @param adTagUri The URI of a compatible ad tag to load. See
         *                 https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
         *                 information on compatible ad tags.
         * @return The new {@link WatchTimeBaseAdLoader}.
         */
        public WatchTimeBaseAdLoader buildForAdTag(Uri adTagUri) {
            return new WatchTimeBaseAdLoader(
                    context,
                    adTagUri,
                    imaSdkSettings,
                    /* adsResponse= */ null,
                    adPreloadTimeoutMs,
                    vastLoadTimeoutMs,
                    fakeCuePointsDistanceMs,
                    mediaBitrate,
                    focusSkipButtonWhenAvailable,
                    playAdBeforeStartPosition,
                    adUiElements,
                    adEventListener,
                    imaFactory,
                    adLoaderInputs);
        }

        /**
         * Returns a new {@link WatchTimeBaseAdLoader} with the specified sideloaded ads response.
         *
         * @param adsResponse The sideloaded VAST, VMAP, or ad rules response to be used instead of
         *                    making a request via an ad tag URL.
         * @return The new {@link WatchTimeBaseAdLoader}.
         */
        public WatchTimeBaseAdLoader buildForAdsResponse(String adsResponse) {
            return new WatchTimeBaseAdLoader(
                    context,
                    /* adTagUri= */ null,
                    imaSdkSettings,
                    adsResponse,
                    adPreloadTimeoutMs,
                    vastLoadTimeoutMs,
                    fakeCuePointsDistanceMs,
                    mediaBitrate,
                    focusSkipButtonWhenAvailable,
                    playAdBeforeStartPosition,
                    adUiElements,
                    adEventListener,
                    imaFactory, adLoaderInputs);
        }
    }

    private static final boolean DEBUG = false;
    private static final String TAG = "WatchTimeBaseAdLoader";

    private static final String IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima";
    private static final String IMA_SDK_SETTINGS_PLAYER_VERSION = ExoPlayerLibraryInfo.VERSION;

    /**
     * Interval at which ad progress updates are provided to the IMA SDK, in milliseconds. 100 ms is
     * the interval recommended by the IMA documentation.
     *
     * @see com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer.VideoAdPlayerCallback
     */
    private static final int AD_PROGRESS_UPDATE_INTERVAL_MS = 100;

    /**
     * The value used in {@link VideoProgressUpdate}s to indicate an unset duration.
     */
    private static final long IMA_DURATION_UNSET = -1L;

    /**
     * Threshold before the end of content at which IMA is notified that content is complete if the
     * player buffers, in milliseconds.
     */
    private static final long THRESHOLD_END_OF_CONTENT_MS = 5000;
    /**
     * Threshold before the start of an ad at which IMA is expected to be able to preload the ad, in
     * milliseconds.
     */
    private static final long THRESHOLD_AD_PRELOAD_MS = 1000;
    /**
     * The threshold below which ad cue points are treated as matching, in microseconds.
     */
    private static final long THRESHOLD_AD_MATCH_US = 1000;

    private static final int DEFAULT_VAST_TIMEOUT = 5000;


    private static final int TIMEOUT_UNSET = -1;
    private static final int BITRATE_UNSET = -1;

    /**
     * The state of ad playback.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED})
    private @interface ImaAdState {
    }

    /**
     * The ad playback state when IMA is not playing an ad.
     */
    private static final int IMA_AD_STATE_NONE = 0;
    /**
     * The ad playback state when IMA has called {@link WatchTimeBaseAdLoader.ComponentListener#playAd(AdMediaInfo)} and not
     * {@link WatchTimeBaseAdLoader.ComponentListener##pauseAd(AdMediaInfo)}.
     */
    private static final int IMA_AD_STATE_PLAYING = 1;
    /**
     * The ad playback state when IMA has called {@link WatchTimeBaseAdLoader.ComponentListener#pauseAd(AdMediaInfo)} while
     * playing an ad.
     */
    private static final int IMA_AD_STATE_PAUSED = 2;

    private static final int FAKE_CUEPOINTS_DISTANCE = 30; // in secs
    public static final int NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD = 8000; // 8 sec
    public static final int NEXT_AD_DISTANCE_THRESHOLD = 3000; // 3 sec


    @Nullable
    private final Uri adTagUri;
    @Nullable
    private final String adsResponse;
    private final long adPreloadTimeoutMs;
    private final int vastLoadTimeoutMs;
    private final int mediaLoadTimeoutMs;
    private final int fakeCuePointsDistanceMs;
    private final boolean focusSkipButtonWhenAvailable;
    private final boolean playAdBeforeStartPosition;
    private final int mediaBitrate;
    @Nullable
    private final Set<UiElement> adUiElements;
    @Nullable
    private final AdEvent.AdEventListener adEventListener;
    private final ImaFactory imaFactory;
    private final Timeline.Period period;
    private final Handler handler;
    private final WatchTimeBaseAdLoader.ComponentListener componentListener;
    private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks;
    private final AdDisplayContainer adDisplayContainer;
    private final com.google.ads.interactivemedia.v3.api.AdsLoader adsLoader;
    private final Runnable updateAdProgressRunnable;
    private final Map<AdMediaInfo, MxAdPlaybackState.AdInfo> adInfoByAdMediaInfo;

    private boolean wasSetPlayerCalled;
    @Nullable
    private Player nextPlayer;
    @Nullable
    private Object pendingAdRequestContext;
    private List<String> supportedMimeTypes;
    @Nullable
    private AdsLoader.EventListener eventListener;
    @Nullable
    private Player player;
    private VideoProgressUpdate lastContentProgress;
    private VideoProgressUpdate lastAdProgress;
    private int lastVolumePercent;

    @Nullable
    private AdsManager adsManager;
    private boolean isAdsManagerInitialized;
    private boolean hasAdPlaybackState;
    @Nullable
    private AdsMediaSource.AdLoadException pendingAdLoadError;
    private Timeline timeline;
    private long contentDurationMs;
    private AdPlaybackState adPlaybackState;
    private AdPlaybackState adPlaybackStateActual;
    // Fields tracking IMA's state.

    /**
     * Whether IMA has sent an ad event to pause content since the last resume content event.
     */
    private boolean imaPausedContent;
    /**
     * The current ad playback state.
     */
    private @WatchTimeBaseAdLoader.ImaAdState
    int imaAdState;
    /**
     * The current ad media info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}.
     */
    @Nullable
    private AdMediaInfo imaAdMediaInfo;
    /**
     * The current ad info, or {@code null} if in state {@link #IMA_AD_STATE_NONE}.
     */
    @Nullable
    private MxAdPlaybackState.AdInfo imaAdInfo;
    /**
     * Whether {@link com.google.ads.interactivemedia.v3.api.AdsLoader#contentComplete()} has been
     * called since starting ad playback.
     */
    private boolean sentContentComplete;

    // Fields tracking the player/loader state.

    /**
     * Whether the player is playing an ad.
     */
    private boolean playingAd;
    /**
     * Whether the player is buffering an ad.
     */
    private boolean bufferingAd;
    /**
     * If the player is playing an ad, stores the ad index in its ad group. {@link C#INDEX_UNSET}
     * otherwise.
     */
    private int playingAdIndexInAdGroup;
    /**
     * The ad info for a pending ad for which the media failed preparation, or {@code null} if no
     * pending ads have failed to prepare.
     */
    @Nullable
    private MxAdPlaybackState.AdInfo pendingAdPrepareErrorAdInfo;
    /**
     * If a content period has finished but IMA has not yet called {@link
     * WatchTimeBaseAdLoader.ComponentListener#playAd(AdMediaInfo)}, stores the value of {@link
     * SystemClock#elapsedRealtime()} when the content stopped playing. This can be used to determine
     * a fake, increasing content position. {@link C#TIME_UNSET} otherwise.
     */
    private long fakeContentProgressElapsedRealtimeMs;
    /**
     * If {@link #fakeContentProgressElapsedRealtimeMs} is set, stores the offset from which the
     * content progress should increase. {@link C#TIME_UNSET} otherwise.
     */
    private long fakeContentProgressOffsetMs;
    /**
     * Stores the pending content position when a seek operation was intercepted to play an ad.
     */
    private long pendingContentPositionMs;
    /**
     * Whether {@link WatchTimeBaseAdLoader.ComponentListener#getContentProgress()} has sent {@link
     * #pendingContentPositionMs} to IMA.
     */
    private boolean sentPendingContentPositionMs;
    /**
     * Stores the real time in milliseconds at which the player started buffering, possibly due to not
     * having preloaded an ad, or {@link C#TIME_UNSET} if not applicable.
     */
    private long waitingForPreloadElapsedRealtimeMs;

    private long startLoadMediaTime;
    private long startRequestTime;
    private int expectedAdGroupIndex =  C.INDEX_UNSET;
    private int prevPlayedAdGroupIndex = C.INDEX_UNSET;
    private final @Nullable IVideoAdTracker adTracker;
    private @NonNull
    AdLoaderInputs adLoaderInputs;
    // AdsLoader implementation.
    private PlaybackStatsListener playbackStatsListener;


    /** ============================================ **/


    /**
     * Creates a new IMA ads loader.
     *
     * <p>If you need to customize the ad request, use {@link WatchTimeBaseAdLoader.Builder} instead.
     *
     * @param context  The context.
     * @param adTagUri The {@link Uri} of an ad tag compatible with the Android IMA SDK. See
     *                 https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility for
     *                 more information.
     */
    public WatchTimeBaseAdLoader(Context context, Uri adTagUri) {
        this(
                context,
                adTagUri,
                /* imaSdkSettings= */ null,
                /* adsResponse= */ null,
                /* adPreloadTimeoutMs= */ WatchTimeBaseAdLoader.Builder.DEFAULT_AD_PRELOAD_TIMEOUT_MS,
                /* vastLoadTimeoutMs= */ TIMEOUT_UNSET,
                TIMEOUT_UNSET,
                /* mediaBitrate= */ BITRATE_UNSET,
                /* focusSkipButtonWhenAvailable= */ true,
                /* playAdBeforeStartPosition= */ true,
                /* adUiElements= */ null,
                /* adEventListener= */ null,
                /* imaFactory= */ new DefaultImaFactory(),
                /* extra inputs for loader */ new AdLoaderInputs());
    }

    @SuppressWarnings({"nullness:argument.type.incompatible", "methodref.receiver.bound.invalid"})
    private WatchTimeBaseAdLoader(
            Context context,
            @Nullable Uri adTagUri,
            @Nullable ImaSdkSettings imaSdkSettings,
            @Nullable String adsResponse,
            long adPreloadTimeoutMs,
            int vastLoadTimeoutMs,
            int fakeCuePointsDistanceMs,
            int mediaBitrate,
            boolean focusSkipButtonWhenAvailable,
            boolean playAdBeforeStartPosition,
            @Nullable Set<UiElement> adUiElements,
            @Nullable AdEvent.AdEventListener adEventListener,
            ImaFactory imaFactory,
            AdLoaderInputs adLoaderInputs) {
        Assertions.checkArgument(adTagUri != null || adsResponse != null);
        this.adTagUri = adTagUri;
        this.adsResponse = adsResponse;
        this.adPreloadTimeoutMs = adPreloadTimeoutMs;
        if (vastLoadTimeoutMs == TIMEOUT_UNSET) {
            this.vastLoadTimeoutMs = DEFAULT_VAST_TIMEOUT;
        } else {
            this.vastLoadTimeoutMs = vastLoadTimeoutMs;
        }
        this.fakeCuePointsDistanceMs = fakeCuePointsDistanceMs <= 0 ? FAKE_CUEPOINTS_DISTANCE : fakeCuePointsDistanceMs;
        this.mediaLoadTimeoutMs = fakeCuePointsDistanceMs + NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD ;

        this.mediaBitrate = mediaBitrate;
        this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable;
        this.playAdBeforeStartPosition = playAdBeforeStartPosition;
        this.adUiElements = adUiElements;
        this.adEventListener = adEventListener;
        this.imaFactory = imaFactory;
        this.adLoaderInputs = adLoaderInputs != null ? adLoaderInputs : new AdLoaderInputs();
        this.adTracker = adLoaderInputs.getAdTracker();
        if (imaSdkSettings == null) {
            imaSdkSettings = imaFactory.createImaSdkSettings();
            if (DEBUG) {
                imaSdkSettings.setDebugMode(true);
            }
        }
        imaSdkSettings.setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE);
        imaSdkSettings.setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION);
        period = new Timeline.Period();
        handler = Util.createHandler(getImaLooper(), /* callback= */ null);
        componentListener = new WatchTimeBaseAdLoader.ComponentListener();
        adCallbacks = new ArrayList<>(/* initialCapacity= */ 1);
        adDisplayContainer = imaFactory.createAdDisplayContainer();
        adDisplayContainer.setPlayer(/* videoAdPlayer= */ componentListener);
        adsLoader =
                imaFactory.createAdsLoader(
                        context.getApplicationContext(), imaSdkSettings, adDisplayContainer);
        adsLoader.addAdErrorListener(componentListener);
        adsLoader.addAdsLoadedListener(componentListener);
        updateAdProgressRunnable = this::updateAdProgress;
        adInfoByAdMediaInfo = new HashMap<>();
        supportedMimeTypes = Collections.emptyList();
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
        fakeContentProgressOffsetMs = C.TIME_UNSET;
        pendingContentPositionMs = C.TIME_UNSET;
        waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
        contentDurationMs = C.TIME_UNSET;
        timeline = Timeline.EMPTY;
        adPlaybackState = AdPlaybackState.NONE;
        adPlaybackStateActual = AdPlaybackState.NONE;
        playbackStatsListener = new PlaybackStatsListener(true, null);
    }

    /**
     * Returns the underlying {@code com.google.ads.interactivemedia.v3.api.AdsLoader} wrapped by
     * this instance.
     */
    public com.google.ads.interactivemedia.v3.api.AdsLoader getAdsLoader() {
        return adsLoader;
    }

    /**
     * Returns the {@link AdDisplayContainer} used by this loader.
     *
     * <p>Note: any video controls overlays registered via {@link
     * AdDisplayContainer#registerVideoControlsOverlay(View)} will be unregistered automatically when
     * the media source detaches from this instance. It is therefore necessary to re-register views
     * each time the ads loader is reused. Alternatively, provide overlay views via the {@link
     * AdsLoader.AdViewProvider} when creating the media source to benefit from automatic
     * registration.
     */
    public AdDisplayContainer getAdDisplayContainer() {
        return adDisplayContainer;
    }

    private final Runnable requestTimeOutRunnable = new Runnable() {
        @Override
        public void run() {
            adsLoader.removeAdsLoadedListener(componentListener);
            componentListener.onAdError(new AdsLoadRequestTimeoutEvent(pendingAdRequestContext));
        }
    };

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
        if (hasAdPlaybackState || adsManager != null || pendingAdRequestContext != null) {
            // Ads have already been requested.
            return;
        }
        adDisplayContainer.setAdContainer(adViewGroup);
        AdsRequest request = imaFactory.createAdsRequest();
        if (adTagUri != null) {
            request.setAdTagUrl(adTagUri.toString());
        } else {
            request.setAdsResponse(adsResponse);
        }
        request.setVastLoadTimeout(vastLoadTimeoutMs);
        request.setContentProgressProvider(componentListener);
        pendingAdRequestContext = new Object();
        request.setUserRequestContext(pendingAdRequestContext);
        handler.postDelayed(requestTimeOutRunnable, vastLoadTimeoutMs + 2000);
        adsLoader.requestAds(request);
    }


    @Override
    public void setPlayer(@Nullable Player player) {
        Assertions.checkState(Looper.myLooper() == getImaLooper());
        Assertions.checkState(player == null || player.getApplicationLooper() == getImaLooper());
        if (this.player != null && player instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer) this.player).removeAnalyticsListener(playbackStatsListener);
        }
        nextPlayer = player;
        if (this.nextPlayer != null && nextPlayer instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer) this.nextPlayer).addAnalyticsListener(playbackStatsListener);
        }
        wasSetPlayerCalled = true;
    }

    @Override
    public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
        List<String> supportedMimeTypes = new ArrayList<>();
        for (@C.ContentType int contentType : contentTypes) {
            // IMA does not support Smooth Streaming ad media.
            if (contentType == C.TYPE_DASH) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_MPD);
            } else if (contentType == C.TYPE_HLS) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8);
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
        this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes);
    }

    @Override
    public void start(AdsLoader.EventListener eventListener, AdsLoader.AdViewProvider adViewProvider) {
        Assertions.checkState(
                wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.");
        player = nextPlayer;
        if (player == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "start IMA ad loader with player: " + player.hashCode() + " :: ima loader " + this.hashCode());
        }
        player.addListener(this);
        boolean playWhenReady = player.getPlayWhenReady();
        this.eventListener = eventListener;
        lastVolumePercent = 0;
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        ViewGroup adViewGroup = adViewProvider.getAdViewGroup();
        adDisplayContainer.setAdContainer(adViewGroup);
        View[] adOverlayViews = adViewProvider.getAdOverlayViews();
        for (View view : adOverlayViews) {
            adDisplayContainer.registerVideoControlsOverlay(view);
        }
        maybeNotifyPendingAdLoadError();
        if (hasAdPlaybackState) {
            // Pass the ad playback state to the player, and resume ads if necessary.
            eventListener.onAdPlaybackState(adPlaybackState);
            if (adsManager != null && imaPausedContent && playWhenReady) {
                adsManager.resume();
            }
        } else if (adsManager != null) {
            adPlaybackStateActual = new MxAdPlaybackState(AdPlaybackStateFactory.fromCuePoints(adsManager.getAdCuePoints()).adGroupTimesUs);
            List<Float> fakeCuePoints = generateFakeCuePoints(adsManager);
            adPlaybackState =  new MxAdPlaybackState(AdPlaybackStateFactory.fromCuePoints(fakeCuePoints).adGroupTimesUs);
            updateAdPlaybackState();
        } else {
            // Ads haven't loaded yet, so request them.
            requestAds(adViewGroup);
        }
    }


    @Override
    public void stop() {
        @Nullable Player player = this.player;
        if (player == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, " stop ima ad loader : ");
        }

        if (adsManager != null && imaPausedContent) {
            adsManager.pause();
            adPlaybackState =
                    adPlaybackState.withAdResumePositionUs(
                            playingAd ? C.msToUs(player.getCurrentPosition()) : 0);
        }
        lastVolumePercent = getPlayerVolumePercent();
        lastAdProgress = getAdVideoProgressUpdate();
        lastContentProgress = getContentVideoProgressUpdate();
        adDisplayContainer.unregisterAllVideoControlsOverlays();
        player.removeListener(this);
        this.player = null;
        eventListener = null;
    }

    @Override
    public void release() {
        if (DEBUG) {
            Log.d(TAG, "  ima ad loader release ");
        }
        pendingAdRequestContext = null;
        destroyAdsManager();
        adsLoader.removeAdsLoadedListener(componentListener);
        adsLoader.removeAdErrorListener(componentListener);
        imaPausedContent = false;
        imaAdState = IMA_AD_STATE_NONE;
        imaAdMediaInfo = null;
        stopUpdatingAdProgress();
        imaAdInfo = null;
        pendingAdLoadError = null;
        adPlaybackState = AdPlaybackState.NONE;
        adPlaybackStateActual = AdPlaybackState.NONE;
        hasAdPlaybackState = true;
        updateAdPlaybackState();
        removeTimeoutCallback();
    }

    private void removeTimeoutCallback() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
        if (player == null) {
            return;
        }
        try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception);
        } catch (RuntimeException e) {
            maybeNotifyInternalError("handlePrepareError", e);
        }
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
        long contentDurationUs = timeline.getPeriod(/* periodIndex= */ 0, period).durationUs;
        contentDurationMs = C.usToMs(contentDurationUs);
        if (contentDurationUs != C.TIME_UNSET) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
        }
        @Nullable AdsManager adsManager = this.adsManager;
        if (!isAdsManagerInitialized && adsManager != null) {
            isAdsManagerInitialized = true;
            @Nullable AdsRenderingSettings adsRenderingSettings = setupAdsRendering();
            if (adsRenderingSettings == null) {
                // There are no ads to play.
                destroyAdsManager();
            } else {
                adsManager.init(adsRenderingSettings);
                adsManager.start();
                if (DEBUG) {
                    Log.d(TAG, "Initialized with ads rendering settings: " + adsRenderingSettings);
                }
            }
            updateAdPlaybackState();
        }
        if (DEBUG) Log.d(TAG, "onTimelineChanged: " + reason);
        handleTimelineOrPositionChanged(false);
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
        handleTimelineOrPositionChanged((reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT));
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
        @Nullable Player player = this.player;
        if (adsManager == null || player == null) {
            return;
        }

        if (playbackState == Player.STATE_BUFFERING && !player.isPlayingAd()) {
            // Check whether we are waiting for an ad to preload.
            int adGroupIndex = getLoadingAdGroupIndex();
            if (adGroupIndex == C.INDEX_UNSET) {
                return;
            }
            AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adGroupIndex];
            if (adGroup.count != C.LENGTH_UNSET
                    && adGroup.count != 0
                    && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
                // An ad is available already so we must be buffering for some other reason.
                return;
            }
            long adGroupTimeMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]);
            long contentPositionMs = getContentPeriodPositionMs(player, timeline, period);
            long timeUntilAdMs = adGroupTimeMs - contentPositionMs;
            if (timeUntilAdMs < adPreloadTimeoutMs) {
                waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime();
            }
        } else if (playbackState == Player.STATE_READY) {
            waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
        }

        if (imaAdState == IMA_AD_STATE_PLAYING && !playWhenReady) {
            adsManager.pause();
            return;
        }

        if (imaAdState == IMA_AD_STATE_PAUSED && playWhenReady) {
            adsManager.resume();
            return;
        }

        handlePlayerStateChanged(playWhenReady, playbackState);
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (imaAdState != IMA_AD_STATE_NONE) {
            AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
            for (int i = 0; i < adCallbacks.size(); i++) {
                adCallbacks.get(i).onError(adMediaInfo);
            }
        }
    }


    // Internal methods.

    /**
     * Configures ads rendering for starting playback, returning the settings for the IMA SDK or
     * {@code null} if no ads should play.
     */
    @NonNull
    private AdsRenderingSettings setupAdsRendering() {
        AdsRenderingSettings adsRenderingSettings = imaFactory.createAdsRenderingSettings();
        adsRenderingSettings.setEnablePreloading(true);
        adsRenderingSettings.setMimeTypes(supportedMimeTypes);
        if (mediaLoadTimeoutMs != TIMEOUT_UNSET) {
            adsRenderingSettings.setLoadVideoTimeout(mediaLoadTimeoutMs);
        }
        if (mediaBitrate != BITRATE_UNSET) {
            adsRenderingSettings.setBitrateKbps(mediaBitrate / 1000);
        }
        adsRenderingSettings.setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable);
        if (adUiElements != null) {
            adsRenderingSettings.setUiElements(adUiElements);
        }

        long contentPositionMs =
                getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period);
        int adGroupForPositionIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(
                        C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
        if (adGroupForPositionIndex != C.INDEX_UNSET && !hasPrerollAdGroups(adPlaybackStateActual.adGroupTimesUs)) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupForPositionIndex);
            if (DEBUG) {
                Log.d(TAG, "Init ad rendering settings contentPositionMs : " + contentPositionMs + " skipped ad index " + adGroupForPositionIndex);
            }
        }

        return adsRenderingSettings;
    }

    private static boolean hasPrerollAdGroups(long[] adGroupTimesUs) {
        int count = adGroupTimesUs.length;
        return count > 0 &&  adGroupTimesUs[0] == 0;
    }
    private void handleAdEvent(AdEvent adEvent) {
        switch (adEvent.getType()) {
            case AD_BREAK_FETCH_ERROR:
                removeTimeoutCallback();
                if (DEBUG) {
                    Log.e(TAG, "AD_BREAK_FETCH_ERROR  : ");
                }
                String adGroupTimeSecondsString =
                        Assertions.checkNotNull(adEvent.getAdData().get("adBreakTime"));
                int adGroupTimeSeconds = Integer.parseInt(adGroupTimeSecondsString);
                int adGroupIndex = adGroupTimeSeconds == -1
                        ? adPlaybackStateActual.adGroupCount - 1
                        : Util.linearSearch(
                        adPlaybackStateActual.adGroupTimesUs, C.MICROS_PER_SECOND * adGroupTimeSeconds);

                if (adTracker != null) {
                    adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(IVideoAdTracker.WATCH_TIME_BASE_AD_LOADER, adGroupIndex, startRequestTime, new Exception("Fetch error for ad "), getAdGroupCount()));
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
            default:
                break;
        }
    }


    private void tryUpdateStartRequestTime(int adGroupIndexAfterPositionActualUs, long contentPositionMs, long contentDurationMs) {
        if (expectedAdGroupIndex != C.INDEX_UNSET) return;
        int adGroupIndex = adPlaybackStateActual.getAdGroupIndexForPositionUs(C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
        if (adGroupIndex == C.INDEX_UNSET){
            adGroupIndex = adGroupIndexAfterPositionActualUs;
        }
        if (adGroupIndex != C.INDEX_UNSET
                && Math.max(0, adPlaybackStateActual.adGroupTimesUs[adGroupIndex] -  C.msToUs(contentPositionMs)) < 300){
            expectedAdGroupIndex = adGroupIndex;
            startRequestTime = System.currentTimeMillis();
        }
    }

    private VideoProgressUpdate getContentVideoProgressUpdate() {
        if (player == null) {
            return lastContentProgress;
        }
        boolean hasContentDuration = contentDurationMs != C.TIME_UNSET;
        long contentPositionMs;
        if (imaAdState == IMA_AD_STATE_NONE && !playingAd) {
            contentPositionMs = playbackStatsListener.getContentTotalPlayTimeMs();
            if (DEBUG) { Log.d(TAG, " content progress from playbackStats "); }
        } else {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }

        if (hasContentDuration) {
            int adGroupIndexAfterPositionActualUs = adPlaybackStateActual.getAdGroupIndexAfterPositionUs(C.msToUs(contentPositionMs), C.msToUs(contentDurationMs));
            if (adGroupIndexAfterPositionActualUs != C.INDEX_UNSET) {
                long positionUs =
                        C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
                int adGroupIndexAfterPositionUs = adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs, C.msToUs(contentDurationMs));
                if (adGroupIndexAfterPositionUs != C.INDEX_UNSET && adPlaybackState.adGroups[adGroupIndexAfterPositionUs].count < 0) {
                    long nextAdTimeOffsetMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs] - positionUs);
                    if (nextAdTimeOffsetMs < NEXT_AD_DISTANCE_THRESHOLD) {
                        adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndexAfterPositionUs);
                        updateAdPlaybackState();
                        if (DEBUG)
                            Log.d(TAG, " Skipped fake cuepoint " + adGroupIndexAfterPositionUs + " -- pos: " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndexAfterPositionUs]));
                    }
                }
            }
            tryUpdateStartRequestTime(adGroupIndexAfterPositionActualUs, contentPositionMs, contentDurationMs);
        }
        long contentDurationMs = this.contentDurationMs;
        return new VideoProgressUpdate(contentPositionMs, contentDurationMs);
    }


    private VideoProgressUpdate getAdVideoProgressUpdate() {
        if (player == null) {
            return lastAdProgress;
        } else if (imaAdState != IMA_AD_STATE_NONE && playingAd) {
            long adDuration = player.getDuration();
            return adDuration == C.TIME_UNSET
                    ? VideoProgressUpdate.VIDEO_TIME_NOT_READY
                    : new VideoProgressUpdate(player.getCurrentPosition(), adDuration);
        } else {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }
    }

    private void updateAdProgress() {
        VideoProgressUpdate videoProgressUpdate = getAdVideoProgressUpdate();
        AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
        for (int i = 0; i < adCallbacks.size(); i++) {
            adCallbacks.get(i).onAdProgress(adMediaInfo, videoProgressUpdate);
        }
        handler.removeCallbacks(updateAdProgressRunnable);
        handler.postDelayed(updateAdProgressRunnable, AD_PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void stopUpdatingAdProgress() {
        handler.removeCallbacks(updateAdProgressRunnable);
    }

    private int getPlayerVolumePercent() {
        @Nullable Player player = this.player;
        if (player == null) {
            return lastVolumePercent;
        }

        @Nullable Player.AudioComponent audioComponent = player.getAudioComponent();
        if (audioComponent != null) {
            return (int) (audioComponent.getVolume() * 100);
        }

        // Check for a selected track using an audio renderer.
        TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
        for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
            if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
                return 100;
            }
        }
        return 0;
    }

    private void handlePlayerStateChanged(boolean playWhenReady, @Player.State int playbackState) {
        if (playingAd && imaAdState == IMA_AD_STATE_PLAYING) {
            if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
                AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
                for (int i = 0; i < adCallbacks.size(); i++) {
                    adCallbacks.get(i).onBuffering(adMediaInfo);
                }
                stopUpdatingAdProgress();
                bufferingAd = true;
            } else if (bufferingAd && playbackState == Player.STATE_READY) {
                bufferingAd = false;
                updateAdProgress();
            }
        }

        if (imaAdState == IMA_AD_STATE_NONE
                && playbackState == Player.STATE_BUFFERING
                && playWhenReady) {
            ensureSentContentCompleteIfAtEndOfStream();
        } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
            AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
            if (adMediaInfo == null) {
                Log.w(TAG, "onEnded without ad media info");
            } else {
                for (int i = 0; i < adCallbacks.size(); i++) {
                    adCallbacks.get(i).onEnded(adMediaInfo);
                }
            }
            if (DEBUG) {
                Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlayerStateChanged");
            }
        }
    }

    private void handleTimelineOrPositionChanged(boolean seek) {
        @Nullable Player player = this.player;
        if (adsManager == null || player == null) {
            return;
        }


        if (!playingAd && !player.isPlayingAd()) {
            ensureSentContentCompleteIfAtEndOfStream();
            if (!sentContentComplete && !timeline.isEmpty() && seek) {
                if (DEBUG) {
                    Log.d(TAG, "Ad skipped on user seek 1");
                }
                skipAdOnUserSeek(player);
            }
        }

        boolean wasPlayingAd = playingAd;
        int oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup;
        playingAd = player.isPlayingAd();
        playingAdIndexInAdGroup = playingAd ? player.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
        boolean adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup;
        if (adFinished) {
            // IMA is waiting for the ad playback to finish so invoke the callback now.
            // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
            @Nullable AdMediaInfo adMediaInfo = imaAdMediaInfo;
            if (adMediaInfo == null) {
                Log.w(TAG, "onEnded without ad media info");
            } else {
                for (int i = 0; i < adCallbacks.size(); i++) {
                    adCallbacks.get(i).onEnded(adMediaInfo);
                }
                if (adTracker != null) {
                    adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_SUCCESS, IVideoAdTracker.buildSuccessParams(IVideoAdTracker.WATCH_TIME_BASE_AD_LOADER, startLoadMediaTime, startRequestTime, startLoadMediaTime, expectedAdGroupIndex != C.INDEX_UNSET ? expectedAdGroupIndex : prevPlayedAdGroupIndex, getAdGroupCount()));
                }
            }
            if (DEBUG) {
                Log.d(TAG, "VideoAdPlayerCallback.onEnded adFinished in onTimelineChanged/onPositionDiscontinuity");
            }

        }


        if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
            int adGroupIndex = player.getCurrentAdGroupIndex();
            if (adPlaybackState.adGroupTimesUs[adGroupIndex] == C.TIME_END_OF_SOURCE) {
                sendContentComplete();
            } else if (seek) {
                if (DEBUG) {
                    Log.d(TAG, "Ad skipped on user seek 2");
                }
                skipAdOnUserSeek(player);
            }
        }


    }

    private void skipAdOnUserSeek(Player player) {
        if (!timeline.isEmpty()) {
            long positionMs = getContentPeriodPositionMs(player, timeline, period);
            timeline.getPeriod(/* periodIndex= */ 0, period);
            int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
            if (newAdGroupIndex != C.INDEX_UNSET && adPlaybackState.adGroups[newAdGroupIndex].count < 0) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(newAdGroupIndex);
                updateAdPlaybackState();
                if (DEBUG) {
                    Log.d(TAG, "Ad skipped on user seek onTimelineChanged/onPositionDiscontinuity " + newAdGroupIndex);
                }
            }
        }
    }


    private void skipAllFakeCuePoints() {
        // skip all ads
        try {
            for (int i = 0; i < adPlaybackState.adGroupTimesUs.length; i++) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
            }
            updateAdPlaybackState();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (DEBUG) {
            Log.d(TAG, " skipped all fake cue points ");
        }
    }

    private void resumeContentInternal() {
        if (DEBUG) {
            Log.d(TAG, " resumeContentInternal  ");
        }
        if (expectedAdGroupIndex != C.INDEX_UNSET){
            prevPlayedAdGroupIndex = expectedAdGroupIndex;
        }
        expectedAdGroupIndex = C.INDEX_UNSET;
        if (imaAdInfo != null) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(imaAdInfo.adGroupIndex);
            updateAdPlaybackState();
        }
        if (adPlaybackStateActual.getAdGroupIndexForPositionUs(Long.MAX_VALUE, Long.MAX_VALUE) == C.INDEX_UNSET) {
            // all ads over
            if (DEBUG) {
                Log.d(TAG, "No more ads to play ");
            }
            skipAllFakeCuePoints();
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
        stopUpdatingAdProgress();
        // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
        Assertions.checkNotNull(imaAdInfo);
        int adGroupIndex = imaAdInfo.adGroupIndex;
        int adIndexInAdGroup = imaAdInfo.adIndexInAdGroup;
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request.
            return;
        }
        if (DEBUG) {
            Log.d(TAG, " Mark ad played " + adGroupIndex);
        }
        adPlaybackState =
                adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0);
        updateAdPlaybackState();
        if (!playingAd) {
            imaAdMediaInfo = null;
            imaAdInfo = null;
        }
    }

    private void handleAdGroupLoadError(Exception error) {
        if (player == null) {
            return;
        }

        // TODO: Once IMA signals which ad group failed to load, remove this call.
        int adGroupIndex = getLoadingAdGroupIndex();
        if (adGroupIndex == C.INDEX_UNSET) {
            Log.w(TAG, "Unable to determine ad group index for ad group load error", error);
            return;
        }

        if (adTracker != null) {
            adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(IVideoAdTracker.WATCH_TIME_BASE_AD_LOADER, adGroupIndex, startRequestTime, error, getAdGroupCount()));
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
            pendingAdPrepareErrorAdInfo = new MxAdPlaybackState.AdInfo(adGroupIndex, adIndexInAdGroup);
        } else {
            AdMediaInfo adMediaInfo = Assertions.checkNotNull(imaAdMediaInfo);
            // We're already playing an ad.
            if (adIndexInAdGroup > playingAdIndexInAdGroup) {
                // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
                // which means that the ad after will load (if any).
                for (int i = 0; i < adCallbacks.size(); i++) {
                    adCallbacks.get(i).onEnded(adMediaInfo);
                }
            }
            playingAdIndexInAdGroup = adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
            for (int i = 0; i < adCallbacks.size(); i++) {
                adCallbacks.get(i).onError(Assertions.checkNotNull(adMediaInfo));
            }
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
        updateAdPlaybackState();
        if (adTracker != null) {
            adTracker.trackEvent(IVideoAdTracker.EVENT_VIDEO_AD_PLAY_FAILED, IVideoAdTracker.buildFailedParams(IVideoAdTracker.WATCH_TIME_BASE_AD_LOADER, expectedAdGroupIndex, adIndexInAdGroup, startRequestTime, exception, getAdGroupCount()));
        }
    }

    private int getAdGroupCount() {
        return adsManager != null && adsManager.getAdCuePoints() != null ? adsManager.getAdCuePoints().size() : -1;
    }

    private void ensureSentContentCompleteIfAtEndOfStream() {
        if (!sentContentComplete
                && contentDurationMs != C.TIME_UNSET
                && pendingContentPositionMs == C.TIME_UNSET
                && getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period)
                + THRESHOLD_END_OF_CONTENT_MS
                >= contentDurationMs) {
            sendContentComplete();
        }
    }

    private void sendContentComplete() {
        adsLoader.contentComplete();
        sentContentComplete = true;
        if (DEBUG) {
            Log.d(TAG, "adsLoader.contentComplete");
        }
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
            if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(/* adGroupIndex= */ i);
            }
        }
        updateAdPlaybackState();
    }

    private void updateAdPlaybackState() {
        // Ignore updates while detached. When a player is attached it will receive the latest state.
        if (eventListener != null) {
            eventListener.onAdPlaybackState(adPlaybackState);
        }
    }

    private void maybeNotifyPendingAdLoadError() {
        if (pendingAdLoadError != null && eventListener != null) {
            eventListener.onAdLoadError(pendingAdLoadError, getAdsDataSpec(adTagUri));
            pendingAdLoadError = null;
        }
    }

    private void maybeNotifyInternalError(String name, Exception cause) {
        String message = "Internal error in " + name;
        Log.e(TAG, message, cause);
        // We can't recover from an unexpected error in general, so skip all remaining ads.
        for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
        }
        updateAdPlaybackState();
        if (eventListener != null) {
            eventListener.onAdLoadError(
                    AdsMediaSource.AdLoadException.createForUnexpected(new RuntimeException(message, cause)),
                    getAdsDataSpec(adTagUri));
        }
    }

    private int getFakeAdGroupIndexForAdPod(AdPodInfo adPodInfo) {
        if (adPodInfo.getPodIndex() == -1) {
            // This is a postroll ad.
            return adPlaybackState.adGroupCount - 1;
        }

        long positionUs =
                C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
        if (DEBUG) {
            Log.d(TAG, " Player position " + C.usToMs(positionUs));
        }
        int adGroupIndex = getFakeCuepointForLoadingAd(positionUs);
        if (adGroupIndex != C.INDEX_UNSET) return adGroupIndex;

        throw new IllegalStateException("Failed to find cue point");
    }

    private int getFakeCuepointForLoadingAd(long positionUs) {
        int adGroupIndex = getLoadingAdGroupIndex();
        if (adGroupIndex != C.INDEX_UNSET) {
            long timeLeftMs = C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex] - positionUs);
            if (timeLeftMs > 0 && timeLeftMs < NEXT_FAKE_CUEPOINTS_DISTANCE_THRESHOLD) {
                if (DEBUG) {
                    Log.d(TAG, "Next cue-point too close  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs);
                }
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex);
                return getFakeCuepointForLoadingAd(positionUs);
            }
            if (DEBUG) {
                Log.d(TAG, " Loading  Next Ad  " + adGroupIndex + " time : " + C.usToMs(adPlaybackState.adGroupTimesUs[adGroupIndex]) + " time left : " + timeLeftMs);
            }
            return adGroupIndex;
        }
        return C.INDEX_UNSET;
    }


    private int getAdGroupIndexForAdPod(AdPodInfo adPodInfo) {
        if (adPodInfo.getPodIndex() == -1) {
            // This is a postroll ad.
            return adPlaybackStateActual.adGroupCount - 1;
        }

        // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
        // We receive cue points from IMA SDK as floats. This code replicates the same calculation used
        // to populate adGroupTimesUs (having truncated input back to float, to avoid failures if the
        // behavior of the IMA SDK changes to provide greater precision in AdPodInfo).
        long adPodTimeUs =
                Math.round((double) ((float) adPodInfo.getTimeOffset()) * C.MICROS_PER_SECOND);
        for (int adGroupIndex = 0; adGroupIndex < adPlaybackStateActual.adGroupCount; adGroupIndex++) {
            long adGroupTimeUs = adPlaybackStateActual.adGroupTimesUs[adGroupIndex];
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                    && Math.abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US) {
                return adGroupIndex;
            }
        }

        throw new IllegalStateException("Failed to find cue point");
    }


    /**
     * Returns the index of the ad group that will preload next, or {@link C#INDEX_UNSET} if there is
     * no such ad group.
     */
    private int getLoadingAdGroupIndex() {
        long playerPositionUs =
                C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
        int adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, C.msToUs(contentDurationMs));
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex =
                    adPlaybackState.getAdGroupIndexAfterPositionUs(
                            playerPositionUs, C.msToUs(contentDurationMs));
        }
        return adGroupIndex;
    }

    private String getAdMediaInfoString(AdMediaInfo adMediaInfo) {
        @Nullable MxAdPlaybackState.AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
        return "AdMediaInfo[" + adMediaInfo.getUrl() + (adInfo != null ? ", " + adInfo : "") + "]";
    }

    private static DataSpec getAdsDataSpec(@Nullable Uri adTagUri) {
        return new DataSpec(adTagUri != null ? adTagUri : Uri.EMPTY);
    }

    private static long getContentPeriodPositionMs(
            Player player, Timeline timeline, Timeline.Period period) {
        long contentWindowPositionMs = player.getContentPosition();
        return contentWindowPositionMs
                - (timeline.isEmpty()
                ? 0
                : timeline.getPeriod(/* periodIndex= */ 0, period).getPositionInWindowMs());
    }

    private static boolean isAdGroupLoadError(AdError adError) {
        // TODO: Find out what other errors need to be handled (if any), and whether each one relates to
        // a single ad, ad group or the whole timeline.
        return adError.getErrorCode() == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH
                || adError.getErrorCode() == AdError.AdErrorCode.UNKNOWN_ERROR;
    }

    private static Looper getImaLooper() {
        // IMA SDK callbacks occur on the main thread. This method can be used to check that the player
        // is using the same looper, to ensure all interaction with this class is on the main thread.
        return Looper.getMainLooper();
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

    private void destroyAdsManager() {
        if (adsManager != null) {
            adsManager.removeAdErrorListener(componentListener);
            adsManager.removeAdEventListener(componentListener);
            if (adEventListener != null) {
                adsManager.removeAdEventListener(adEventListener);
            }
            adsManager.destroy();
            adsManager = null;
            adLoaderInputs.getImaCustomUiController().releaseAdManager();
        }
    }

    private List<Float> generateFakeCuePoints(AdsManager adsManager) {
        List<Float> originalCuepoints = adsManager.getAdCuePoints();
        List<Float> fakeCuePoints = new ArrayList<>();
        for (int i = 0; i < originalCuepoints.size(); i++) {
            float cuePoint = originalCuepoints.get(i);
            if (cuePoint == -1.0 || cuePoint == 0) {
                fakeCuePoints.add(cuePoint);
            }
        }
        int fakeCuePointsDistance = fakeCuePointsDistanceMs/1000;
        float start = fakeCuePointsDistance;
        for (int i = 0; i < adLoaderInputs.getVideoDuration() / fakeCuePointsDistance + 1; i++) {
            fakeCuePoints.add(start);
            start += fakeCuePointsDistance;
        }
        return fakeCuePoints;
    }



    private final class ComponentListener
            implements VideoAdPlayer,
            ContentProgressProvider,
            AdErrorEvent.AdErrorListener,
            com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener,
            AdEvent.AdEventListener {

        // com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener implementation.

        @Override
        public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
            removeTimeoutCallback();
            AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();
            if (!Util.areEqual(pendingAdRequestContext, adsManagerLoadedEvent.getUserRequestContext())) {
                adsManager.destroy();
                return;
            }
            pendingAdRequestContext = null;
            WatchTimeBaseAdLoader.this.adsManager = adsManager;
            adPlaybackStateActual = new MxAdPlaybackState(AdPlaybackStateFactory.fromCuePoints(adsManager.getAdCuePoints()).adGroupTimesUs);
            if (DEBUG) {
                Log.d(TAG, "onAdsManagerLoaded : " + TextUtils.join(" , ", adsManager.getAdCuePoints()));
            }
            adLoaderInputs.getImaCustomUiController().setAdsManager(adsManager);
            adsManager.addAdErrorListener(this);
            adsManager.addAdEventListener(this);
            if (adEventListener != null) {
                adsManager.addAdEventListener(adEventListener);
            }
            if (player != null) {
                // If a player is attached already, start playback immediately.
                try {
                    List<Float> fakeCuePoints = generateFakeCuePoints(adsManager);
                    adPlaybackState = new MxAdPlaybackState(AdPlaybackStateFactory.fromCuePoints(fakeCuePoints).adGroupTimesUs);
                    if (DEBUG) {
                        Log.d(TAG, "onAdsManagerLoaded : fake " + TextUtils.join(" , ", fakeCuePoints));
                    }
                    hasAdPlaybackState = true;
                    updateAdPlaybackState();
                } catch (RuntimeException e) {
                    maybeNotifyInternalError("onAdsManagerLoaded", e);
                }
            }
        }


        // AdEvent.AdEventListener implementation.

        @Override
        public void onAdEvent(AdEvent adEvent) {
            AdEvent.AdEventType adEventType = adEvent.getType();
            if (DEBUG && adEventType != AdEvent.AdEventType.AD_PROGRESS) {
                Log.d(TAG, "onAdEvent: " + adEventType);
            }
            if (adsManager == null) {
                // Drop events after release.
                return;
            }
            try {
                handleAdEvent(adEvent);
            } catch (RuntimeException e) {
                maybeNotifyInternalError("onAdEvent", e);
            }
        }

        // AdErrorEvent.AdErrorListener implementation.

        @Override
        public void onAdError(AdErrorEvent adErrorEvent) {
            removeTimeoutCallback();
            AdError error = adErrorEvent.getError();
            if (DEBUG) {
                Log.d(TAG, "onAdError", error);
            }

            if (adsManager == null) {
                // No ads were loaded, so allow playback to start without any ads.
                pendingAdRequestContext = null;
                adPlaybackState = AdPlaybackState.NONE;
                adPlaybackStateActual = AdPlaybackState.NONE;
                hasAdPlaybackState = true;
                updateAdPlaybackState();
            } else if (isAdGroupLoadError(error)) {
                try {
                    handleAdGroupLoadError(error);
                } catch (RuntimeException e) {
                    maybeNotifyInternalError("onAdError", e);
                }
            }
            if (pendingAdLoadError == null) {
                pendingAdLoadError = AdsMediaSource.AdLoadException.createForAllAds(error);
            }
            maybeNotifyPendingAdLoadError();
        }

        // ContentProgressProvider implementation.

        @Override
        public VideoProgressUpdate getContentProgress() {
            VideoProgressUpdate videoProgressUpdate = getContentVideoProgressUpdate();
            if (DEBUG) {
                Log.d(TAG, "Content progress: " + videoProgressUpdate);
            }

            if (waitingForPreloadElapsedRealtimeMs != C.TIME_UNSET) {
                // IMA is polling the player position but we are buffering for an ad to preload, so playback
                // may be stuck. Detect this case and signal an error if applicable.
                long stuckElapsedRealtimeMs =
                        SystemClock.elapsedRealtime() - waitingForPreloadElapsedRealtimeMs;
                if (stuckElapsedRealtimeMs >= THRESHOLD_AD_PRELOAD_MS) {
                    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET;
                    handleAdGroupLoadError(new IOException("Ad preloading timed out"));
                    maybeNotifyPendingAdLoadError();
                }
            }

            return videoProgressUpdate;
        }

        // VideoAdPlayer implementation.

        @Override
        public VideoProgressUpdate getAdProgress() {
            throw new IllegalStateException("Unexpected call to getAdProgress when using preloading");
        }

        @Override
        public int getVolume() {
            return getPlayerVolumePercent();
        }

        @Override
        public void loadAd(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
            try {
                if (adsManager == null) {
                    // Drop events after release.
                    if (DEBUG) {
                        Log.d(
                                TAG,
                                "loadAd after release "
                                        + getAdMediaInfoString(adMediaInfo)
                                        + ", ad pod "
                                        + adPodInfo);
                    }
                    return;
                }

                int fakeAdGroupIndex = getFakeAdGroupIndexForAdPod(adPodInfo);
                int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
                MxAdPlaybackState.AdInfo adInfo = new MxAdPlaybackState.AdInfo(fakeAdGroupIndex, adIndexInAdGroup);
                adInfoByAdMediaInfo.put(adMediaInfo, adInfo);

                if (DEBUG) {
                    Log.d(TAG, "loadAd " + getAdMediaInfoString(adMediaInfo));
                }
                if (adPlaybackState.isAdInErrorState(fakeAdGroupIndex, adIndexInAdGroup)) {
                    // We have already marked this ad as having failed to load, so ignore the request. IMA
                    // will timeout after its media load timeout.
                    return;
                }

                AdPlaybackState.AdGroup adGroup = adPlaybackState.adGroups[adInfo.adGroupIndex];

                adPlaybackState =
                        adPlaybackState.withAdCount(
                                adInfo.adGroupIndex, Math.max(adPodInfo.getTotalAds(), adGroup.states.length));
                Uri adUri = Uri.parse(adMediaInfo.getUrl());
                adPlaybackState =
                        adPlaybackState.withAdUri(adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adUri);
                updateAdPlaybackState();
                startLoadMediaTime = System.currentTimeMillis();
            } catch (RuntimeException e) {
                maybeNotifyInternalError("loadAd", e);
            } finally {
                updateActualAdPlaybackState(adMediaInfo, adPodInfo);
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
        public void playAd(AdMediaInfo adMediaInfo) {
            if (DEBUG) {
                long positionUs =
                        C.msToUs(getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period));
                Log.d(TAG, "playAd " + getAdMediaInfoString(adMediaInfo) + " Player position "+ C.usToMs(positionUs));
            }
            if (adsManager == null) {
                // Drop events after release.
                return;
            }

            if (imaAdState == IMA_AD_STATE_PLAYING) {
                // IMA does not always call stopAd before resuming content.
                // See [Internal: b/38354028].
                Log.w(TAG, "Unexpected playAd without stopAd");
            }


            try {
                if (imaAdState == IMA_AD_STATE_NONE) {
                    // IMA is requesting to play the ad, so stop faking the content position.
                    fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET;
                    fakeContentProgressOffsetMs = C.TIME_UNSET;
                    imaAdState = IMA_AD_STATE_PLAYING;
                    imaAdMediaInfo = adMediaInfo;
                    imaAdInfo = Assertions.checkNotNull(adInfoByAdMediaInfo.get(adMediaInfo));
                    for (int i = 0; i < adCallbacks.size(); i++) {
                        adCallbacks.get(i).onPlay(adMediaInfo);
                    }
                    if (pendingAdPrepareErrorAdInfo != null
                            && pendingAdPrepareErrorAdInfo.equals(imaAdInfo)) {
                        pendingAdPrepareErrorAdInfo = null;
                        for (int i = 0; i < adCallbacks.size(); i++) {
                            adCallbacks.get(i).onError(adMediaInfo);
                        }
                    }
                    updateAdProgress();
                } else {
                    imaAdState = IMA_AD_STATE_PLAYING;
                    Assertions.checkState(adMediaInfo.equals(imaAdMediaInfo));
                    for (int i = 0; i < adCallbacks.size(); i++) {
                        adCallbacks.get(i).onResume(adMediaInfo);
                    }
                }
                if (!Assertions.checkNotNull(player).getPlayWhenReady()) {
                    Assertions.checkNotNull(adsManager).pause();
                }

            } catch (RuntimeException e) {
                maybeNotifyInternalError("playAd", e);
            }
        }

        @Override
        public void stopAd(AdMediaInfo adMediaInfo) {
            if (DEBUG) {
                Log.d(TAG, "stopAd " + getAdMediaInfoString(adMediaInfo));
            }
            if (adsManager == null) {
                // Drop event after release.
                return;
            }
            if (imaAdState == IMA_AD_STATE_NONE) {
                // This method is called if loadAd has been called but the preloaded ad won't play due to a
                // seek to a different position, so drop the event and discard the ad. See also [Internal:
                // b/159111848].
                @Nullable MxAdPlaybackState.AdInfo adInfo = adInfoByAdMediaInfo.get(adMediaInfo);
                if (adInfo != null) {
                    adPlaybackState =
                            adPlaybackState.withSkippedAd(adInfo.adGroupIndex, adInfo.adIndexInAdGroup);
                    updateAdPlaybackState();
                }
                return;
            }

            try {
                Assertions.checkNotNull(player);
                stopAdInternal();
            } catch (RuntimeException e) {
                maybeNotifyInternalError("stopAd", e);
            }
        }

        @Override
        public void pauseAd(AdMediaInfo adMediaInfo) {
            if (DEBUG) {
                Log.d(TAG, "pauseAd " + getAdMediaInfoString(adMediaInfo));
            }
            if (adsManager == null) {
                // Drop event after release.
                return;
            }
            if (imaAdState == IMA_AD_STATE_NONE) {
                // This method is called if loadAd has been called but the loaded ad won't play due to a
                // seek to a different position, so drop the event. See also [Internal: b/159111848].
                return;
            }

            try {
                Assertions.checkState(adMediaInfo.equals(imaAdMediaInfo));
                imaAdState = IMA_AD_STATE_PAUSED;
                for (int i = 0; i < adCallbacks.size(); i++) {
                    adCallbacks.get(i).onPause(adMediaInfo);
                }
            } catch (RuntimeException e) {
                maybeNotifyInternalError("pauseAd", e);
            }
        }

        @Override
        public void release() {
            // Do nothing.
        }
    }

    private void updateActualAdPlaybackState(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
        try {
            int adGroupIndexForAdPod = getAdGroupIndexForAdPod(adPodInfo);
            if (adGroupIndexForAdPod != C.INDEX_UNSET) {
                int adIndexInAdGroup = adPodInfo.getAdPosition() - 1;
                AdPlaybackState.AdGroup adGroup = adPlaybackStateActual.adGroups[adGroupIndexForAdPod];
                adPlaybackStateActual =
                    adPlaybackStateActual.withAdCount(
                                adGroupIndexForAdPod, Math.max(adPodInfo.getTotalAds(), adGroup.states.length));
                Uri adUri = Uri.parse(adMediaInfo.getUrl());
                adPlaybackStateActual =
                    adPlaybackStateActual.withAdUri(adGroupIndexForAdPod, adIndexInAdGroup, adUri);
                adPlaybackStateActual = adPlaybackStateActual.withPlayedAd(adGroupIndexForAdPod, adIndexInAdGroup);
                expectedAdGroupIndex = adGroupIndexForAdPod;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
