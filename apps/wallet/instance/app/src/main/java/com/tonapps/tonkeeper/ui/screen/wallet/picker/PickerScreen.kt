package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.WalletPickerAdapter
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.Holder
import com.tonapps.uikit.color.buttonSecondaryForegroundColor
import com.tonapps.wallet.localization.Localization
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal

class PickerScreen: BaseListFragment(), BaseFragment.Modal {

    override val scaleBackground: Boolean = true

    private val pickerViewModel: PickerViewModel by viewModel()
    private val adapter: WalletPickerAdapter by inject()

    private lateinit var actionButton: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(pickerViewModel.walletChangedFlow) {
            finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val horizontalOffset = requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium)

        actionButton = createActionButton(view.context)
        actionButton.setOnClickListener { pickerViewModel.toggleEditMode() }
        addViewHeader(actionButton, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 32.dp, Gravity.CENTER_VERTICAL).also {
            it.marginEnd = horizontalOffset
            it.marginStart = horizontalOffset
        })

        setListPadding(horizontalOffset, 0, horizontalOffset, 0)
        setTitle(getString(Localization.wallets))
        setAdapter(adapter)

        setTouchHelperCallback(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun isLongPressDragEnabled() = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val item = (viewHolder as? Holder<*>)?.item ?: return false
                if (item is Item.Wallet && item.editMode) {
                    adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    HapticHelper.impactLight(requireContext())
                    return true
                }
                return false
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                val item = (viewHolder as? Holder<*>)?.item ?: return
                if (item is Item.Wallet && item.editMode) {
                    pickerViewModel.saveOrder(adapter.rebuild())
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

        })

        collectFlow(pickerViewModel.editModeFlow, ::applyEditMove)
    }

    private fun applyEditMove(edit: Boolean) {
        if (edit) {
            actionButton.setText(Localization.done)
        } else {
            actionButton.setText(Localization.edit)
        }
        adapter.submitList(adapter.currentList.map {
            if (it is Item.Wallet) {
                it.copy(editMode = edit)
            } else {
                it
            }
        })
    }

    private fun createActionButton(context: Context): AppCompatTextView {
        val button = AppCompatTextView(context)
        button.setPaddingHorizontal(14.dp)
        button.gravity = Gravity.CENTER
        button.setBackgroundResource(uikit.R.drawable.bg_button_secondary)
        button.setTextAppearance(uikit.R.style.TextAppearance_Label2)
        button.setTextColor(requireContext().buttonSecondaryForegroundColor)
        return button
    }

    companion object {
        fun newInstance() = PickerScreen()
    }
}