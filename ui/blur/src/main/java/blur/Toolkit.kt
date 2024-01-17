/*
 * Copyright (C) 2021 The Android Open Source Project
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

package blur

import android.graphics.Bitmap

object Toolkit {

    fun blur(inputBitmap: Bitmap, outputBitmap: Bitmap, radius: Int = 5) {
        nativeBlurBitmap(nativeHandle, inputBitmap, outputBitmap, radius, null)
    }

    private var nativeHandle: Long = 0

    init {
        System.loadLibrary("renderscript-toolkit")
        nativeHandle = createNative()
    }

    fun shutdown() {
        destroyNative(nativeHandle)
        nativeHandle = 0
    }

    private external fun createNative(): Long

    private external fun destroyNative(nativeHandle: Long)

    private external fun nativeBlurBitmap(
        nativeHandle: Long,
        inputBitmap: Bitmap,
        outputBitmap: Bitmap,
        radius: Int,
        restriction: Range2d?
    )

}


/**
 * Define a range of data to process.
 *
 * This class is used to restrict a [Toolkit] operation to a rectangular subset of the input
 * tensor.
 *
 * @property startX The index of the first value to be included on the X axis.
 * @property endX The index after the last value to be included on the X axis.
 * @property startY The index of the first value to be included on the Y axis.
 * @property endY The index after the last value to be included on the Y axis.
 */
data class Range2d(
    val startX: Int,
    val endX: Int,
    val startY: Int,
    val endY: Int
) {
    constructor() : this(0, 0, 0, 0)
}
