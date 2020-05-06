package com.google.android.exoplayer2.drm;

import android.os.Looper;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SessionUtil {

  /** {@link DrmSessionManager} that supports no DRM schemes. */
  public static final DrmSessionManager<ExoMediaCrypto> DUMMY =
      new DrmSessionManager<ExoMediaCrypto>() {

        @Override
        public boolean canAcquireSession(DrmInitData drmInitData) {
          return false;
        }

        @Override
        public DrmSession<ExoMediaCrypto> acquireSession(
            Looper playbackLooper, DrmInitData drmInitData) {
          return new ErrorStateDrmSession<>(
              new DrmSession.DrmSessionException(
                  new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME)));
        }

        @Override
        @Nullable
        public Class<ExoMediaCrypto> getExoMediaCryptoType(DrmInitData drmInitData) {
          return null;
        }
      };

  /** Returns {@link #DUMMY}. */
  public static <T extends ExoMediaCrypto> DrmSessionManager<T> getDummyDrmSessionManager() {
    return (DrmSessionManager<T>) DUMMY;
  }
}
