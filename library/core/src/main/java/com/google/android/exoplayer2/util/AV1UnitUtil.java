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
package com.google.android.exoplayer2.util;

/**
 * Utility methods for handling AV1 OBU units.
 */
public final class AV1UnitUtil {

  public static final String TAG = "AV1UnitUtil";
  public static final int AV1_ADAPTIVE = 2;
  public static final int AV1_COLOR_PRI_BT709 = 1;
  public static final int AV1_COLOR_PRI_UNKNOWN = 2;
  public static final int AV1_TRC_UNKNOWN = 2;
  public static final int AV1_TRC_SRGB = 13;
  public static final int AV1_MC_IDENTITY = 0;
  public static final int AV1_MC_UNKNOWN = 2;
  public static final int AV1_PIXEL_LAYOUT_I400 = 0; ///< monochrome
  public static final int AV1_PIXEL_LAYOUT_I420 = 1; ///< 4:2:0 planar
  public static final int AV1_PIXEL_LAYOUT_I422 = 2; ///< 4:2:2 planar
  public static final int AV1_PIXEL_LAYOUT_I444 = 3; ///< 4:4:4 planar
  public static final int AV1_CHR_UNKNOWN = 0;

  // Constants from Section 3. "Symbols and abbreviated terms"
  public static final int AV1_MAX_CDEF_STRENGTHS = 8;
  public static final int AV1_MAX_OPERATING_POINTS = 32;
  public static final int AV1_MAX_TILE_COLS = 64;
  public static final int AV1_MAX_TILE_ROWS = 64;
  public static final int AV1_MAX_SEGMENTS = 8;
  public static final int AV1_NUM_REF_FRAMES = 8;
  public static final int AV1_PRIMARY_REF_NONE = 7;
  public static final int AV1_REFS_PER_FRAME = 7;
  public static final int AV1_TOTAL_REFS_PER_FRAME = (AV1_REFS_PER_FRAME + 1);

  public static final class OBUType {
    public static final int OBU_SEQ_HDR   = 1;
    public static final int OBU_TD        = 2;
    public static final int OBU_FRAME_HDR = 3;
    public static final int OBU_TILE_GRP  = 4;
    public static final int OBU_METADATA  = 5;
    public static final int OBU_FRAME     = 6;
    public static final int OBU_REDUNDANT_FRAME_HDR = 7;
    public static final int OBU_PADDING   = 15;
  }

  public static final class FrameType {
    public static final int AV1_FRAME_TYPE_NO_FRAME = -1;    ///< Key Intra frame
    public static final int AV1_FRAME_TYPE_KEY = 0;    ///< Key Intra frame
    public static final int AV1_FRAME_TYPE_INTER = 1;  ///< Inter frame
    public static final int AV1_FRAME_TYPE_INTRA = 2;  ///< Non key Intra frame
    public static final int AV1_FRAME_TYPE_SWITCH = 3; ///< Switch Inter frame
  }

  public static class SequenceHeaderOperatingPoint {
    public void SequenceHeaderOperatingPoint() {

    }
    public int majorLevel, minorLevel;
    public int initialDisplayDelay;
    public int idc;
    public int tier;
    public int decoderModelParamPresent;
    public int displayModelParamPresent;
  }

  public static class SequenceHeaderOperatingParameterInfo {
    public void SequenceHeaderOperatingParameterInfo() {

    }
    public int decoderBufferDelay;
    public int encoderBufferDelay;
    public int lowDelayMode;
  }

  public static final class ObuHeader {
    public final boolean valid;
    public final int obuType;
    public final long obuLength;
    public final int temporalId;
    public final int spatialId;

