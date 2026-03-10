package uikit.widget.image

import blur.Toolkit
import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

class BlurTransformation(
    val radius: Float
) : Transformation() {

    override val cacheKey: String = "BlurTransformation(radius=$radius)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val output = input.copy(input.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
        Toolkit.blur(input, output, radius.toInt())
        return output
    }

}