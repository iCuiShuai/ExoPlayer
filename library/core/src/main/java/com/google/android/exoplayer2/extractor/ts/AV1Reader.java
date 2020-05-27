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

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR;
import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_RANDOM_ACCESS_INDICATOR;

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.AV1UnitUtil;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a continuous av1 byte stream and extracts individual frames.
 */
public final class AV1Reader implements ElementaryStreamReader {
  private static final int OBU_HEADER_SCRATCH_SIZE = 10; // max obu type and obu size type

  private final ParsableBitArray obuHeaderScratch;

  private final boolean allowNonIdrKeyframes;
  private final boolean detectAccessUnits;
  private final AV1UnitTargetBuffer sequenceHeader;
  private long totalBytesWritten;
  private long nextOBUOffset;

  private String formatId;
  private TrackOutput output;
  private SampleReader sampleReader;

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // Per PES packet state that gets reset at the start of each PES packet.
  private long pesTimeUs;

  // State inherited from the TS packet header.
  private boolean randomAccessIndicator;

  private boolean payloadUnitStartIndicator;

  //current ts left data is not a complete obu unit or obu unit has no length field, then we will wait next payloadUnitStartIndicator is true before parse obu unit
  public boolean waitUntilNextPayloadUnitStartIndicator;

  /**
   * @param seiReader An SEI reader for consuming closed caption channels.
   * @param allowNonIdrKeyframes Whether to treat samples consisting of non-IDR I slices as
   *     synchronization samples (key-frames).
   * @param detectAccessUnits Whether to split the input stream into access units (samples) based on
   *     frame headers. Pass {@code false} if the stream contains access unit delimiters (AUDs).
   */
  public AV1Reader(SeiReader seiReader, boolean allowNonIdrKeyframes, boolean detectAccessUnits) {
    this.allowNonIdrKeyframes = allowNonIdrKeyframes;
    this.detectAccessUnits = detectAccessUnits;
    sequenceHeader = new AV1UnitTargetBuffer(AV1UnitUtil.OBUType.OBU_SEQ_HDR, 128);

    nextOBUOffset = 0;
    obuHeaderScratch = new ParsableBitArray(new byte[OBU_HEADER_SCRATCH_SIZE]);
    waitUntilNextPayloadUnitStartIndicator = false;
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read, or {@code null} to skip.
   * @param targetLength The target length of the read.
   * @return Whether the target length has been reached.
   */
  private boolean readData(byte[] source, int offset, int limit, byte[] target, int targetLength) {
    int readLength = limit - offset;
    int bytesToRead = Math.min(readLength, targetLength);
    if (bytesToRead <= 0) {
      return true;
    } else if (target != null) {
      System.arraycopy(source, offset, target, 0, bytesToRead);
    }

    return bytesToRead == targetLength;
  }

  @Override
  public void seek() {
    sequenceHeader.reset();
    sampleReader.reset();
    totalBytesWritten = 0;
    randomAccessIndicator = false;
    waitUntilNextPayloadUnitStartIndicator = false;
    nextOBUOffset = 0;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_VIDEO);
    sampleReader = new SampleReader(output, allowNonIdrKeyframes, detectAccessUnits);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    this.pesTimeUs = pesTimeUs;
    randomAccessIndicator |= (flags & FLAG_RANDOM_ACCESS_INDICATOR) != 0;
    payloadUnitStartIndicator = (flags & FLAG_PAYLOAD_UNIT_START_INDICATOR) != 0;

    if(payloadUnitStartIndicator) {
      waitUntilNextPayloadUnitStartIndicator = false;
      nextOBUOffset = 0;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    int offset = data.getPosition();
    int limit = data.limit();
    int length = limit - offset;
    byte[] dataArray = data.data;

    // Append the data to the buffer.
    totalBytesWritten += data.bytesLeft();
    output.sampleData(data, data.bytesLeft());

    int obuUnitOffset = offset;
    // Scan the appended data, processing OBU units as they are encountered
    while (true) {
      if(waitUntilNextPayloadUnitStartIndicator) {
        // We didn't have enough data to parse another OBU unit.
        obuUnitData(dataArray, obuUnitOffset, limit, false);
        nextOBUOffset = 0;
        return;
      }

      obuUnitOffset = offset + (int)nextOBUOffset;
      if(obuUnitOffset > limit) {
        // We didn't have enough data to parse another OBU unit.
        long leftLength = limit - offset;
        obuUnitData(dataArray, offset, limit, false);
        nextOBUOffset -= leftLength;
        return;
      }
      if (obuUnitOffset == limit) {
        // We've scanned to the end of the data without finding the start of another OBU unit.
        obuUnitData(dataArray, offset, limit, true);
        nextOBUOffset = 0;
        return;
      }

      obuHeaderScratch.reset(obuHeaderScratch.data);
      int readLength = (limit - obuUnitOffset) >= OBU_HEADER_SCRATCH_SIZE ? OBU_HEADER_SCRATCH_SIZE : (limit - obuUnitOffset);
      if(readLength < 3) {
        // We didn't have enough data to parse another OBU unit.
        obuUnitData(dataArray, offset, limit, false);
        nextOBUOffset = 0;
        waitUntilNextPayloadUnitStartIndicator = true;
        return;
      }

      readData(dataArray, obuUnitOffset, limit, obuHeaderScratch.data, readLength);
      AV1UnitUtil.ObuHeader obuHeader = AV1UnitUtil.obuUnitStart(obuHeaderScratch, 0, readLength);
      if(obuHeader == null || !obuHeader.valid) {
        obuUnitData(dataArray, offset, limit, false);
        nextOBUOffset = 0;
        waitUntilNextPayloadUnitStartIndicator = true;
        return;
      }

      if(obuHeader.obuLength == -1) {
        //obu unit has no length field, we wait next PayloadUnitStartIndicator
        obuUnitData(dataArray, offset, limit, false);
        nextOBUOffset = 0;
        waitUntilNextPayloadUnitStartIndicator = true;
        return;
      }

      // We've seen the start of a OBU unit of the following type.
      int obuUnitType = obuHeader.obuType;

      // This is the number of bytes from the current offset to the start of the next OBU unit.
      // It may be negative if the OBU unit started in the previously consumed data.
      int lengthToObuUnit = obuUnitOffset - offset;
      if (lengthToObuUnit > 0) {
        obuUnitData(dataArray, offset, obuUnitOffset, true);
      }
      int bytesWrittenPastPosition = limit - obuUnitOffset;
      long absolutePosition = totalBytesWritten - bytesWrittenPastPosition;
      // Indicate the end of the previous OBU unit. If the length to the start of the next unit
      // is negative then we wrote too many bytes to the OBU buffers. Discard the excess bytes
      // when notifying that the unit has ended.
      endObuUnit(absolutePosition, bytesWrittenPastPosition,
          lengthToObuUnit < 0 ? -lengthToObuUnit : 0, pesTimeUs);
      // Indicate the start of the next OBU unit.
      startObuUnit(absolutePosition, bytesWrittenPastPosition, obuUnitType, pesTimeUs);

      offset += nextOBUOffset;

      nextOBUOffset = obuHeader.obuLength == -1 ? limit - offset : obuHeader.obuLength;
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private void startObuUnit(long position, int offset, int obuUnitType, long pesTimeUs) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sequenceHeader.startObuUnit(obuUnitType);
    }
    sampleReader.startObuUnit(position, offset, obuUnitType, pesTimeUs);
  }

  private void obuUnitData(byte[] dataArray, int offset, int limit, boolean completeObuData) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sequenceHeader.appendToObuUnit(dataArray, offset, limit);
    }
    sampleReader.appendToObuUnit(dataArray, offset, limit, completeObuData);
  }

  private void endObuUnit(long position, int offset, int discardPadding, long pesTimeUs) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sequenceHeader.endObuUnit(discardPadding);
      if (!hasOutputFormat) {
        if (sequenceHeader.isCompleted()) {
          List<byte[]> initializationData = new ArrayList<>();
          initializationData.add(Arrays.copyOf(sequenceHeader.obuData, sequenceHeader.obuLength));
          AV1UnitUtil.SequenceHeader sequenceHeader = AV1UnitUtil.parseSequenceHeader(
              this.sequenceHeader.obuData, 0, this.sequenceHeader.obuLength);

          output.format(
              Format.createVideoSampleFormat(
                  formatId,
                  MimeTypes.VIDEO_AV1,
                  CodecSpecificDataUtil.buildAvcCodecString(
                      sequenceHeader.profileIdc,
                      Format.NO_VALUE,
                      sequenceHeader.levelIdc),
                  /* bitrate= */ Format.NO_VALUE,
                  /* maxInputSize= */ Format.NO_VALUE,
                  sequenceHeader.width,
                  sequenceHeader.height,
                  /* frameRate= */ Format.NO_VALUE,
                  initializationData,
                  /* rotationDegrees= */ Format.NO_VALUE,
                  sequenceHeader.pixelWidthAspectRatio,
                  /* drmInitData= */ null));
          hasOutputFormat = true;
          sampleReader.putSequenceHeader(sequenceHeader);
          this.sequenceHeader.reset();
        }
      } else if (sequenceHeader.isCompleted()) {
        AV1UnitUtil.SequenceHeader sequenceHeader = AV1UnitUtil.parseSequenceHeader(
            this.sequenceHeader.obuData, 0, this.sequenceHeader.obuLength);
        sampleReader.putSequenceHeader(sequenceHeader);
        this.sequenceHeader.reset();
      }
    }

    boolean sampleIsKeyFrame =
        sampleReader.endObuUnit(position, offset, hasOutputFormat, randomAccessIndicator, payloadUnitStartIndicator);
    if (sampleIsKeyFrame) {
      // This is either an IDR frame or the first I-frame since the random access indicator, so mark
      // it as a keyframe. Clear the flag so that subsequent non-IDR I-frames are not marked as
      // keyframes until we see another random access indicator.
      randomAccessIndicator = false;
    }
  }

  /** Consumes a stream of OBU units and outputs samples. */
  private static final class SampleReader {

    private static final int DEFAULT_BUFFER_SIZE = 128;

    private final TrackOutput output;
    private final boolean allowNonIdrKeyframes;
    private final boolean detectAccessUnits;
    private final SparseArray<AV1UnitUtil.SequenceHeader> sequenceHeader;
    private final ParsableBitArray bitArray;

    private byte[] buffer;
    private int bufferLength;

    // Per OBU unit state. A sample consists of one or more OBU units.
    private int obuUnitType;
    private long obuUnitStartPosition;
    private boolean isFilling;
    private long obuUnitTimeUs;
    private FrameHeaderData previousFrameHeader;
    private FrameHeaderData frameHeader;

    // Per sample state that gets reset at the start of each sample.
    private boolean readingSample;
    private long samplePosition;
    private long sampleTimeUs;
    private boolean sampleIsKeyframe;

    public SampleReader(TrackOutput output, boolean allowNonIdrKeyframes,
        boolean detectAccessUnits) {
      this.output = output;
      this.allowNonIdrKeyframes = allowNonIdrKeyframes;
      this.detectAccessUnits = detectAccessUnits;
      sequenceHeader = new SparseArray<>();
      previousFrameHeader = new FrameHeaderData();
      frameHeader = new FrameHeaderData();
      buffer = new byte[DEFAULT_BUFFER_SIZE];
      bitArray = new ParsableBitArray(buffer, DEFAULT_BUFFER_SIZE);
      reset();
    }

    public boolean needsSpsPps() {
      return detectAccessUnits;
    }

    public void putSequenceHeader(AV1UnitUtil.SequenceHeader sequenceHeaderData) {
      sequenceHeader.append(0, sequenceHeaderData);
    }

    public void reset() {
      isFilling = false;
      readingSample = false;
      frameHeader.clear();
    }

    public void startObuUnit(long position, int offset, int type, long pesTimeUs) {
      //if is an obu sequence unit , we out put it with later key frame
      if(type == AV1UnitUtil.OBUType.OBU_SEQ_HDR) {
        if (detectAccessUnits) {
          //output the previous one.
          if (readingSample) {
            obuUnitStartPosition = position;
            outputSample(offset);
          }
        }
        obuUnitStartPosition = position;
        readingSample = false;
        obuUnitType = 0;
        frameHeader.clear();
        return;
      }

      obuUnitType = type;
      obuUnitTimeUs = pesTimeUs;

      if(readingSample) {
        obuUnitStartPosition = position;
      }

      if ((allowNonIdrKeyframes && frameHeader.isIntraNonKeyFrame())
          || (detectAccessUnits && (obuUnitType == AV1UnitUtil.OBUType.OBU_FRAME
              || obuUnitType == AV1UnitUtil.OBUType.OBU_FRAME_HDR
              || obuUnitType == AV1UnitUtil.OBUType.OBU_REDUNDANT_FRAME_HDR)
              || obuUnitType == AV1UnitUtil.OBUType.OBU_TILE_GRP)) {
        // Store the previous header and prepare to populate the new one.
        FrameHeaderData newFrameHeader = previousFrameHeader;
        previousFrameHeader = frameHeader;
        frameHeader = newFrameHeader;
        frameHeader.clear();
        bufferLength = 0;
        isFilling = true;
      }
    }

    public void parseObu() {
      AV1UnitUtil.ObuHeader obuHeader = AV1UnitUtil.obuUnitStart(bitArray, 0, bitArray.bitsLeft());

      switch (obuHeader.obuType) {
        case AV1UnitUtil.OBUType.OBU_FRAME:
        case AV1UnitUtil.OBUType.OBU_FRAME_HDR:
          parseFrameHeader(obuHeader);
          break;
        case AV1UnitUtil.OBUType.OBU_REDUNDANT_FRAME_HDR:
          parseRedudantFrameHeader(obuHeader);
          break;
        case AV1UnitUtil.OBUType.OBU_TILE_GRP:
          paseTileGroup(obuHeader);
          break;
        default:
          break;
      }
    }

    public void paseTileGroup(AV1UnitUtil.ObuHeader obuHeader) {
      AV1UnitUtil.SequenceHeader seqData = sequenceHeader.get(0);
      frameHeader.setAll(seqData, previousFrameHeader.frameType, obuHeader.obuType);
    }

    public void parseRedudantFrameHeader(AV1UnitUtil.ObuHeader obuHeader) {
      parseFrameHeader(obuHeader);
    }

    public int read_frame_size() {
      AV1UnitUtil.SequenceHeader seqData = sequenceHeader.get(0);

      return 0;
    }

    public void parseFrameHeader(AV1UnitUtil.ObuHeader obuHeader) {
      AV1UnitUtil.SequenceHeader seqData = sequenceHeader.get(0);

      int showExistingFrame = seqData.reducedStillPictureHeader > 0 ? 0 : bitArray.readBits(1);
      int frameId = -1;
      if (showExistingFrame > 0) {
        int existingFrameIdx = bitArray.readBits(3);
        if (seqData.decoderModelInfoPresent > 0 && seqData.equalPictureInterval == 0) {
          int framePresentationDelay = bitArray.readBits(seqData.framePresentationDelayLength);
        }
        if (seqData.frameIdNumbersPresent > 0)
          frameId = bitArray.readBits(seqData.frameIdNBits);

        frameHeader.setAll(seqData, AV1UnitUtil.FrameType.AV1_FRAME_TYPE_NO_FRAME, obuHeader.obuType);
        return ;
      }

      int frameType = seqData.reducedStillPictureHeader > 0 ? AV1UnitUtil.FrameType.AV1_FRAME_TYPE_KEY : bitArray.readBits(2);
      int showFrame = seqData.reducedStillPictureHeader > 0 ? 1 : bitArray.readBits(1);
      if (showFrame > 0) {
        if (seqData.decoderModelInfoPresent > 0 && seqData.equalPictureInterval == 0) {
          int framePresentationDelay = bitArray.readBits(seqData.framePresentationDelayLength);
        }
      } else {
        int showableFrame = bitArray.readBits(1);
      }

      int errorResilientMode =
          ((frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_KEY && showFrame > 0) ||
              frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_SWITCH ||
              seqData.reducedStillPictureHeader > 0) ? 1 : bitArray.readBits(1);

      int disableCdfUpdate = bitArray.readBits(1);
      int allowScreenContentTools = seqData.screenContentTools == AV1UnitUtil.AV1_ADAPTIVE ?
          bitArray.readBits(1) : seqData.screenContentTools;
      int forceIntegerMv = 0;
      if (allowScreenContentTools > 0)
        forceIntegerMv = seqData.forceIntegerMv == AV1UnitUtil.AV1_ADAPTIVE ?
            bitArray.readBits(1) : seqData.forceIntegerMv;
      else
        forceIntegerMv = 0;

      if ((frameType & 1) == 0)
        forceIntegerMv = 1;

      if (seqData.frameIdNumbersPresent > 0)
        frameId = bitArray.readBits(seqData.frameIdNBits);

      int frameSizeOverride = seqData.reducedStillPictureHeader > 0 ? 0 :
          frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_SWITCH ? 1 : bitArray.readBits(1);

      int frameOffset = seqData.orderHint > 0 ?
          bitArray.readBits(seqData.orderHintNBits) : 0;

      int primaryRefRrame = errorResilientMode == 0 && (frameType & 1) != 0 ? bitArray.readBits(3) : AV1UnitUtil.AV1_PRIMARY_REF_NONE;

      if (seqData.decoderModelInfoPresent > 0) {
        int bufferRemovalTimePresent = bitArray.readBits(1);
        if (bufferRemovalTimePresent != 0) {
          for (int i = 0; i < seqData.numOperatingPoints; i++) {
                AV1UnitUtil.SequenceHeaderOperatingPoint seqop = seqData.operatingPoints[i];
            if (seqop.decoderModelParamPresent != 0) {
              int inTemporalLayer = (seqop.idc >> obuHeader.temporalId) & 1;
              int inSpatiaLayer  = (seqop.idc >> (obuHeader.spatialId + 8)) & 1;
              if (seqop.idc == 0 || (inTemporalLayer != 0 && inSpatiaLayer != 0)) {
                int bufferRemovalTime = bitArray.readBits(seqData.bufferRemovalDelayLength);
              }
            }
          }
        }
      }

      frameHeader.setAll(seqData, frameType, obuHeader.obuType);
      return;
    }

    /**
     * Called to pass stream data.
     *
     * @param data Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void appendToObuUnit(byte[] data, int offset, int limit, boolean completeObuData) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (buffer.length < bufferLength + readLength) {
        buffer = Arrays.copyOf(buffer, (bufferLength + readLength) * 2);
      }
      System.arraycopy(data, offset, buffer, bufferLength, readLength);
      bufferLength += readLength;

      if(!completeObuData) {
        return;
      }

      bitArray.reset(buffer, bufferLength);

      parseObu();

      isFilling = false;
    }

    public boolean endObuUnit(
        long position, int offset, boolean hasOutputFormat, boolean randomAccessIndicator, boolean payloadUnitStartIndicator) {
      if (obuUnitType == AV1UnitUtil.OBUType.OBU_TD
          || (detectAccessUnits
          && (frameHeader.isFirstTileOfPicture(previousFrameHeader)))) {
        // If the OBU unit ending is the start of a new sample, output the previous one.
        if (hasOutputFormat && readingSample) {
          int obuUnitLength = (int) (position - obuUnitStartPosition);
          outputSample(offset + obuUnitLength);
        }
        samplePosition = obuUnitStartPosition;
        sampleTimeUs = obuUnitTimeUs;
        sampleIsKeyframe = false;
        readingSample = true;
      }
      boolean treatIFrameAsKeyframe =
          allowNonIdrKeyframes ? frameHeader.isIFrame() : randomAccessIndicator;
      sampleIsKeyframe |=
          frameHeader.isKeyFrame()
              || (treatIFrameAsKeyframe && frameHeader.isIntraNonKeyFrame());
      return sampleIsKeyframe;
    }

    private void outputSample(int offset) {
      @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
      int size = (int) (obuUnitStartPosition - samplePosition);
      output.sampleMetadata(sampleTimeUs, flags, size, offset, null);
    }

    private static final class FrameHeaderData {
      private AV1UnitUtil.SequenceHeader seqData;
      private boolean isComplete;
      private boolean hasFrameType;
      private int frameType;
      private int obuType;

      public void clear() {
        hasFrameType = false;
        isComplete = false;
      }

      public void setAll(
          AV1UnitUtil.SequenceHeader seqData,
          int frameType,
          int obuType) {
        this.seqData = seqData;
        this.frameType = frameType;
        this.obuType = obuType;
        isComplete = true;
        hasFrameType = true;
      }

      public boolean isKeyFrame() {
        if(hasFrameType) {
          if(frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_KEY) {
            obuType = obuType;
          }
        }
        return hasFrameType && (frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_KEY);
      }

      public boolean isIntraNonKeyFrame() {
        return hasFrameType && frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_INTRA;
      }

      public boolean isIFrame() {
        return hasFrameType && (frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_INTRA || frameType == AV1UnitUtil.FrameType.AV1_FRAME_TYPE_KEY);
      }

      private boolean isFirstTileOfPicture(FrameHeaderData other) {
        //todo...we still have many things to do to find fist frame
        return isComplete
            && (!other.isComplete
            || obuType == AV1UnitUtil.OBUType.OBU_FRAME
            || frameType != other.frameType
            );
      }
    }
  }
}
