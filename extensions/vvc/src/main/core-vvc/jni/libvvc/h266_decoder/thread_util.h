/*****************************************************************************
 * Copyright(C) 2018 - 2020 Tencent.
 *
 * All Rights Reserved.
 ****************************************************************************/

/**
 * @file thread_util.h
 *
 * Thread utilites
 */

#ifndef UTIL_INCLUDE_THREAD_UTIL_H_
#define UTIL_INCLUDE_THREAD_UTIL_H_

#include <atomic>
#include <condition_variable>  // NOLINT
#include <limits>
#include <mutex>  // NOLINT
#include <queue>
#include <thread>  // NOLINT
#include <utility>

#include "util.h"

namespace util {

/**
 * @class Event
 * @brief Event that can be waited for and triggered.
 * The event object is useful in sending a signal to a thread indicating that a
 * particular event has occurred. The initial state of the event object is not signaled,
 * Use the @ref Trigger function to set the state of an event object to signaled.
 */
class Event {
 public:
  Event() = default;
  ~Event() = default;

  Event(const Event&) = delete;            /**< Non-copyable */
  Event& operator=(const Event&) = delete; /**< Non-copyable */

  /**
   * Reset event
   */
  void Reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    counter_ = 0;
  }

  /**
   * Wait for event
   */
  void Wait() {
    std::unique_lock<std::mutex> lock(mutex_);
    cond_.wait(lock, [this] { return counter_ != 0; });
    counter_--;
  }

  /**
   * Wait for event without resettig counter
   */
  void WaitNoReset() {
    std::unique_lock<std::mutex> lock(mutex_);
    cond_.wait(lock, [this] { return counter_ != 0; });
  }

  /**
   * Trigger event
   */
  void Trigger() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (counter_ < (std::numeric_limits<uint32_t>::max)()) {
      counter_++;
    }

    cond_.notify_all();
  }

  /**
   * Wait for event with a timeout
   * @param[in] wait_ms Time limit(ms) when waiting for an event
   * Return true if time out, false otherwise
   */
  bool TimedWait(uint32_t wait_ms) {
    bool timeout = false;

    auto const end_time = std::chrono::steady_clock::now() + std::chrono::milliseconds(wait_ms);

    std::unique_lock<std::mutex> lock(mutex_);
    if (counter_ == 0) {
      timeout = cond_.wait_until(lock, end_time) == std::cv_status::timeout;
    }

    if (counter_ > 0) {
      counter_--;
      timeout = false;
    }

    return timeout;
  }

 private:
  // @cond private
  mutable std::mutex mutex_;
  std::condition_variable cond_;
  uint32_t counter_ = 0;
  // @endcond
};

/**
 * @class ThreadBase
 * @brief Thread class
 * @note C++11 thread doesn't have the support for all thread utilities.
 * Following interfaces internally will call native APIs, so please check their
 * required input and return values. Also unless necessary, we do NOT recommend use these APIs.
 * 1. @ref GetPriority
 *    GetThreadPriority(Win), pthread_getschedparam(others)
 * 2. @ref SetPriority
 *    SetThreadPriority(Win), pthread_setschedparam(others)
 */
class ThreadBase {
 public:
  ThreadBase() = default;

  virtual ~ThreadBase() = default;

  ThreadBase(const ThreadBase&) = delete;            /**< Non-copyable */
  ThreadBase& operator=(const ThreadBase&) = delete; /**< Non-copyable */

  /** Thread main func that derived class needs to implement */
  virtual void ThreadMain() = 0;

  /** Get thread priority
   * @return Thread priority level
   */
  int GetPriority();

  /** Set thread priority
   * @param[in] priority Priority value of the thread
   * @return True if succeed, false otherwise
   */
  bool SetPriority(int priority);

  /** Start thread
   * @return True if succeed, false otherwise
   */
  bool Start();

  /** Stop thread */
  void Stop();

  /** Awaken the thread */
  void Wait() { return wake_event_.Wait(); }

  /** Awaken the thread */
  void Awaken() { return wake_event_.Trigger(); }

  /** Retreive number of hardware thread contexts that can run concurrently */
  static int GetHardwareConcurrency() { return std::thread::hardware_concurrency(); }

