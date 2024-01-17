package com.tonapps.singer.screen.create.password

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.CreateViewModel
import com.tonapps.singer.screen.create.pager.PageType
import com.tonapps.singer.screen.password.PasswordFragment
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel

class RepeatPasswordFragment: PasswordFragment() {

    companion object {
        fun newInstance() = RepeatPasswordFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = getString(R.string.re_enter_password)

        createViewModel.currentPage.filter { it == PageType.RepeatPassword }.onEach {
            focus()
        }.launchIn(lifecycleScope)
    }

    override fun onPasswordSent(password: String): Boolean {
        return createViewModel.checkPassword(password)
    }
}