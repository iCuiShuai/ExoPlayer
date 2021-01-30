/*****************************************************************************
 * Copyright(C) 2018 - 2020 Tencent.
 *
 * All Rights Reserved.
 ****************************************************************************/

/**
 * @file mem_util.h
 * @brief Memory utilities
 */

#ifndef UTIL_INCLUDE_MEM_UTIL_H_
#define UTIL_INCLUDE_MEM_UTIL_H_

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "util.h"

namespace util {

/**
 * 32-byte alignment for AVX2
 */
constexpr int kAvx2Alignment = 32;

/**
 * 32-byte alignment for Intrinsics
 */
constexpr int kNeonAlignment = 32;

/**
 * Default alignment based on current architecture
 */
#if ENABLE_NEON
constexpr int kDefaultAlignment = kNeonAlignment;
#else
constexpr int kDefaultAlignment = kAvx2Alignment;
#endif

/** 
 * Allocates memory on a specified alignment boundary similar to C++17 aligned_malloc.
 * @param[in] alignment The alignment value, which must be an integer
 * power of 2
 * @param[in] size Size of the requested memory allocation
 * @return A pointer to the memory block that was allocated or nullptr if
 * the operation failed. The pointer is a multiple of alignment.
 */
void* AlignedMalloc(size_t alignment, size_t size);

/** 
 * Frees a block of memory that was allocated with AlignedMalloc
 * @param[in] mem A pointer to the memory block that was returned by AlignedMalloc.
 */
void AlignedFree(void* mem);

// @cond private
namespace detail {
struct AlignedDeleter {
  void operator()(void* p) { AlignedFree(p); }
};
}  // namespace detail
// @endcond

/** 
 * @brief Unique pointer with runtime-specified alignment
 */
template<typename T>
class AlignedUniquePtr : public std::unique_ptr<T, detail::AlignedDeleter> {
 public:
  AlignedUniquePtr() = default;  /**< Default c'tor*/

  AlignedUniquePtr(AlignedUniquePtr&& moved) = default;  /**< Move c'tor */
  AlignedUniquePtr& operator= (AlignedUniquePtr&& moved) = default;  /**< Move assignment */

  /**
   * Allocate elements in c'tor
   */
  template <typename... Params>
  AlignedUniquePtr(int alignment, std::ptrdiff_t count, Params&&... params) {
    std::unique_ptr<void, detail::AlignedDeleter> raw{AlignedMalloc(alignment, count * sizeof(T))};

    T* p = static_cast<T*>(raw.get());
    ASSERT(reinterpret_cast<std::uintptr_t>(p) % alignment == 0);

    if (!p) {
      throw std::bad_alloc();
    }

    // skip the construction for trivally constructible type and param pack is empty
    constexpr bool skip_construct =
      sizeof...(params) == 0 && std::is_trivially_constructible<T>::value;
    if (!skip_construct) {
      int constructed = 0;
      try {
        for (; constructed < count; ++constructed) {
          new (p + constructed) T(std::forward(params)...);
        }
      } catch (...) {
        for (; constructed; --constructed) {
          p[constructed - 1].~T();
        }

        throw;
      }
    }

    this->reset(p);
    raw.release();
  }

  /**
   * Allocate elements
   */
  template<typename ...Params>
  void Alloc(int allignment, std::ptrdiff_t count, Params&&... params) {
    *this = AlignedUniquePtr(allignment, count, std::forward(params)...);
  }
};

/**
 * @brief std's Allocator-compliant object to be used with std::vector
 */
template<typename T, int TAlignment =
  (alignof(T) > kDefaultAlignment ? alignof(T) : kDefaultAlignment)>
struct AlignedAllocator {
// @cond private
  static_assert(IsPower2(TAlignment), "Alignment must be a power of 2");

// Minimum requirements for Allocator type in C++11 and on.
  using value_type = T;

  T* allocate(std::size_t count) {
    if (count > std::size_t(-1) / sizeof(T)) {
      throw std::bad_alloc();
    }

    auto p = static_cast<T*>(AlignedMalloc(TAlignment, count * sizeof(T)));
    if (!p) {
      throw std::bad_alloc();
    }

    return p;
  }

  void deallocate(T* p, std::size_t) noexcept {
    AlignedFree(p);
  }

// Because this template has non-type argument we cant' rely on std::allocator_traits to
//   define AlignedAllocator<internal_object<T>, N>. See detailed explanation in
//   https://stackoverflow.com/questions/48061522/create-the-simplest-allocator-with-two-template-arguments NOLINT

  template<class U> struct rebind {
    using other = AlignedAllocator<U, TAlignment>;
  };

// Note: Some STL implementations may not require the copy and comparison API below
  AlignedAllocator() = default;

  template<typename U>
  AlignedAllocator(const AlignedAllocator<U, TAlignment>&) noexcept {}

  template<typename U>
  AlignedAllocator& operator=(const AlignedAllocator<U, TAlignment>&) noexcept {}

  template<typename U>
  bool operator==(const AlignedAllocator<U, TAlignment>&) noexcept { return true; }

  template<typename U>
  AlignedAllocator& operator!=(const AlignedAllocator<U, TAlignment>&) noexcept { return false; }

// @endcond
};

/**
 * @brief Convenience shortcut for std::vector<...AlignedAllocator...>
 */
template<typename T, int TAlignment =
  (alignof(T) > kDefaultAlignment ? alignof(T) : kDefaultAlignment)>
using AlignedVector = std::vector<T, util::AlignedAllocator<T, TAlignment>>;

}  // namespace util

#endif  // UTIL_INCLUDE_MEM_UTIL_H_

