package com.tonapps.tonkeeper.extensions

import android.graphics.Bitmap
import android.net.Uri
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.core.ImagePipeline
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
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

suspend fun ImagePipeline.loadSquare(uri: Uri, size: Int): Bitmap? {
    val imageRequest = ImageRequestBuilder.newBuilderWithSource(uri)
        .setResizeOptions(ResizeOptions.forSquareSize(size))
        .build()

    return getBitmap(imageRequest)
}