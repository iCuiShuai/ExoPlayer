/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.vvc;

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Log;

/** Configures and queries the underlying native library. */
public final class VVCLibrary {
  private static final String TAG = "VVCLibrary";
  private static boolean isAvailable = false;
  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.vvc1");
  }

  private static final LibraryLoader LOADER = new LibraryLoader("mxvvcdec");

  private VVCLibrary() {}

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean nativeInit(byte[]vvcConfig, int length) {
    try {
      VVCDecoder.updateVVCConfig(vvcConfig, length);
      if(!isAvailable) {
        VVCDecoder.nativeInit();
        isAvailable = true;
      }
    } catch (Throwable ignore) {
      Log.w(TAG, "Failed to init vvclibrary ");
    }
    return isAvailable;
  }

  public static boolean isAvailable() {
    return isAvailable;
  }

  /** Returns whether the underlying library is available, loading it if necessary. */
//  public static boolean nativeInit() {
//    if(isAvailable) {
//      VVCDecoder.nativeInit();
//    }
//
//    return isAvailable;
//  }
//
//  public static boolean isAvailable() {
//    if(isAvailable) {
//      return isAvailable;
//    }
//    isAvailable =  LOADER.isAvailable();
//    nativeInit();
//    return isAvailable;
//  }

  /**
   * @return true library can be used
   */
  public static boolean isLinked() {
    try {
      if (!isAvailable()) return false;
      VVCDecoder.vvcCheckLibrary();
      return true;
    } catch (Throwable ignore) {
    }
    return false;
  }
}
