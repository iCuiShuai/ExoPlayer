/*****************************************************************************
 * Copyright(C) 2018 - 2020 Tencent.
 *
 * All Rights Reserved.
 ****************************************************************************/

/**
 * @file util.h
 * @brief Common utilities
 */

#ifndef UTIL_INCLUDE_UTIL_H_
#define UTIL_INCLUDE_UTIL_H_

#include <iostream>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cassert>
#include <cstdlib>
#include <limits>
#include <type_traits>
#include <utility>

#ifdef _MSC_VER
#include <intrin.h>
#endif

// @cond private
#ifndef __has_builtin
#define __has_builtin(x) __has_builtin##x  // Compatibility with non-clang compilers.
#endif

#if __GNUC__ > 3 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 4)
#define __has_builtin__builtin_clz 1
#define __has_builtin__builtin_clzll 1
#endif

#if __GNUC__ > 4 || (__GNUC__ == 4 && __GNUC_MINOR__ >= 8)
#define __has_builtin__builtin_bswap32 1
#endif
// @endcond

namespace util {

/**
 * @def UNUSED
 * @brief Define UNUSED for unused parameter so the compiler 
 * would not issue warning
 */
#ifndef UNUSED
#define UNUSED(x) ((void)sizeof(x))
#endif

/**
 * @def ASSERT
 * @brief Implementation of assert that can be used in C++11 consexpr
 */
#ifdef NDEBUG
#define ASSERT(condition) UNUSED(condition)
#else
namespace detail {
// @cond private

// Unlike C++14, C++11 doesn't allow assert in constexpr so we use a trick from
// http://ericniebler.com/2014/09/27/assert-and-constexpr-in-cxx11/

struct AssertFailure {
  template<typename Fun>
  explicit AssertFailure(Fun fun) {
    fun();
    exit(EXIT_FAILURE);
  }
};

// @endcond
}  // namespace detail
#define ASSERT(cond) (cond) ? 0 : throw util::detail::AssertFailure([]{ assert(!#cond); })
#endif

/**
 * @def FORCE_INLINE
 * @brief Define of FORCE_INLINE for forcing the compiler
 * inline specified function
 */

#ifdef _MSC_VER
/**
 * @def FORCE_INLINE_ENABLED
 * @brief Define of FORCE_INLINE_ENABLED for diable/enable force_inline
 * disable it can make release build faster
 * enable it can make some intrinsics functions run faster
 */
#define FORCE_INLINE_ENABLED 0
#if FORCE_INLINE_ENABLED
#define FORCE_INLINE static __forceinline
#else
#define FORCE_INLINE static inline
#endif
#else
#define FORCE_INLINE static __inline__ __attribute__((always_inline))
#endif

/**
 * @brief The number of elements in a flat array
 */
template <typename T, int N>
constexpr int CountOf(T const (&)[N]) noexcept {
  return N;
}

/**
 * @brief The number of elements in an std::array
 */
template <typename TArray>
constexpr int CountOf(const TArray&) noexcept {
  return static_cast<int>(std::tuple_size<TArray>::value);
}

/**
 * @brief Determine if the value is a power of 2
 */
template <typename T>
constexpr bool IsPower2(T val) {
  return
    ASSERT(std::is_integral<T>::value),
    ASSERT(val > 0),
    0 == (val & (val - 1));
}

/**
 * Calculate ceil(num/denom)
 * @param[in] num Numerator
 * @param[in] denom Denominator
 * @brief Return ceil(num/denom)
 */
template<typename T>
constexpr T CeilDiv(T num, T denom) {
  return ASSERT(denom > 0), (num + denom - 1) / denom;
}

/**
 * Calculate smallest mutiple of quantum that is no less than the value
 * @param[in] val Value to be aligned
 * @param[in] quantum The alignment
 * @brief Return smallest mutiple of quantum that is no less than the value
 */
template<typename T>
constexpr T AlignUp(T val, T quantum) {
  return CeilDiv(val, quantum) * quantum;  // @todo Optimize?
}

/**
 * Calculate round(x/(2^shift))
 * @param[in] x value to be shifted
 * @param[in] shift number of bits to shift. Can be 0
 * @brief Return round(x/(2^shift))
 */
template <typename T>
static inline T RoundRShift(T x, int32_t shift) {
  ASSERT(shift >= 0);

  // @todo Optimize with intrinsics

  return (0 == shift) ? x : ((x + ((1 << shift) >> 1)) >> shift);
}

/**
 * Calculate signed round(x/(2^shift))
 * @param[in] x value to be shifted
 * @param[in] shift number of bits to shift. Can be 0
 * @brief Return signed round(x/(2^shift))
 */
template <typename T>
static inline T RoundDivPower2(T x, int32_t shift) {
  ASSERT(shift >= 0);

  const int sign = (x >= 0) ? 1 : -1;
  return sign * (RoundRShift(std::abs(x), shift));
}

/**
 * Cast a number to a type saturating to a valid range
 * Either the range or the destination type is optional,
 * can be deduced from the other.
 * @param[in] x value to be satureate/cast
 * @param[in] limits a pair of min/max values.

 * @brief Cast a number to a type saturating to a valid range
 */
template<typename TDst, typename TSrc>
constexpr TDst ClipCast(
  TSrc x,
  const std::pair<TDst, TDst>& limits = {
    std::numeric_limits<TDst>::lowest(), std::numeric_limits<TDst>::max()
  }) {
  return (x < limits.first)
         ? limits.first
         : (x > limits.second)
           ? limits.second
           : static_cast<TDst>(x);
}

/**
 * @enum kClipType
 * @brief a flag to indicate which end is to be clipped
 */
enum class kClipType : int8_t {
  kClipMin = 0,
  kClipMax,
  kClipMinMax
};

/**
 * @class Normalizer
 * @brief Calculate round(x/(2^shift)), then cast the number to a type saturating to a valid range
 * If non-type template parameter clip_type is kClipMin, then will call the class template partial
 * specialization which casts the number to a type saturating to a minimum (have not implemented)
 * If non-type template parameter clip_type is kClipMax, then will call the class template partial
 * specialization which casts the number to a type saturating to a maximum value
 * If non-type template parameter clip_type is kClipMinMax, then will call the class template
 * partial specialization which casts the number to a type saturating to a range
 */
template<typename TDst, kClipType clip_type>
class Normalizer;


/**
 * Define default clipping range
 */
template<typename T>
static inline std::pair<T, T> NumericLimits() {
  return { std::numeric_limits<T>::min(), std::numeric_limits<T>::max() };
}

/**
 * @brief Calculate round(x/(2^shift)), then cast the number to a type saturating to a valid range
 */
template<typename TDst>
class Normalizer<TDst, kClipType::kClipMinMax> {
 public:
  /**
   * Constructor
   * @param[in] shift number of bits to shift. Can be 0
   * @param[in] limits a pair of min/max values.
   */
  Normalizer(int32_t shift, const std::pair<TDst, TDst>& limits = NumericLimits<TDst>())
    : shift_(shift), limits_(limits) {}

