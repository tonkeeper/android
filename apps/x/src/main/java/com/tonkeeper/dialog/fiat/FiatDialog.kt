package com.tonkeeper.dialog.fiat

import android.content.Context
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.fiat.models.FiatItem
import com.tonkeeper.core.fiat.models.FiatSuccessUrlPattern
import com.tonkeeper.dialog.fiat.list.MethodAdapter
import com.tonkeeper.fragment.country.CountryScreen
import com.tonkeeper.fragment.fiat.web.FiatWebFragment
import com.tonkeeper.fragment.root.RootActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uikit.base.BaseSheetDialog
import uikit.extensions.activity
import uikit.list.LinearLayoutManager
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class FiatDialog(
    context: Context,
    private val scope: CoroutineScope
): BaseSheetDialog(context) {

    companion object {
        const val FIAT_DIALOG_REQUEST = "fiat_dialog_request"

        fun open(context: Context) {
            val rootActivity = context.activity as? RootActivity ?: return
            rootActivity.fiatDialog.show()
        }
    }

    private val confirmationDialog: ConfirmationDialog by lazy {
        ConfirmationDialog(context)
    }

    private val adapter = MethodAdapter {
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
        listView.adapter = adapter
        listView.layoutManager = LinearLayoutManager(context)
    }

    override fun show() {
        scope.launch {
            val items = App.fiat.getMethods(App.settings.country)
            showWithData(items)
        }
    }

    private  fun showWithData(items: List<FiatItem>) {
        super.show()
        adapter.submitList(MethodAdapter.buildMethodItems(items)) {
            fixPeekHeight()
        }
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
        navigation?.add(FiatWebFragment.newInstance(url, pattern))
    }

    private fun pickCountry() {
        dismiss()
        navigation?.add(CountryScreen.newInstance(FIAT_DIALOG_REQUEST))
    }
}