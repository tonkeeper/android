package uikit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.request.target
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.pxOrElse
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import coil3.transform.Transformation
import coil3.util.CoilUtils
import uikit.R
import uikit.extensions.useAttributes
import uikit.widget.image.BlurTransformation
import uikit.widget.image.BorderTransformation

data class ResizeOptions(val width: Int, val height: Int) {

    companion object {
        fun forSquareSize(size: Int) = ResizeOptions(size, size)
    }
}

@Deprecated("Use Compose")
class AsyncImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppCompatImageView(context, attrs, defStyle) {

    companion object {

        @Volatile
        private var instance: ImageLoader? = null

        fun get(context: Context): ImageLoader =
            instance ?: synchronized(this) {
                instance ?: ImageLoader.Builder(context.applicationContext)
                    .crossfade(true)
                    .build()
                    .also { instance = it }
            }

        suspend fun loadSquareBitmap(context: Context, url: Uri, size: Int = 0): Bitmap? {
            val loader = ImageLoader(context)

            val request = ImageRequest.Builder(context)
                .data(url)
                .size(coil3.size.Size(size, size))
                .scale(Scale.FILL)
                .build()

            val result = loader.execute(request)
            return (result as? SuccessResult)?.image?.toBitmap()
        }
    }

    data class Border(
        val color: Int = 0,
        val width: Int = 0
    ) {

        val isEmpty: Boolean
            get() = color == 0 || width == 0
    }

    data class RoundedCornerRadius(
        val topLeft: Float = 0f,
        val topRight: Float = 0f,
        val bottomLeft: Float = 0f,
        val bottomRight: Float = 0f
    ) {

        val isEmpty: Boolean
            get() = topLeft == 0f && topRight == 0f && bottomLeft == 0f && bottomRight == 0f

        constructor(radius: Float) : this(
            topLeft = radius,
            topRight = radius,
            bottomLeft = radius,
            bottomRight = radius
        )
    }

    private val prefixResourceUri: String by lazy {
        "android.resource://${context.packageName}/"
    }

    private val coilScale: Scale
        get() = when (scaleType) {
            ScaleType.CENTER_CROP -> Scale.FILL
            ScaleType.CENTER_INSIDE, ScaleType.FIT_CENTER, ScaleType.FIT_START, ScaleType.FIT_END -> Scale.FIT
            else -> Scale.FIT
        }

    var blur: Boolean = false

    private var roundAsCircle: Boolean = false
    private var roundedCornerRadius = RoundedCornerRadius()
    private var resizeOptions: ResizeOptions? = null
    private var border = Border()
    private var viewAspectRatio = 0f
    private var placeholderDrawable: Drawable? = null

    init {
        context.useAttributes(attrs, R.styleable.AsyncImageView) {
            roundAsCircle = it.getBoolean(R.styleable.AsyncImageView_roundAsCircle, false)
            border = Border(
                color = it.getColor(R.styleable.AsyncImageView_roundingBorderColor, 0),
                width = it.getDimensionPixelSize(R.styleable.AsyncImageView_roundingBorderWidth, 0)
            )
            viewAspectRatio = it.getFloat(R.styleable.AsyncImageView_viewAspectRatio, 0f)
            val placeholderResId = it.getResourceId(R.styleable.AsyncImageView_placeholderImage, 0)
            if (placeholderResId != 0) {
                ContextCompat.getDrawable(context, placeholderResId)?.let { drawable ->
                    placeholderDrawable = drawable
                }
            }
            roundedCornerRadius = RoundedCornerRadius(it.getDimensionPixelSize(R.styleable.AsyncImageView_roundedCornerRadius, 0).toFloat())
        }
    }

    private fun resourceUri(resId: Int): Uri = "${prefixResourceUri}${resId}".toUri()

    fun setRoundTop(radius: Float) {
        roundedCornerRadius = RoundedCornerRadius(topLeft = radius, topRight = radius)
        if (radius > 0) {
            roundAsCircle = false
        }
    }

