package com.tonapps.tonkeeper.ui.screen.init.pager.child

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.data.AccountColor
import com.tonapps.tonkeeper.ui.screen.name.base.NameFragment
import com.tonapps.tonkeeper.ui.screen.name.base.NameModeCreate
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.extensions.collectFlow
import uikit.extensions.setPaddingTop

class NameChild: NameFragment(NameModeCreate) {

    companion object {

        private const val VALUE_KEY = "value"

        fun newInstance(name: String?): NameChild {
            val fragment = NameChild()
            fragment.arguments?.putString(VALUE_KEY, name)
            return fragment
        }
    }

    private val nameValue: String by lazy { arguments?.getString(VALUE_KEY) ?: "" }

    private val initViewModel: InitViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel.uiTopOffset.onEach {
            view.setPaddingTop(it)
        }.launchIn(lifecycleScope)

        collectFlow(initViewModel.loading, ::setLoading)
    }

    override fun onResume() {
        super.onResume()
        setName(nameValue)
        setColor(AccountColor.all.first())
        setEmoji("\uD83D\uDC8E")
        focus(true)
    }

    override fun onPause() {
        super.onPause()
        focus(false)
    }

    override fun onData(name: String, emoji: CharSequence, color: Int) {
        initViewModel.setData(name, emoji, color)
    }

}