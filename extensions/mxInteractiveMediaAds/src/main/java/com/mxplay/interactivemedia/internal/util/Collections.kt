package com.mxplay.interactivemedia.internal.util

private fun rangeCheck(size: Int, fromIndex: Int, toIndex: Int) {
    when {
        fromIndex > toIndex -> throw IllegalArgumentException("fromIndex ($fromIndex) is greater than toIndex ($toIndex).")
        fromIndex < 0 -> throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")
        toIndex > size -> throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
    }
}

/**
 * Returns the index of the smallest element in {@code array} that is greater than (or optionally
 * equal to) a specified {@code value}.
 *
 * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
 * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
 * index of the last one will be returned.
 *
 * @param inclusive If the value is present in the array, whether to return the corresponding
 *     index. If false then the returned index corresponds to the smallest element strictly
 *     greater than the value.
 * @param stayInBounds If true, then {@code (a.length - 1)} will be returned in the case that the
 *     value is greater than the largest element in the array. If false then {@code a.length} will
 *     be returned.
 *
 * @param comparison function that returns zero when called on the list element being searched.
 * On the elements coming before the target element, the function must return negative values;
 * on the elements coming after the target element, the function must return positive values.
 *
 *
 * @return The index of the smallest element in {@code array} that is greater than (or optionally
 *     equal to) {@code value}.
 */

fun <T> List<T>.binarySearchCeil(fromIndex: Int = 0, toIndex: Int = size, inclusive : Boolean = true, stayInBounds : Boolean = true, comparison: (T) -> Int): Int {
    rangeCheck(size, fromIndex, toIndex)
    var index: Int = this.binarySearch(fromIndex, toIndex, comparison)
    if (index < 0) {
        index = index.inv()
    } else {
        while (++index < this.size && comparison(this.get(index)) == 0) {
        }
        if (inclusive) {
            index--
        }
    }
    return if (stayInBounds) Math.min(this.size - 1, index) else index
}


/**
 * Returns the index of the largest element in {@code list} that is less than (or optionally equal
 * to) a specified {@code value}.
 *
 * <p>The search is performed using a binary search algorithm, so the list must be sorted. If the
 * list contains multiple elements equal to {@code value} and {@code inclusive} is true, the index
 * of the first one will be returned.
 *
 *
 * @param inclusive If the value is present in the list, whether to return the corresponding
 *     index. If false then the returned index corresponds to the largest element strictly less
 *     than the value.
 * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
 *     the smallest element in the list. If false then -1 will be returned.
 *
 *@param comparison function that returns zero when called on the list element being searched.
 * On the elements coming before the target element, the function must return negative values;
 * on the elements coming after the target element, the function must return positive values.
 *
 *
 * @return The index of the largest element in {@code list} that is less than (or optionally equal
 *     to) {@code value}.
 */

fun <T> List<T>.binarySearchFloor(fromIndex: Int = 0, toIndex: Int = size, inclusive : Boolean = true, stayInBounds : Boolean = true, comparison: (T) -> Int): Int {
    rangeCheck(size, fromIndex, toIndex)
    var index: Int = this.binarySearch(fromIndex, toIndex, comparison)
    if (index < 0) {
        index = -(index + 2)
    } else {
        while (--index >= 0 && comparison(this.get(index)) == 0) {
        }
        if (inclusive) {
            index++
        }
    }
    return if (stayInBounds) Math.max(0, index) else index
}


