package com.tonkeeper.dialog.fiat

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.core.fiat.models.FiatItem
import com.tonkeeper.core.fiat.models.FiatSuccessUrlPattern
import com.tonkeeper.dialog.fiat.list.MethodAdapter
import com.tonkeeper.dialog.fiat.list.MethodItem
import com.tonkeeper.fragment.country.CountryScreen
import com.tonkeeper.fragment.fiat.FiatWebFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uikit.base.BaseSheetDialog
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.HeaderView

class FiatDialog(
    context: Context,
    private val scope: CoroutineScope
): BaseSheetDialog(context) {

    companion object {
        const val FIAT_DIALOG_REQUEST = "fiat_dialog_request"
    }

    private val confirmationDialog: ConfirmationDialog by lazy {
        ConfirmationDialog(context)
    }

    private val onItemClick: (item: MethodItem) -> Unit = {
        openItem(it.body)
    }

    private val headerView: HeaderView
    private val listView: RecyclerView

    init {
        setContentView(R.layout.dialog_fiat)
        hideHeader()

        headerView = findViewById(R.id.header)!!
        headerView.setBackgroundColor(Color.TRANSPARENT)
        headerView.doOnCloseClick = { pickCountry() }
        headerView.doOnActionClick = { dismiss() }

        listView = findViewById(R.id.list)!!
    }

    override fun show() {
        scope.launch {
            val items = App.fiat.getMethods(App.settings.country)
            showWithData(items)
        }
    }

    private  fun showWithData(items: List<FiatItem>) {
        super.show()
        listView.adapter = MethodAdapter(MethodAdapter.buildMethodItems(items), onItemClick)
    }

    private fun openItem(item: FiatItem) {
        scope.launch {
            if (isShowConfirmation(item.id)) {
                showConfirmDialog(item)
            } else {
                openUrl(item.actionButton.url, item.successUrlPattern)
            }
        }
    }

    private suspend fun isShowConfirmation(
        id: String
    ): Boolean {
        return App.fiat.isShowConfirmation(id)
    }

    private fun disableShowConfirmation(id: String) {
        scope.launch {
            App.fiat.disableShowConfirmation(id)
        }
    }

    private fun showConfirmDialog(item: FiatItem) {
        confirmationDialog.show(item) { disableConfirm ->
            openUrl(item.actionButton.url, item.successUrlPattern)
            if (disableConfirm) {
                disableShowConfirmation(item.id)
            }
        }
    }

    private fun openUrl(
        url: String,
        pattern: FiatSuccessUrlPattern?
    ) {
        dismiss()
        nav()?.add(FiatWebFragment.newInstance(url, pattern))
    }

    private fun pickCountry() {
        dismiss()
        nav()?.add(CountryScreen.newInstance(FIAT_DIALOG_REQUEST))
    }
}