  /**
   * Function Objects (Functors)
   * @param[in] x value to be saturate/cast
   * @brief Calculate round(x/(2^shift)), then cast a number to a type saturating to a valid range
   */
  template<typename TSrc>
  TDst operator()(TSrc x) {
    x = RoundRShift(x, shift_);
    return ClipCast(x, limits_);
  }

 private:
  // @cond private
  int32_t shift_ = 0;
  const std::pair<TDst, TDst> limits_ = NumericLimits<TDst>();
  // @endcond
};

/**
  * @brief Calculate round(x/(2^shift)), then cast a number to a type saturating to a maximum value.
  * The number used in this class should be positive
  */
template<typename TDst>
class Normalizer<TDst, kClipType::kClipMax> {
 public:
  /**
   * Constructor
   * @param[in] shift number of bits to shift. Can be 0
   * @param[in] limits a pair of min/max values.
   */
  Normalizer(int32_t shift, const std::pair<TDst, TDst>& limits = NumericLimits<TDst>())
    : shift_(shift), limits_(limits) {}

  /**
   * Function Objects (Functors)
   * @param[in] x value to be saturate/cast
   * @brief Calculate round(x/(2^shift)), then cast a number to a type saturating to a maximum value
   */
  template<typename TSrc>
  TDst operator()(TSrc x) {
    ASSERT(x >= limits_.first);

    x = RoundRShift(x, shift_);
    return (x < limits_.second)
      ? static_cast<TDst>(x)
      : limits_.second;
  }