    public ObuHeader(
        boolean valid,
        int obuType,
        long obuLength,
        int temporalId,
        int spatialId
    ) {
      this.valid = valid;
      this.obuType = obuType;
      this.obuLength = obuLength;
      this.temporalId = temporalId;
      this.spatialId = spatialId;
    }
  }
  /**
   * Holds data parsed from a sequence header set obu unit.
   */
  public static final class SequenceHeader {
    /**
     * Stream profile, 0 for 8-10 bits/component 4:2:0 or monochrome;
     * 1 for 8-10 bits/component 4:4:4; 2 for 4:2:2 at any bits/component,
     * or 12 bits/component at any chroma subsampling.
     */
    public final int profileIdc;
    public final int reducedStillPictureHeader;
    public final int decoderModelInfoPresent;
    public final int equalPictureInterval;
    public final int framePresentationDelayLength;
    public final int frameIdNumbersPresent;
    public final int frameIdNBits;
    public final int deltaFrameIdNBits;
    public final int screenContentTools;
    public final int forceIntegerMv;
    public final int orderHint;
    public final int orderHintNBits;
    public final int numOperatingPoints;
    public final int bufferRemovalDelayLength;
    public final int constraintsFlagsAndReservedZero2Bits;
    public final int levelIdc;
    public final int seqParameterSetId;
    public final int width;
    public final int height;
    public final float pixelWidthAspectRatio;
    public final boolean separateColorPlaneFlag;
    public final boolean frameMbsOnlyFlag;

    public final SequenceHeaderOperatingPoint[] operatingPoints;
    public final SequenceHeaderOperatingParameterInfo[] operatingParameterInfos;

    public SequenceHeader(
        int profileIdc,
        int reducedStillPictureHeader,
        int decoderModelInfoPresent,
        int equalPictureInterval,
        int framePresentationDelayLength,
        int frameIdNumbersPresent,
        int frameIdNBits,
        int deltaFrameIdNBits,
        int screenContentTools,
        int forceIntegerMv,
        int orderHint,
        int orderHintNBits,
        int numOperatingPoints,
        int bufferRemovalDelayLength,
        int constraintsFlagsAndReservedZero2Bits,
        int levelIdc,
        int seqParameterSetId,
        int width,
        int height,
        float pixelWidthAspectRatio,
        boolean separateColorPlaneFlag,
        boolean frameMbsOnlyFlag,
        SequenceHeaderOperatingPoint[] operatingPoints,
        SequenceHeaderOperatingParameterInfo[] operatingParameterInfos) {
      this.profileIdc = profileIdc;
      this.reducedStillPictureHeader = reducedStillPictureHeader;
      this.decoderModelInfoPresent = decoderModelInfoPresent;
      this.equalPictureInterval = equalPictureInterval;
      this.framePresentationDelayLength = framePresentationDelayLength;
      this.frameIdNumbersPresent = frameIdNumbersPresent;
      this.frameIdNBits = frameIdNBits;
      this.deltaFrameIdNBits = deltaFrameIdNBits;
      this.screenContentTools = screenContentTools;
      this.forceIntegerMv = forceIntegerMv;
      this.orderHint = orderHint;
      this.orderHintNBits = orderHintNBits;
      this.numOperatingPoints = numOperatingPoints;
      this.bufferRemovalDelayLength = bufferRemovalDelayLength;
      this.constraintsFlagsAndReservedZero2Bits = constraintsFlagsAndReservedZero2Bits;
      this.levelIdc = levelIdc;
      this.seqParameterSetId = seqParameterSetId;
      this.width = width;
      this.height = height;
      this.pixelWidthAspectRatio = pixelWidthAspectRatio;
      this.separateColorPlaneFlag = separateColorPlaneFlag;
      this.frameMbsOnlyFlag = frameMbsOnlyFlag;
      this.operatingPoints = operatingPoints;
      this.operatingParameterInfos = operatingParameterInfos;
    }

  }