 private:
  // @cond private
  std::thread thread_;
  Event wake_event_;
  // @endcond
};

/**
 * @class ThreadSafeInteger
 * @brief ThreadSafeInteger class for safe operation between threads.
 * This class is intended for use in signaling state changes safely between threads.
 * It provide some safe integer operations between threads to avoid read-write collisions
 */

class ThreadSafeInteger {
 public:
  ThreadSafeInteger() = default;
  ~ThreadSafeInteger() = default;

  ThreadSafeInteger(const ThreadSafeInteger&) = delete;            /**< Non-copyable */
  ThreadSafeInteger& operator=(const ThreadSafeInteger&) = delete; /**< Non-copyable */

  /** Wait until the value changed
   * @param[in] old_val The old value
   * @return the new value
   */
  int WaitForChange(int old_val) {
    std::unique_lock<std::mutex> lock(mutex_);
    cond_.wait(lock, [this, old_val] { return val_ != old_val; });
    return val_;
  }

  /** Wait until value bigger than the threshold
   * @param[in] threshold threshold
   * @return the new value
   */
  int WaitUntil(int threshold) {
    std::unique_lock<std::mutex> lock(mutex_);
    cond_.wait(lock, [this, threshold] { return val_ >= threshold; });
    return val_;
  }

  /** Get the value
   * @return the value
   */
  int Get() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return val_;
  }

  /** Set the value
   * @param[in] new_val new value
   */
  void Set(int new_val) {
    std::lock_guard<std::mutex> lock(mutex_);
    val_ = new_val;
    cond_.notify_all();
  }

  /** Awaken all waiting threads, but make no value change */
  void Poke() {
    std::lock_guard<std::mutex> lock(mutex_);
    cond_.notify_all();
  }

  /** Increment the value */
  void Increment(int n = 1) {
    ASSERT(n >= 1);
    std::lock_guard<std::mutex> lock(mutex_);
    val_ += n;
    cond_.notify_all();
  }

 private:
  // @cond private
  mutable std::mutex mutex_;
  std::condition_variable cond_;
  int val_ = -1;
  // @endcond
};

/**
 * @class ThreadSafeQueue
 * @brief thread-safe queue
 */
template <typename T>
class ThreadSafeQueue {
 public:
  ThreadSafeQueue() { shutdown_ = false; }

  /**
   * Push a new value to the queue
   * @param[in] new_value New value that will be pushed to the queue
   */
  void Push(T new_value) {
    std::lock_guard<std::mutex> lk(mutex_);
    data_queue_.push(std::move(new_value));
    data_cond_.notify_one();
  }

  /**
   * Wait and pop a value from the queue
   * @param[out] value Value that will be popped from the queue
   */
  bool WaitAndPop(T* value) {
    std::unique_lock<std::mutex> lk(mutex_);
    data_cond_.wait(lk, [this] { return shutdown_ || !data_queue_.empty(); });
    if (shutdown_) {
      return false;
    }

    *value = std::move(data_queue_.front());
    data_queue_.pop();
    return true;
  }

  /**
   * Try pop a value from the queue
   * @param[out] value Value that will be popped from the queue if the call succeeds
   */
  bool TryPop(T* value) {
    std::lock_guard<std::mutex> lk(mutex_);
    if (data_queue_.empty()) {
      return false;
    }
    *value = std::move(data_queue_.front());
    data_queue_.pop();
    return true;
  }

  /**
   * Check if the queue is empty
   */
  bool Empty() const {
    std::lock_guard<std::mutex> lk(mutex_);
    return data_queue_.empty();
  }

  /**
   * Returns the number of elements in the queue
   */
  size_t Size() const {
    std::lock_guard<std::mutex> lk(mutex_);
    return data_queue_.size();
  }

  /**
   * Shut down the queue
   */
  void Shutdown() {
    shutdown_ = true;
    data_cond_.notify_all();
  }

 private:
  // @cond private
  mutable std::mutex mutex_;
  std::queue<T> data_queue_;
  std::condition_variable data_cond_;
  std::atomic<bool> shutdown_;
  // @endcond
};

}  // namespace util

#endif  // UTIL_INCLUDE_THREAD_UTIL_H_
