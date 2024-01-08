package uikit.list

import android.content.Context
import androidx.recyclerview.widget.RecyclerView

class LinearLayoutManager @JvmOverloads constructor(
    context: Context,
    orientation: Int = RecyclerView.VERTICAL,
    reverseLayout: Boolean = false,
): androidx.recyclerview.widget.LinearLayoutManager(context, orientation, reverseLayout) {

    override fun supportsPredictiveItemAnimations(): Boolean = false

}