  /**
   * Parses an SequenceHeader OBU unit using the syntax defined in AV1 specification
   * Version 1.0.0
   *
   * @param obuData A buffer containing sequence header.
   * @param obuOffset The offset of the obu unit header in {@code obuData}.
   * @param obuLimit The limit of the obu unit in {@code obuData}.
   * @return A parsed representation of the sequence header data.
   */
  public static SequenceHeader parseSequenceHeader(byte[] obuData, int obuOffset, int obuLimit) {
    ParsableBitArray data = new ParsableBitArray(obuData, obuLimit);
    ObuHeader obuHeader = obuUnitStart(data, 0, 10);

    int profileIdc = data.readBits(3);
    if (profileIdc > 2) {
      return null;
    }

    int stillPicture = data.readBits(1);
    int reducedStillPictureHeader = data.readBits(1);

    if (reducedStillPictureHeader > 0 && stillPicture == 0) {
      return null;
    }
    int decoderModelInfoPresent = 0;
    int equalPictureInterval = 0;
    int framePresentationDelayLength = 0;
    int numOperatingPoints = 0;
    int bufferRemovalDelayLength = 0;
    SequenceHeaderOperatingPoint[] operatingPoints= new SequenceHeaderOperatingPoint[AV1_MAX_OPERATING_POINTS];
    SequenceHeaderOperatingParameterInfo[] operatingParameterInfos= new SequenceHeaderOperatingParameterInfo[AV1_MAX_OPERATING_POINTS];
    if (reducedStillPictureHeader > 0) {
      operatingPoints[0] = new SequenceHeaderOperatingPoint();
      int timingInfoPresent = 0;
      decoderModelInfoPresent = 0;
      int displayModelInfoPresent = 0;
      numOperatingPoints = 1;
      int idc = 0;
      operatingPoints[0].majorLevel = data.readBits(3);
      operatingPoints[0].minorLevel = data.readBits(2);
      int tier = 0;
      int decoderModelParamPresent = 0;
      int displayModelParamPresent = 0;
    } else {
      int timingInfoPresent = data.readBits(1);
      decoderModelInfoPresent = 0;
      int encoderDecoderBufferDelayLength = 0;
      if (timingInfoPresent > 0) {
        int numUnitsInTick = data.readBits(32);
        int timeScale = data.readBits(32);
        equalPictureInterval = data.readBits(1);
        if (equalPictureInterval > 0) {
                long numTicksPerPicture = data.readUnsignedVLCInt();
          if (numTicksPerPicture == 0xFFFFFFFF) {
            return null;
          }
          numTicksPerPicture = numTicksPerPicture + 1;
        }

        decoderModelInfoPresent = data.readBits(1);
        if (decoderModelInfoPresent > 0) {
          encoderDecoderBufferDelayLength = data.readBits(5) + 1;
          int numUnitsInDecodingTick = data.readBits(32);
          bufferRemovalDelayLength = data.readBits(5) + 1;
          framePresentationDelayLength = data.readBits(5) + 1;
        }
      } else {
        decoderModelInfoPresent = 0;
      }

      int displayModelInfoPresent = data.readBits(1);
      numOperatingPoints = data.readBits(5) + 1;
      for (int i = 0; i < numOperatingPoints; i++) {
        SequenceHeaderOperatingPoint operatingPoint = new SequenceHeaderOperatingPoint();
        operatingPoint.idc = data.readBits(12);
        operatingPoint.majorLevel = 2 + data.readBits(3);
        operatingPoint.minorLevel = data.readBits(2);
        operatingPoint.tier = operatingPoint.majorLevel > 3 ? data.readBits(1) : 0;
        operatingPoint.decoderModelParamPresent = decoderModelInfoPresent > 0 ? data.readBits(1) : 0;
        if (operatingPoint.decoderModelParamPresent > 0) {
          SequenceHeaderOperatingParameterInfo operatingParameterInfo = new SequenceHeaderOperatingParameterInfo();
          operatingParameterInfo.decoderBufferDelay = data.readBits(encoderDecoderBufferDelayLength);
          operatingParameterInfo.encoderBufferDelay = data.readBits(encoderDecoderBufferDelayLength);
          operatingParameterInfo.lowDelayMode = data.readBits(1);
          operatingParameterInfos[i] = operatingParameterInfo;
        }
        operatingPoint.displayModelParamPresent = displayModelInfoPresent > 0 ? data.readBits(1) : 0;
        if (operatingPoint.displayModelParamPresent > 0) {
          operatingPoint.initialDisplayDelay = data.readBits(4) + 1;
        }
        operatingPoints[i] = operatingPoint;
      }
    }

    int widthNBits = data.readBits(4) + 1;
    int heightNBits = data.readBits(4) + 1;
    int maxWidth = data.readBits(widthNBits) + 1;
    int maxHeight = data.readBits(heightNBits) + 1;
    int frameIdNumbersPresent = reducedStillPictureHeader > 0 ? 0 : data.readBits(1);
    int frameIdNBits = 0;
    int deltaFrameIdNBits = 0;
    if (frameIdNumbersPresent > 0) {
      deltaFrameIdNBits = data.readBits(4) + 2;
      frameIdNBits = data.readBits(3) + deltaFrameIdNBits + 1;
    }

    int sb128 = data.readBits(1);
    int filterIntra = data.readBits(1);
    int intraEdgeFilter = data.readBits(1);
    int screenContentTools = 0;
    int forceIntegerMv = 0;
    int orderHint = 0;
    int orderHintNBits = 0;
    if (reducedStillPictureHeader > 0) {
      int interIntra = 0;
      int maskedCompound = 0;
      int warpedMotion = 0;
      int dualFilter = 0;
      orderHint = 0;
      int jntComp = 0;
      int refFrameMvs = 0;
      orderHintNBits = 0;
      screenContentTools = AV1_ADAPTIVE;
      forceIntegerMv = AV1_ADAPTIVE;
    } else {
      int interIntra = data.readBits(1);
      int maskedCompound = data.readBits(1);
      int warpedMotion = data.readBits(1);
      int dualFilter = data.readBits(1);
      orderHint = data.readBits(1);
      if (orderHint > 0) {
        int jntComp = data.readBits(1);
        int refFrameMvs = data.readBits(1);
      } else {
        int jntComp = 0;
        int refFrameMvs = 0;
        orderHintNBits = 0;
      }
      screenContentTools = data.readBits(1) > 0 ? AV1_ADAPTIVE : data.readBits(1);

      forceIntegerMv = screenContentTools > 0 ?
          (data.readBits(1) > 0 ? AV1_ADAPTIVE : data.readBits(1)) : 2;
      if (orderHint > 0) {
        orderHintNBits = data.readBits(3) + 1;
      }
    }

    int superRes = data.readBits(1);
    int cdef = data.readBits(1);
    int restoration = data.readBits(1);
    int hbd = data.readBits(1);
    if (profileIdc == 2 && hbd > 0) hbd += data.readBits(1);
    int monochrome = profileIdc != 1 ? data.readBits(1) : 0;
    int colorDescriptionPresent = data.readBits(1);;
    int pri;
    int trc;
    int mtrx;
    if (colorDescriptionPresent > 0) {
      pri = data.readBits(8);
      trc = data.readBits(8);
      mtrx = data.readBits(8);
    } else {
      pri = AV1_COLOR_PRI_UNKNOWN;
      trc = AV1_TRC_UNKNOWN;
      mtrx = AV1_MC_UNKNOWN;
    }
    int separateUvDeltaQ;
    if (monochrome > 0) {
      int colorRange = data.readBits(1);
      int layout = AV1_PIXEL_LAYOUT_I400;
      int ssHor = 1;
      int ssVer = 1;
      int chr = AV1_CHR_UNKNOWN;
      separateUvDeltaQ = 0;
    } else if (pri == AV1_COLOR_PRI_BT709 &&
        trc == AV1_TRC_SRGB &&
        mtrx ==AV1_MC_IDENTITY)
    {
      int layout = AV1_PIXEL_LAYOUT_I444;
      int ssHor = 0;
      int ssVer = 0;
      int colorRange = 1;
      if (profileIdc != 1 && !(profileIdc == 2 && hbd == 2)) {
        return null;
      }
    } else {
      int colorRange = data.readBits(1);
      int layout;
      int ssHor = 0;
      int ssVer = 0;
      switch (profileIdc) {
        case 0:
          layout = AV1_PIXEL_LAYOUT_I420;
          ssHor = 1;
          ssVer = 1;
          break;
        case 1:
          layout = AV1_PIXEL_LAYOUT_I444;
          ssHor = 0;
          ssVer = 0;
          break;
        case 2:
          if (hbd == 2) {
            ssHor = data.readBits(1);
            ssVer = ssHor > 0 ? data.readBits(1) : 0;
          } else {
            ssHor = 1;
            ssVer = 0;
          }
          layout = ssHor > 0 ?
              ssVer > 0 ? AV1_PIXEL_LAYOUT_I420 :
                  AV1_PIXEL_LAYOUT_I422 :
              AV1_PIXEL_LAYOUT_I444;
          break;
      }
      int chr = ssHor == 1 && ssVer == 1 ?
          data.readBits(2) : AV1_CHR_UNKNOWN;
    }
    separateUvDeltaQ = monochrome <= 0 ? data.readBits(1) : 0;

    int filmGrainPresent = data.readBits(1);


    data.readBits(1); // dummy bit

    float pixelWidthHeightRatio = 1.0f;
    int levelIdc = operatingPoints[0].majorLevel *10 + operatingPoints[0].minorLevel;

    return new SequenceHeader(
        profileIdc,
        reducedStillPictureHeader,
        decoderModelInfoPresent,
        equalPictureInterval,
        framePresentationDelayLength,
        frameIdNumbersPresent,
        frameIdNBits,
        deltaFrameIdNBits,
        screenContentTools,
        forceIntegerMv,
        orderHint,
        orderHintNBits,
        numOperatingPoints,
        bufferRemovalDelayLength,
        0,
        levelIdc,
        0,
        maxWidth,
        maxHeight,
        pixelWidthHeightRatio,
        false,
        false,
        operatingPoints,
        operatingParameterInfos);
  }

