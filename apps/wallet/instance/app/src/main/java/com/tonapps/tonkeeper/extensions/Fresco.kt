package com.tonapps.tonkeeper.extensions

import android.graphics.Bitmap
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipeline
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun ImagePipeline.getBitmap(request: ImageRequest, callback: (Bitmap?) -> Unit) {
    val dataSource = fetchDecodedImage(request, null)
    dataSource.subscribe(object : BaseBitmapDataSubscriber() {
        override fun onNewResultImpl(bitmap: Bitmap?) {
            callback(bitmap)
        }

        override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
            callback(null)
            dataSource.close()
        }
    }, CallerThreadExecutor.getInstance())
}

suspend fun ImagePipeline.getBitmap(request: ImageRequest) = suspendCoroutine { continuation ->
    getBitmap(request) {
        continuation.resume(it)
    }
}