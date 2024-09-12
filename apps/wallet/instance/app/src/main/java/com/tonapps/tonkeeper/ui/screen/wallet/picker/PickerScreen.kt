package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item.Companion.height
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.Holder
import com.tonapps.uikit.color.buttonSecondaryForegroundColor
import com.tonapps.uikit.list.LinearLayoutManager
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal

class PickerScreen: BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.Modal {

    override val scaleBackground: Boolean = true

    override val viewModel: PickerViewModel by viewModel {
        parametersOf(requireArguments().getString(ARG_WALLET_ID_FOCUS)!!)
    }

    private val adapter = Adapter { wallet ->
        viewModel.setWallet(wallet)
        finish()
    }

    private lateinit var actionButton: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val horizontalOffset = requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium)

        actionButton = createActionButton(view.context)
        actionButton.setOnClickListener { viewModel.toggleEditMode() }
        addViewHeader(actionButton, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 32.dp, Gravity.CENTER_VERTICAL).also {
            it.marginEnd = horizontalOffset
            it.marginStart = horizontalOffset
        })
        setTitle(getString(Localization.wallets))
        setAdapter(adapter)

        setTouchHelperCallback(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun isLongPressDragEnabled() = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                val item = adapter.getItem(fromPosition) as? Item.Wallet ?: return false
                if (!item.editMode) {
                    return false
                }
                adapter.moveItem(fromPosition, toPosition)
                HapticHelper.impactLight(requireContext())
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.getItem(position) as? Item.Wallet ?: return
                if (item.editMode) {
                    viewModel.saveOrder(adapter.rebuild())
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

        })

        collectFlow(viewModel.editModeFlow, ::applyEditMove)
        collectFlow(viewModel.uiItemsFlow, ::setNewList)
    }

    private fun setNewList(list: List<Item>) {
        updateBottomSheetHeight(list.height + headerContainer.height + (16.dp * 2))
        adapter.submitList(list)
    }

    private fun updateBottomSheetHeight(height: Int) {
        val maxHeight = coordinatorView.measuredHeight - 72.dp
        val newHeight = height.coerceAtMost(maxHeight)
        if (bottomSheetView.measuredHeight != newHeight) {
            bottomSheetView.updateLayoutParams<ViewGroup.LayoutParams> {
                this.height = newHeight
            }
        }
    }

    private fun applyEditMove(edit: Boolean) {
        actionButton.setText(if (edit) Localization.done else Localization.edit)

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

        private const val ARG_WALLET_ID_FOCUS = "wallet_id_focus"

        fun newInstance(walletIdFocus: String = ""): PickerScreen {
            val fragment = PickerScreen()
            fragment.putStringArg(ARG_WALLET_ID_FOCUS, walletIdFocus)
            return fragment
        }
    }
}