  /**
   * Finds the first OBU unit in {@code data}.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @return The ObuHeader, or null if a obu unit was not found.
   */
  public static ObuHeader obuUnitStart(ParsableBitArray data, int startOffset, int endOffset) {
    int length = endOffset - startOffset;
    Assertions.checkState(length >= 0);
    if (length == 0) {
      return null;
    }

    boolean valid = false;
    data.skipBit();   // reserved
    int obuType = data.readBits(4);
    int hasEntension = data.readBits(1);
    int hasLengthField = data.readBits(1);
    data.skipBit();   // reserved

    int temporalId = 0, spatialId = 0;
    if(hasEntension == 1) {
      temporalId = data.readBits(3);
      spatialId = data.readBits(2);
      data.readBits(3);
    }

    if(obuType < 0 || obuType > 15) {
      valid = false;
      return null;
    }

    long obuLength = -1;
    if(hasLengthField != 0) {
      obuLength = data.readUleb128();
      if(obuLength == -1) {
        valid = false;
        return null;
      }
    }
    if(obuLength == -1) {
      valid = true;
      return new ObuHeader(valid, obuType, obuLength, temporalId, spatialId);
    }

    int initBitPos = data.getPosition();
    int initBytePos = initBitPos >>> 3;
    long pktBytelen = initBytePos + obuLength;
    valid = true;

    return new ObuHeader(valid, obuType, pktBytelen, temporalId, spatialId);
  }

  private AV1UnitUtil() {
    // Prevent instantiation.
  }

}