    fun setRoundLeft(radius: Float) {
        roundedCornerRadius = RoundedCornerRadius(topLeft = radius, bottomLeft = radius)
        if (radius > 0) {
            roundAsCircle = false
        }
    }

    fun setRound(radius: Float) {
        roundedCornerRadius = RoundedCornerRadius(radius)
        if (radius > 0) {
            roundAsCircle = false
        }
    }

    fun setCircular() {
        roundAsCircle = true
        roundedCornerRadius = RoundedCornerRadius()
    }

    fun setScaleTypeCenterInside() {
        scaleType = ScaleType.CENTER_INSIDE
    }

    fun setScaleTypeCenterCrop() {
        scaleType = ScaleType.CENTER_CROP
    }

    fun setLocalRes(resId: Int) {
        load(resourceUri(resId))
    }

    fun setImageURI(uriString: String?, callerContext: Any?) {
        if (uriString.isNullOrBlank()) {
            clear(callerContext)
        } else {
            setImageURI(uriString.toUri())
        }
    }

    fun setImageURI(uri: Uri, callerContext: Any?) {
        if (callerContext is ResizeOptions) {
            setImageURIWithResize(uri, callerContext)
        } else {
            setImageURI(uri)
        }
    }

    override fun setImageURI(uri: Uri?) {
        if (uri == null) {
            clear(null)
        } else if (uri.scheme == "res") {
            uri.path?.replaceFirst("/", "")?.toInt()?.let {
                load(resourceUri(it))
            }
        } else {
            load(uri)
        }
    }

    override fun setImageResource(resId: Int) {
        clear(null)
        super.setImageResource(resId)
    }

    private fun load(data: Any?) {
        val transformations = mutableListOf<Transformation>()
        if (blur) {
            transformations.add(BlurTransformation(20f))
        }
        if (roundAsCircle) {
            transformations.add(CircleCropTransformation())
        }
        if (!roundedCornerRadius.isEmpty) {
            transformations.add(RoundedCornersTransformation(
                topLeft = roundedCornerRadius.topLeft,
                topRight = roundedCornerRadius.topRight,
                bottomLeft = roundedCornerRadius.bottomLeft,
                bottomRight = roundedCornerRadius.bottomRight
            ))
        }
        if (!border.isEmpty) {
            transformations.add(BorderTransformation(
                borderColor = border.color,
                borderWidthPx = border.width.toFloat(),
                asCircle = roundAsCircle,
                cornerRadiusPx = roundedCornerRadius.topLeft
            ))
        }

        val builder = ImageRequest.Builder(context)
            .data(data)
            .target(this)
            .allowHardware(true)
            .crossfade(true)
            .transformations(transformations)
            .scale(coilScale)

        builder.placeholder(placeholderDrawable)

        resizeOptions?.let { ro ->
            val w = if (ro.width > 0) ro.width else coil3.size.Size.ORIGINAL.width.pxOrElse { 0 }
            val h = if (ro.height > 0) ro.height else coil3.size.Size.ORIGINAL.height.pxOrElse { 0 }
            builder.size(coil3.size.Size(w, h))
            builder.precision(Precision.EXACT)
            if (coilScale == Scale.FIT) {
                builder.scale(Scale.FILL)
            }
        }

        get(context).enqueue(builder.build())
    }

    fun setImageURIWithResize(uri: Uri, resizeOptions: ResizeOptions) {
        this.resizeOptions = resizeOptions
        setImageURI(uri)
    }

    fun setPlaceholder(drawable: Drawable?) {
        placeholderDrawable = drawable
    }

    fun clear(callerContext: Any?) {
        setImageDrawable(null)
        CoilUtils.dispose(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (viewAspectRatio) {
            0f -> super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            1f -> super.onMeasure(widthMeasureSpec, widthMeasureSpec)
            else -> {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = (width / viewAspectRatio).toInt()
                setMeasuredDimension(width, height)
            }
        }
    }
}