package com.tonapps.tonkeeper.ui.screen.ledger.steps


import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Adapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.SharedFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow

class LedgerConnectionFragment() : BaseFragment(R.layout.fragment_ledger_steps) {
    companion object {

        private const val WITH_CONFIRM_TX = "WITH_CONFIRM_TX"

        fun newInstance(showConfirmTxStep: Boolean): LedgerConnectionFragment {
            val fragment = LedgerConnectionFragment()
            fragment.arguments = Bundle().apply {
                putBoolean(WITH_CONFIRM_TX, showConfirmTxStep)
            }
            return fragment
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all {
            it.value
        }
        if (allGranted) {
            connectionViewModel.scan()
        } else {
            showBluetoothPermissionsAlert()
        }
    }
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionsAndScan()
    }

    private val showConfirmTxStep: Boolean by lazy { requireArguments().getBoolean(WITH_CONFIRM_TX) }

    private val connectionViewModel: LedgerConnectionViewModel by viewModel {
        parametersOf(
            showConfirmTxStep
        )
    }

    private val adapter = Adapter()

    private lateinit var listView: RecyclerView
    private lateinit var bluetoothIconView: AppCompatImageView
    private lateinit var ledgerView: RelativeLayout
    private lateinit var ledgerDisplayView: LinearLayout
    private lateinit var ledgerDisplayText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        collectFlow(connectionViewModel.uiItemsFlow, adapter::submitList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothIconView = view.findViewById(R.id.bluetooth_icon)
        ledgerView = view.findViewById(R.id.ledger_picture)
        ledgerDisplayView = view.findViewById(R.id.ledger_display)
        ledgerDisplayText = view.findViewById(R.id.ledger_display_text)

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter

        collectFlow(connectionViewModel.currentStepFlow, ::animateView)

        collectFlow(connectionViewModel.bluetoothState) { state ->
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    connectionViewModel.disconnect()
                    promptEnableBluetooth()
                }

                BluetoothAdapter.STATE_ON -> {
                    checkPermissionsAndScan();
                }
            }
        }
    }

    private fun showBluetoothPermissionsAlert() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(Localization.bluetooth_permissions_alert_title)
        builder.setMessage(Localization.bluetooth_permissions_alert_message)
        builder.setPositiveButton(Localization.bluetooth_permissions_alert_open_settings) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            appSettingsLauncher.launch(intent)
        }
        builder.setNegativeButton(Localization.cancel)
        builder.show()
    }

    private fun promptEnableBluetooth() {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    private fun checkPermissionsAndScan() {
        if (connectionViewModel.isPermissionGranted()) {
            connectionViewModel.scan()
        } else {
            requestPermissionLauncher.launch(connectionViewModel.permissions)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun animateView(currentStep: LedgerStep) {
        val interpolator = AccelerateDecelerateInterpolator()

        if (currentStep == LedgerStep.CONNECT) {
            val blFadeAnim =
                ObjectAnimator.ofFloat(bluetoothIconView, "alpha", bluetoothIconView.alpha, 1f)
            blFadeAnim.duration = 300
            blFadeAnim.startDelay = 200
            blFadeAnim.interpolator = interpolator
            blFadeAnim.start()

            val displayFadeAnim =
                ObjectAnimator.ofFloat(ledgerDisplayView, "alpha", ledgerDisplayView.alpha, 0f)
            displayFadeAnim.duration = 300
            displayFadeAnim.interpolator = interpolator
            displayFadeAnim.start()

            val translationX = ObjectAnimator.ofFloat(
                ledgerView, "translationX", ledgerView.translationX, dpToPx(24f)
            )
            translationX.duration = 300
            translationX.startDelay = 200
            translationX.interpolator = interpolator
            translationX.start()
        } else {
            val blFadeAnim =
                ObjectAnimator.ofFloat(bluetoothIconView, "alpha", bluetoothIconView.alpha, 0f)
            blFadeAnim.duration = 300
            blFadeAnim.interpolator = interpolator
            blFadeAnim.start()

            val displayFadeAnim =
                ObjectAnimator.ofFloat(ledgerDisplayView, "alpha", ledgerDisplayView.alpha, 1f)
            displayFadeAnim.duration = 300
            displayFadeAnim.startDelay = 150
            displayFadeAnim.interpolator = interpolator
            displayFadeAnim.start()

            val translationX = ObjectAnimator.ofFloat(
                ledgerView, "translationX", ledgerView.translationX, dpToPx(-42f)
            )
            translationX.duration = 350
            translationX.interpolator = interpolator
            translationX.start()
        }

        ledgerDisplayText.text = when {
            showConfirmTxStep && (currentStep == LedgerStep.CONFIRM_TX || currentStep == LedgerStep.DONE) -> "Review"
            else -> "TON ready"
        }
    }
}
