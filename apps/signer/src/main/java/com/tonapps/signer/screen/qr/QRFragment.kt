package com.tonapps.signer.screen.qr

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tonapps.signer.R
import com.tonapps.signer.core.entities.KeyEntity
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.extensions.short4
import kotlinx.coroutines.flow.filterNotNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import qr.QRView
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView

class QRFragment: BaseFragment(R.layout.fragment_qr), BaseFragment.Modal {

    companion object {

        private const val ID_KEY = "id"
        private const val BODY_KEY = "body"

        fun newInstance(id: Long, body: String): QRFragment {
            val fragment = QRFragment()
            fragment.arguments = Bundle().apply {
                putLong(ID_KEY, id)
                putString(BODY_KEY, body)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(ID_KEY) }
    private val body: String by lazy { requireArguments().getString(BODY_KEY)!! }
    private val keyRepository: KeyRepository by inject()

    private lateinit var headerView: HeaderView
    private lateinit var contentView: View
    private lateinit var qrView: QRView
    private lateinit var labelView: AppCompatTextView
    private lateinit var doneButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        contentView = view.findViewById(R.id.content)
        contentView.background = QRBackground(requireContext())

        qrView = view.findViewById(R.id.qr)
        qrView.content = body

        labelView = view.findViewById(R.id.label)

        doneButton = view.findViewById(R.id.done)
        doneButton.setOnClickListener { finish() }

        collectFlow(keyRepository.getKey(id).filterNotNull(), ::setKeyEntity)

        fixPeekHeight()
    }

    private fun setKeyEntity(entity: KeyEntity) {
        val builder = StringBuilder(entity.name)
        builder.append(" / ")
        builder.append(entity.hex.short4)

        labelView.text = builder.toString()
    }
}