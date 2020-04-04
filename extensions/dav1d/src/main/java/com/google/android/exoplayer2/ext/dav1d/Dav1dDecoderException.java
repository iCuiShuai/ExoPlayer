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
package com.google.android.exoplayer2.ext.dav1d;

import com.google.android.exoplayer2.video.VideoDecoderException;

/** Thrown when a libgav1 decoder error occurs. */
public final class Dav1dDecoderException extends VideoDecoderException {

  /* package */ Dav1dDecoderException(String message) {
    super(message);
  }

  /* package */ Dav1dDecoderException(String message, Throwable cause) {
    super(message, cause);
  }
}
