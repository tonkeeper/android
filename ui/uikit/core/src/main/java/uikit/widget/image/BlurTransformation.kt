package uikit.widget.image

import blur.Toolkit
import coil3.Bitmap
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import androidx.core.graphics.scale

class BlurTransformation(
    val radius: Float,
    private val maxSize: Int? = null,
) : Transformation() {

    override val cacheKey: String = "BlurTransformation(radius=$radius, maxSize=$maxSize)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val source = downscale(input)
        val output = source.copy(source.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
        Toolkit.blur(source, output, radius.toInt())
        return output
    }

    private fun downscale(input: Bitmap): Bitmap {
        val limit = maxSize ?: return input
        val longest = maxOf(input.width, input.height)
        if (longest <= limit) {
            return input
        }
        val ratio = limit.toFloat() / longest
        return input.scale(
            width = (input.width * ratio).toInt().coerceAtLeast(1),
            height = (input.height * ratio).toInt().coerceAtLeast(1),
        )
    }

}

// Downscaled first: at full size a 20px radius leaves the text on NFT images readable.
fun hiddenBalanceBlurTransformation(): BlurTransformation = BlurTransformation(radius = 20f, maxSize = 160)

fun ImageRequest.Builder.hiddenBalanceBlur(): ImageRequest.Builder =
    transformations(hiddenBalanceBlurTransformation())
