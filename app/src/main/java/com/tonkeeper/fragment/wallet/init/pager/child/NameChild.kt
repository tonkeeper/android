package com.tonkeeper.fragment.wallet.init.pager.child

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import com.tonapps.tonkeeperx.R
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.wallet.init.InitModel
import uikit.base.BaseFragment
import uikit.extensions.startSnakeAnimation
import uikit.widget.InputView
import uikit.widget.LoaderView

class NameChild: BaseFragment(R.layout.fragment_name) {

    companion object {
        fun newInstance() = NameChild()
    }

    private val parentFeature: InitModel by viewModels({ requireParentFragment() })

    private lateinit var nameInput: InputView
    private lateinit var saveButton: Button
    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nameInput = view.findViewById(R.id.name)

        saveButton = view.findViewById(R.id.save)
        saveButton.setOnClickListener { save() }

        nameInput.doOnTextChange = {
            saveButton.isEnabled = it.isNotBlank()
        }

        loaderView = view.findViewById(R.id.name_loading)

        parentFeature.loading.launch(this) { loading ->
            if (loading) {
                setLoadingState()
            } else {
                setDefaultState()
            }
        }
    }

    private fun setLoadingState() {
        saveButton.isEnabled = false
        saveButton.text = ""
        nameInput.isEnabled = false

        loaderView.visibility = View.VISIBLE
        loaderView.resetAnimation()
    }

    private fun setDefaultState() {
        saveButton.isEnabled = nameInput.text.isNotBlank()
        saveButton.text = getString(R.string.save)
        nameInput.isEnabled = true

        loaderView.visibility = View.GONE
        loaderView.stopAnimation()
    }

    private fun save() {
        val name = nameInput.text.trim()
        if (name.isEmpty()) {
            nameInput.startSnakeAnimation()
            return
        }
        parentFeature.setName(name)
    }

    override fun onResume() {
        super.onResume()
        nameInput.focus()
    }
}