 private:
  // @cond private
  int32_t shift_ = 0;
  const std::pair<TDst, TDst> limits_ = NumericLimits<TDst>();
  // @endcond
};

namespace detail {
// @cond private
template<bool conversion_needed>
struct Verifier;

template<> struct Verifier<false> {
  template <typename T>
  static constexpr T VerifyCast(T src) {
    return src;
  }
};

template<> struct Verifier<true> {
  template<typename TDst, typename TSrc>
  static constexpr TDst VerifyCast(TSrc x) {
    return ASSERT(x == static_cast<TSrc>(static_cast<TDst>(x))), static_cast<TDst>(x);
  }
};
// @endcond
}  // namespace detail

/**
 * Cast a number to a type with assert verification.
 * If converstion changes value with respect to == operator
 * the function asserts.
 * @todo Add runtime check option?
 * @param[in] x value to be cast
 * @brief Cast a number to a type verifying the value is not changed by conversion
 */

template<typename TDst, typename TSrc>
constexpr TDst VerifyCast(TSrc x) {
  return detail::Verifier<!std::is_same<TDst, typename std::decay<TSrc>::type>::value>::
    template VerifyCast<TDst>(x);
}

/**
 * @def SSIZEOF
 * @brief Signed version of sizeof
 * We discourage unsigned types
 */
#define SSIZEOF(expr) util::VerifyCast<std::ptrdiff_t>(sizeof(expr))

/**
  * Count of id's for continuous enums.
  * This function is used to define the size of an array the enum is used to index.
  * Any enum that's allowed to be used as an array index (i.e. casted to int)
  * should define a continuous range of values starting with 0 (implicitly or explicitly).
  * and terminated by kCount. For example:
  * enum class Color {
  *   kRed, kGreen, kBlue,
  *   kCount
  * };
  * Note: You need not initalize first value with 0 as it is guaranteed by standard.
  * @brief Count of values for continuous enums
  */
template<typename TEnum>
constexpr int EnumCount() { return static_cast<int>(TEnum::kCount); }


/**
 * @brief Cast a continuous enum to int
 */
template<typename TEnum>
constexpr typename std::enable_if<(0 < static_cast<int>(TEnum::kCount)), int>::type ToInt(TEnum e) {
  return static_cast<int>(e);
}

/**
 * @brief Cast a continuous enum to uint
 */
template <typename TEnum>
constexpr typename std::enable_if<(0 < static_cast<int>(TEnum::kCount)),
  int>::type ToUint(TEnum e) {
  return static_cast<unsigned int>(e);
}

/**
 * @struct EnumRange
 * @brief Helper range expression for continuous enums.
 * This function is used to type-safely iterate through the [0..kCount) range
 * using range-based for loop. For example:
 * for (Color color : EnumRange<Color>()) {
 *   ASSERT(color != Color::kCount);
 * }
 */

template <typename TEnum>
class EnumRange {
  // @cond Internal interface required for C++ range-based iteration
public:
  class Iter {
   public:
    explicit Iter(TEnum begin) :
      value_(static_cast<int>(begin)) {}

    Iter& operator++() { ++value_; return *this; }
    TEnum operator*() const { return static_cast<TEnum>(value_); }
    bool operator!=(const Iter& b) const { return value_ != b.value_; }

   private:
    int value_;
  };

