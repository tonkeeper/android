package com.tonapps.singer.screen.qr

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tonapps.singer.R
import com.tonapps.singer.core.KeyEntity
import com.tonapps.singer.short4
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
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

    private val qrViewModel: QRViewModel by viewModel { parametersOf(id, body) }

    private lateinit var headerView: HeaderView
    private lateinit var contentView: View
    private lateinit var qrView: AppCompatImageView
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
        qrView.doOnLayout { qrViewModel.requestQR(it.measuredWidth, it.measuredHeight) }

        labelView = view.findViewById(R.id.label)

        doneButton = view.findViewById(R.id.done)
        doneButton.setOnClickListener { finish() }

        qrViewModel.keyEntity.onEach(::setKeyEntity).launchIn(lifecycleScope)
        qrViewModel.qrCode.onEach(::setBitmap).launchIn(lifecycleScope)

        fixPeekHeight()
    }

    private fun setKeyEntity(entity: KeyEntity) {
        val builder = StringBuilder(entity.name)
        builder.append(" / ")
        builder.append(entity.hex.short4)

        labelView.text = builder.toString()
    }

    private fun setBitmap(bitmap: Bitmap) {
        qrView.setImageBitmap(bitmap)
    }
}