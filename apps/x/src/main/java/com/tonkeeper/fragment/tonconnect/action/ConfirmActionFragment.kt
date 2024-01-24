package com.tonkeeper.fragment.tonconnect.action

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.history.list.HistoryAdapter
import com.tonkeeper.core.tonconnect.TonConnect
import com.tonkeeper.core.tonconnect.models.TCTransaction
import com.tonkeeper.fragment.passcode.lock.LockScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.list.LinearLayoutManager
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ProcessTaskView

class ConfirmActionFragment: BaseFragment(R.layout.dialog_confirm_action), BaseFragment.Modal {

    companion object {
        private const val SIGN_REQUEST_KEY = "sign_request"

        fun newInstance(transaction: TCTransaction): ConfirmActionFragment {
            val fragment = ConfirmActionFragment()
            fragment.transaction = transaction
            return fragment
        }
    }

    private val tonConnect: TonConnect?
        get() = TonConnect.from(requireContext())

    private val adapter = object : HistoryAdapter(true) {
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerView.setHasFixedSize(false)
            recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    var transaction: TCTransaction? = null

    private lateinit var closeView: View
    private lateinit var listView: RecyclerView
    private lateinit var feeView: AppCompatTextView
    private lateinit var cancelButton: Button
    private lateinit var confirmButton: Button
    private lateinit var buttonsView: View
    private lateinit var processView: ProcessTaskView

    private var isConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(SIGN_REQUEST_KEY) { _ ->
            lifecycleScope.launch { confirm() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        closeView = view.findViewById(R.id.action_close)!!
        closeView.setOnClickListener { cancel() }

        listView = view.findViewById(R.id.list)!!
        listView.adapter = adapter
        listView.layoutManager = LinearLayoutManager(requireContext())

        feeView = view.findViewById(R.id.fee)!!

        cancelButton = view.findViewById(R.id.cancel)!!
        cancelButton.setOnClickListener { cancel() }

        confirmButton = view.findViewById(R.id.confirm)!!

        buttonsView = view.findViewById(R.id.buttons)!!
        processView = view.findViewById(R.id.process)!!

        init()
    }

    private fun init() {
        val transaction = transaction ?: return

        feeView.text = transaction.fee

        confirmButton.setOnClickListener {
            navigation?.add(LockScreen.newInstance(SIGN_REQUEST_KEY))
        }

        adapter.submitList(transaction.previewItems)
    }

    private suspend fun confirm()  {
        val transaction = transaction ?: return
        val tonConnect = TonConnect.from(requireContext()) ?: return
        buttonsView.visibility = View.GONE
        processView.visibility = View.VISIBLE

        try {
            tonConnect.signTransaction(transaction.id, transaction.clientId, transaction.transfers)
            processView.state = ProcessTaskView.State.SUCCESS

            isConfirmed = true
        } catch (e: Throwable) {
            processView.state = ProcessTaskView.State.FAILED
        }

        delay(1000)
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (!isConfirmed) {
            cancel()
        }
    }

    private fun cancel() {
        val transaction = transaction ?: return

        tonConnect?.cancelTransaction(transaction.id, transaction.clientId)
        finish()
    }
}