package blur.node.api31

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.tonapps.ui.blur.R

@RequiresApi(Build.VERSION_CODES.S)
internal class BlurNode(
    private val context: Context
): BaseNode("blur") {

    private val noiseShader: BitmapShader by lazy {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.noise)
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    init {
        applyRenderEffect()
    }

    override fun onBoundsChange(bounds: RectF) {
        super.onBoundsChange(bounds)
        node.setPosition(0, 0, bounds.width().toInt(), bounds.height().toInt())
        node.translationX = bounds.left
        node.translationY = bounds.top
    }

    private fun applyRenderEffect() {
        val radius = 26f
        val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
        val shaderEffect = RenderEffect.createShaderEffect(noiseShader)
        val fixedBlurEffect = RenderEffect.createBlendModeEffect(shaderEffect, blurEffect, BlendMode.MODULATE)
        node.setRenderEffect(fixedBlurEffect)
    }

    override fun draw(input: Canvas, drawOutput: (output: Canvas) -> Unit) {
        val blurCanvas = node.beginRecording()
        blurCanvas.translate(-bounds.left, -bounds.top)
        drawOutput(blurCanvas)
        node.endRecording()

        input.drawRenderNode(node)
    }

}