  Iter begin() const { return Iter(static_cast<TEnum>(0)); }
  Iter end()   const { return Iter(TEnum::kCount); }
  // @endcond
};

/** @overload */
inline static int FloorLog2(int32_t val) {
  ASSERT(val > 0);

#if defined(UNOPTIMIZED)
  int n = 0;
  while (val >= (1 << n)) {
    n++;
  }

  return n - 1;
#elif __has_builtin(__builtin_clz) || defined(__has_builtin__builtin_clz)
  return __builtin_clz(val) ^ 31;  // Same as 31 - CLZ if val > 0 but saves an instruction on x86
#elif defined(_M_X64) || defined(_M_IX86)
  __assume(val > 0);

  unsigned long result;  // NOLINT(runtime/int)
  (void)_BitScanReverse(&result, val);

  return VerifyCast<int>(result);
#else
#error Add your plaftorm to UNOPTIMIZED case or provide optimized implementation
#endif
}

/**
 * Calculate floor(log2())
 * Usually slightly more efficient than CeilLog2.
 * @param[in] val Input value
 * @brief Return floor(log2()) of the input value.
 */

inline static int FloorLog2(int64_t val) {
  ASSERT(val > 0);

#if defined(UNOPTIMIZED) || defined(_M_IX86)
  int32_t hi = static_cast<int32_t>(val >> 32);
  return hi ? 32 + FloorLog2(hi) : FloorLog2(static_cast<int32_t>(val));
#elif __has_builtin(__builtin_clzll) || defined(__has_builtin__builtin_clzll)
  return __builtin_clzll(val) ^ 63;  // Same as 63 - CLZ if val > 0 but saves an instruction on x86
#elif defined(_M_X64)
  __assume(val > 0);

  unsigned long result;  // NOLINT(runtime/int)
  (void)_BitScanReverse64(&result, val);

  return VerifyCast<int>(result);
#else
#error Add your plaftorm to UNOPTIMIZED case or provide optimized implementation
#endif
}

/**
 * Calculate ceil(log2())
 * @param[in] val Input value
 * @brief Return Ceil(log2()) of the input value
 */
template<typename T>
inline static int CeilLog2(T val) {
  return FloorLog2(val) + !IsPower2(val);
}

/**
 * Set a bit mask with n bits set to 1 starting from LSB
 * @brief Return a bit mask with n LSB bits set to 1
 */
template<typename T = uint32_t>
constexpr T LsbOneMask(int n) {
  return
    ASSERT(std::is_unsigned<T>::value),
    ASSERT(n >= 0 && n <= 8 * SSIZEOF(T)),
    static_cast<T>((0xFFFFFFFFFFFFFFFFULL) >> (64 - n));
}

/**
 * Integer's arithmetic right shift
 * @brief Indicate the intention of an arithmetic right shift
 */
template<typename T = int>
constexpr T ArithmeticRShift(T num, int shift) {
  return
    ASSERT(std::is_integral<T>::value),
    ASSERT(shift >= 0),
    num >> shift;
}

/** @overload */
static inline uint16_t FromBigEndian(uint16_t data) {
  return (data << 8) | (data >> 8);
}

/** @overload */
static inline uint32_t FromBigEndian(uint32_t data) {
#if defined(UNOPTIMIZED) \
  || (defined(_MSC_VER) && defined(__clang__))  // NOLINT @todo Remove once Clang on MSVC stops calling _byteswap_X as a function
        return ((data & UINT32_C(0xff000000)) >> 24) |
               ((data & UINT32_C(0x00ff0000)) >>  8) |
               ((data & UINT32_C(0x0000ff00)) <<  8) |
               ((data & UINT32_C(0x000000ff)) << 24);
#elif __has_builtin(__builtin_bswap32) || defined(__has_builtin__builtin_bswap32)
  return __builtin_bswap32(data);
#elif defined(_M_X64) || defined(_M_IX86)
  return _byteswap_ulong(data);
#else
#error Add your plaftorm to UNOPTIMIZED case or provide optimized implementation
#endif
}

/**
 * Convert a pack of big-endian bytes to a valid integer
 * @param[in] data Input bytes
 * @brief Returns a value integer of the same size as the input dada
 */

static inline uint64_t FromBigEndian(uint64_t data) {
  const auto hi = uint64_t{ FromBigEndian(static_cast<uint32_t>(data >> 32)) };
  const auto lo = uint64_t{ FromBigEndian(static_cast<uint32_t>(data)) };

  return lo << 32 | hi;
}

/**
 * @brief Convert an unsigned integer to the same-sized signed one.
 */

template<typename T>
constexpr typename std::make_signed<T>::type AsSigned(T t) {
  return static_cast<typename std::make_signed<T>::type>(t);
}

/**
 * @brief Convert a signed integer to the same-sized unsigned one.
 */

template<typename T>
constexpr typename std::make_unsigned<T>::type AsUnsigned(T t) {
  return static_cast<typename std::make_unsigned<T>::type>(t);
}

/**
 * @brief Clip operation to type T
 */
template <typename T>
inline T Clip(const T minVal, const T maxVal, const T a) {
  return std::min<T>(std::max<T>(minVal, a), maxVal);
}

/**
 * @brief Calculate psnr from sse
 */
inline double SseToPsnr(int64_t samples, int bit_depth, int64_t sse) {
  constexpr double kMaxPsnr = 100.0;
  if (sse > 0) {
    const int peak = 255 << (bit_depth - 8);  // align with VTM PSNR calculation
    const double psnr =
      10.0 * std::log10(static_cast<double>(samples * peak * peak / static_cast<double> (sse)));
    return psnr > kMaxPsnr ? kMaxPsnr : psnr;
  } else {
    return kMaxPsnr;
  }
}

}  // namespace util

#endif  // UTIL_INCLUDE_UTIL_H_

