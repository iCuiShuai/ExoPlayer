/*****************************************************************************
 * Copyright(C) 2018 - 2020 Tencent.
 *
 * All Rights Reserved.
 ****************************************************************************/

/**
 * @file o266dec_api.h
 * @brief Tencent O266 decoder api definition
 */

#ifndef DECODER_INCLUDE_O266DEC_API_H_
#define DECODER_INCLUDE_O266DEC_API_H_

#include <stdint.h>
#include <stdbool.h>

/** Major version of tencent o266 decoder api */
#define O266DEC_MAJOR_VERSION "0"
/** Minor version of tencent o266 decoder api */
#define O266DEC_MINOR_VERSION "1"
/** Patch version of tencent o266 decoder api */
#define O266DEC_PATCH_VERSION "0"

#ifdef __cplusplus
extern "C" {
#endif
/** O266 decoder handle */
typedef struct O266DecImpl* O266DecDecoderHandle;

/**
 * @enum O266DecStatus
 * @brief O266 decoder status
 */
typedef enum {
  kO266DecOk = 0,               /**< Operation succeed */
  kO266DecNeedMoreData,         /**< Decoder needs more data to proceed */
  kO266DecOutputNotReady,       /**< Decoder output is not ready */
  kO266DecEndOfStream,          /**< Finish decoding bitstream */
  kO266DecErrorNullPointer,     /**< Null pointer */
  kO266DecErrorInvalidParam,    /**< Invalid input parameter */
  kO266DecErrorInvalidWorkflow, /**< Call not allowed at this moment */
  kO266DecErrorOutOfMemory,     /**< Memory failure */
  kO266DecErrorNotSupported,    /**< Feature not supported yet */
  kO266DecErrorBitstreamSyntax, /**< Bitstram syntax error */
  kO266DecErrorUnexpected       /**< Unexpected error */
} O266DecStatus;

/**
 * @struct O266DecConfWindow
 * @brief O266 decoder picture conformance window
 */
typedef struct {
  int32_t conf_win_left_offset;   /**< Conformance window left offset */
  int32_t conf_win_right_offset;  /**< Conformance window right offset */
  int32_t conf_win_top_offset;    /**< Conformance window top offset */
  int32_t conf_win_bottom_offset; /**< Conformance window bottom offset */
} O266DecConfWindow;

/**
 * @enum O266DecChromaFormat
 * @brief O266 decoder chroma format
 */
typedef enum {
  kO266DecChromaFormat400 = 0,
  kO266DecChromaFormat420 = 1,
  kO266DecChromaFormat422 = 2,
  kO266DecChromaFormat444 = 3,
} O266DecChromaFormat;

/**
 * @enum O266DecPicType
 * @brief O266 decoder picture type
 */
typedef enum {
  kO266DecPicTypeB = 0, /**< B picture */
  kO266DecPicTypeP = 1, /**< P Picture */
  kO266DecPicTypeI = 2  /**< I Picture */
} O266DecPicType;

/**
 * @struct O266DecConfig
 * @brief O266 decoder configuration
 */
typedef struct {
  bool enable_multi_thread;       /**< true: enable multi thread; false: single thread */
  int32_t num_threads;            /**< Number of threads, 0 for auto  */
  int32_t num_frame_threads;      /**< Number of frame threads, 0 for auto */
  int32_t process_frame_delay;    /**< Number of frames delayed from parsing to processing */
  bool check_decode_picture_hash; /**< true:check MD5; false:no check */
  bool use_16bit_pixel;           /**< use 16-bit pixel YUV buffer */
} O266DecConfig;

/** Number of picture planes supported by decoder */
#define O266DEC_MAX_NUM_PLANES 3

/**
 * @struct O266DecPlaneBuffers
 * @brief Collection of buffers returned by O266DecAllocatorInterface::AllocBuffers
 * Unlike naked array, it can be assigned and returned.
 */
typedef struct {
  void* buf[O266DEC_MAX_NUM_PLANES]; /**< Pointer to buffers to be filled by AllocBuffers */
  void* private_data;  /**< Pointer to user specified data, pass through */
} O266DecPlaneBuffers;

/**
 * @struct O266DecPicturePlane
 * @brief Pointer to read-only 2D pixel plane
 * By providing stride it allows seamless random access to pixels given their x/y coordinates.
 */
typedef struct {
  const void* pix;   /**< Pointer to the first pixel */
  int32_t stride;    /**< Line stride in bytes */
  int32_t bit_depth; /**< Bit depth for each plane */
} O266DecPicturePlane;

/**
 * @struct O266DecPictureHeader
 * @brief O266 decoder picture header
 */
typedef struct {
  int32_t width;         /**< Picture width in number of pixels */
  int32_t height;        /**< Picture height in number of pixels */
  int32_t chroma_format; /**< A value from O266DecChromaFormat*/

  int64_t coded_picture_number;   /**< Picture number in decode order */
  int64_t display_picture_number; /**< Picture number in display order */
  int64_t pts;                    /**< Presentation timestamp */
  int32_t pic_type;               /**< A value from O266DecPicType */

  bool is_field;     /**< Picture frame/field flag */
  bool is_top_field; /**< Picture top/bottom flag */
} O266DecPictureHeader;

/**
 * @struct O266DecOutputPicture
 * @brief O266 decoder output picture
 */
typedef struct {
  O266DecConfWindow conf_win;  /**< Picture display conformance window */
  O266DecPictureHeader header; /**< Picture header */
  O266DecPlaneBuffers buffers; /**< Buffers as returned by the AllocBuffers. */
  O266DecPicturePlane planes[O266DEC_MAX_NUM_PLANES]; /**< 2D planes of the picture */
} O266DecOutputPicture;

/**
 * @struct O266DecPictureBufInfo
 * @brief O266 decoder output picture allocation parameters
 */
typedef struct {
  int32_t alignment;                     /**< Buffer alignment requirement for all planes */
  int32_t size[O266DEC_MAX_NUM_PLANES]; /**< Minimal buffer size required for each plane */
} O266DecPictureBufInfo;

/**
 * @struct O266DecAllocatorInterface
 * @brief User may want the decoder to decode directly into user-supplied
 * picture buffers to avoid copying of pictures.
 * This structure provides the callback function for decoder to call user
 * provided allocate/release functions
 */
typedef struct {
  /** Allocate the picture buffers based on the buffer information.
   * @param[in] info Pointer to the output picture buffer information
   * @param[inout] result Pointer to picture buffer needs to be allocated
   * @return True if buffer allocation succeed, false otherwise.
   */
  bool (*AllocBuffers)(const O266DecPictureBufInfo* info, O266DecPlaneBuffers* result);

  /** Release the allocated picture buffers
   * @param[in] to_release Pointer to the picture buffers that need to be released
   */
  void (*ReleaseBuffers)(O266DecPlaneBuffers* to_release);
} O266DecAllocatorInterface;

/**
 * @struct O266DecDataPacket
 * @brief O266 decoder bitstream data packet
 */
typedef struct {
  const uint8_t* data_buf; /**< Pointer to bitstream data buffer */
  int32_t data_size;       /**< Size of bitstream data in bytes */
  /** Flag to indicate if bitstream data consists of complete nal units */
  bool has_complete_nal;
  int64_t pts; /**< time stamp of data packet */
} O266DecDataPacket;

/**
 * Create decoder instance. User should call this api first to obtain a decoder handle
 * @param[in] cfg Pointer to decoder configuration
 * @param[out] handle Pointer to the created decoder handle
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecCreateDecoder(const O266DecConfig* cfg, O266DecDecoderHandle* handle);

/**
 * Query decoder library verison.
 * @param[in] decoder Decoder handle
 * @param[inout] version_buf Buffer that decoder can use to fill version string
 * @param[in] version_buf_size Size of verison buffer in bytes
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecGetVersion(O266DecDecoderHandle decoder, char* version_buf,
                                int32_t version_buf_size);

/**
 * Close decoder instance created by @ref O266DecCreateDecoder
 * @param[in] decoder Decoder handle
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecCloseDecoder(O266DecDecoderHandle decoder);

/**
 * Set custom allocator for the decoder.
 * It is only allowed to be called when no pictures are allocated.
 * Otherwise, it will fail with kO266DecErrorInvalidWorkflow error.
 * @param[in] decoder Decoder handle
 * @param[in] itf Pointer to @ref O266DecAllocatorInterface. If it is set to NULL, then decoder
 * will allocate the picture buffer.
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecSetAllocator(O266DecDecoderHandle decoder,
                                  const O266DecAllocatorInterface* itf);

/**
 * Get decoder configuration
 * @param[in] decoder Decoder handle
 * @param[out] cfg Pointer to decoder current configuration
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecGetConfig(O266DecDecoderHandle decoder, O266DecConfig* cfg);

/**
 * Get the output picture from decoder
 * @param[in] decoder Decoder handle
 * @param[out] pic Pointer to output picture
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecGetOutputPicture(O266DecDecoderHandle decoder, O266DecOutputPicture* pic);

/**
 * Release the output picture back to decoder
 * @param[in] decoder Decoder handle
 * @param[in] pic Pointer to output picture
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecReleaseOutputPicture(O266DecDecoderHandle decoder,
                                           O266DecOutputPicture* pic);

/**
 * Push bitstream data packet to decoder. The data will be copied into the
 * decoder instance. The data provided to this function should be an o266 only elementary bitstream.
 * @param[in] decoder Decoder handle
 * @param[in] packet Pointer to bitstream data packet
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecPushData(O266DecDecoderHandle decoder, const O266DecDataPacket* packet);

/**
 * Signal decoder would flush the internal data buffers.
 * Caller can invoke O266DecDecodeFrame() to finish decoding the rest of frames if available.
 * @param[in] decoder Decoder handle
 * @param[in] end_of_stream indicates whether this flush operation is triggered by end of stream
 * @return @ref O266DecStatus
 */
O266DecStatus O266DecNotifyFlush(O266DecDecoderHandle decoder, bool end_of_stream);

/**
 * Decode frame.
 * @param[in] decoder Decoder handle
 * @return @ref O266DecStatus.
 * @note Decoder may return @ref kO266DecNeedMoreData to indicate it needs
 * more input data to proceed.
 */
O266DecStatus O266DecDecodeFrame(O266DecDecoderHandle decoder);

#ifdef __cplusplus
}
#endif

#endif  // DECODER_INCLUDE_O266DEC_API_H_
