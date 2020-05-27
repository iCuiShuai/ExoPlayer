/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.util.Assertions;
import java.util.Arrays;

/**
 * A buffer that fills itself with data corresponding to a specific OBU unit, as it is
 * encountered in the stream.
 */
/* package */ final class AV1UnitTargetBuffer {

  private final int targetType;

  private boolean isFilling;
  private boolean isCompleted;

  public byte[] obuData;
  public int obuLength;

  public AV1UnitTargetBuffer(int targetType, int initialCapacity) {
    this.targetType = targetType;

    obuData = new byte[initialCapacity];
  }

  /**
   * Resets the buffer, clearing any data that it holds.
   */
  public void reset() {
    isFilling = false;
    isCompleted = false;
  }

  /**
   * Returns whether the buffer currently holds a complete OBU unit of the target type.
   */
  public boolean isCompleted() {
    return isCompleted;
  }

  /**
   * Called to indicate that a OBU unit has started.
   *
   * @param type The type of the OBU unit.
   */
  public void startObuUnit(int type) {
    Assertions.checkState(!isFilling);
    isFilling = type == targetType;
    if (isFilling) {
      obuLength = 0;
      isCompleted = false;
    }
  }

  /**
   * Called to pass stream data.
   *
   * @param data Holds the data being passed.
   * @param offset The offset of the data in {@code data}.
   * @param limit The limit (exclusive) of the data in {@code data}.
   */
  public void appendToObuUnit(byte[] data, int offset, int limit) {
    if (!isFilling) {
      return;
    }
    int readLength = limit - offset;
    if (obuData.length < obuLength + readLength) {
      obuData = Arrays.copyOf(obuData, (obuLength + readLength) * 2);
    }
    System.arraycopy(data, offset, obuData, obuLength, readLength);
    obuLength += readLength;
  }

  /**
   * Called to indicate that a OBU unit has ended.
   *
   * @param discardPadding The number of excess bytes that were passed to
   *     {@link #appendToObuUnit(byte[], int, int)}, which should be discarded.
   * @return Whether the ended OBU unit is of the target type.
   */
  public boolean endObuUnit(int discardPadding) {
    if (!isFilling) {
      return false;
    }
    obuLength -= discardPadding;
    isFilling = false;
    isCompleted = true;
    return true;
  }

}
