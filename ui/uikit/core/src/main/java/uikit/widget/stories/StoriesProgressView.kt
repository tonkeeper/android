package uikit.widget.stories

import android.content.Context
import android.util.AttributeSet
import android.view.View

class StoriesProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val drawable = StoriesProgressDrawable(context)

    var progress: Float
        get() = drawable.progress
        set(value) {
            drawable.progress = value
        }

    init {
        background = drawable
    }


    override fun hasOverlappingRendering() = false
}