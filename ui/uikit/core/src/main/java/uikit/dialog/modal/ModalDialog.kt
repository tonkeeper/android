package uikit.dialog.modal

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.google.android.material.bottomsheet.BottomSheetDialog

open class ModalDialog(context: Context, @LayoutRes layoutId: Int): BottomSheetDialog(context) {

    init {
        super.setContentView(layoutId)
    }
}