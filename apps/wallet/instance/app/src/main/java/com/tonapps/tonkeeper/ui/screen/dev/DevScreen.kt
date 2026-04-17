@file:Suppress("DEPRECATION")

package com.tonapps.tonkeeper.ui.screen.dev

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ShareCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.locale
import com.tonapps.extensions.retrieveUri
import com.tonapps.log.L
import com.tonapps.security.Security
import com.tonapps.settings.dev.DevSettingsFragment
import com.tonapps.settings.dev.ROUTE_FEATURE_FLAGS
import com.tonapps.settings.dev.ROUTE_TOOLTIPS
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.manager.push.FirebasePush
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.dev.list.launcher.LauncherAdapter
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.list.LinearLayoutManager
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.widget.HeaderView
import uikit.widget.item.ItemSwitchView
import uikit.widget.item.ItemTextView

class DevScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_dev, ScreenContext.None), BaseFragment.BottomSheet {

    override val fragmentName: String = "DevScreen"

    override val viewModel: DevViewModel by viewModel()

    private lateinit var iconsView: RecyclerView
    private lateinit var tetraView: ItemSwitchView
    private lateinit var blurView: ItemSwitchView
    private lateinit var tonConnectLogsView: ItemSwitchView
    private lateinit var logs: ItemSwitchView
    private lateinit var shareLogs: ItemTextView
    private lateinit var importMnemonicAgainView: View
    private lateinit var logView: View
    private lateinit var logDataView: AppCompatEditText
    private lateinit var logCopy: Button
    private lateinit var importPasscodeView: View
    private lateinit var importDAppsView: View
    private lateinit var systemFontSizeView: ItemSwitchView
    private lateinit var debugCountryInput: EditText
    private lateinit var debugCountryTextView: AppCompatTextView
    private lateinit var dnsAllView: ItemSwitchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnCloseClick = { finish() }

        val deviceView = view.findViewById<AppCompatTextView>(R.id.device)
        deviceView.text = getDeviceLines().joinToString("\n")

        iconsView = view.findViewById(R.id.icons)
        iconsView.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL)
        iconsView.adapter = LauncherAdapter()

        tetraView = view.findViewById(R.id.tetra)
        tetraView.setChecked(DevSettings.tetraEnabled, false)
        tetraView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.tetraEnabled = isChecked
            }
        }

        dnsAllView = view.findViewById(R.id.dns_all)
        dnsAllView.setChecked(DevSettings.dnsAll, false)
        dnsAllView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.dnsAll = isChecked
                toastAfterChange()
            }
        }

        blurView = view.findViewById(R.id.blur)
        blurView.setChecked(DevSettings.blurEnabled, false)
        blurView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.blurEnabled = isChecked
                toastAfterChange()
            }
        }

        systemFontSizeView = view.findViewById(R.id.ignore_system_font_size)
        systemFontSizeView.setChecked(DevSettings.ignoreSystemFontSize, false)
        systemFontSizeView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.ignoreSystemFontSize = isChecked
                toastAfterChange()
            }
        }

        tonConnectLogsView = view.findViewById(R.id.tc_logs)
        tonConnectLogsView.setChecked(DevSettings.tonConnectLogs, false)
        tonConnectLogsView.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.tonConnectLogs = isChecked
                toastAfterChange()
            }
        }

        logs = view.findViewById(R.id.logs)
        logs.setChecked(DevSettings.isLogsEnabled, false)
        logs.doOnCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                DevSettings.isLogsEnabled = isChecked
                L.setTargets(L.defaultTargets(requireContext(), isChecked))
            }
        }

        shareLogs = view.findViewById(R.id.share_logs)
        shareLogs.setOnClickListener {
            if (!L.hasLogs()) {
                navigation?.toast("No logs found!")
                return@setOnClickListener
            }

            L.capture { file ->
                val context = requireContext()
                lifecycleScope.launch {
                    DevSettings.isLogsEnabled = false
                    L.setTargets(L.defaultTargets(context, false))

                    ShareCompat.IntentBuilder(context)
                        .run {
                            val uri = retrieveUri(context, file ?: return@launch)
                            setStream(uri)

                            intent.apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Share logs")

                                setDataAndType(uri, "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            setChooserTitle("Share logs")
                            startChooser()
                        }
                }
            }
        }


        importMnemonicAgainView = view.findViewById(R.id.import_mnemonic_again)
        importMnemonicAgainView.setOnClickListener { importMnemonicAgain(false) }
        importMnemonicAgainView.setOnLongClickListener { importMnemonicAgain(true); true }

        importPasscodeView = view.findViewById(R.id.import_passcode)
        importPasscodeView.setOnClickListener { importPasscode() }

        importDAppsView = view.findViewById(R.id.import_dapps)
        importDAppsView.setOnClickListener { importDApps() }

        logView = view.findViewById(R.id.log)
        logDataView = view.findViewById(R.id.log_data)

        debugCountryTextView = view.findViewById(R.id.debug_country_list)

        collectFlow(viewModel.debugCountryFlow, debugCountryTextView::setText)

        debugCountryInput = view.findViewById(R.id.debug_country_input)
        debugCountryInput.setText(DevSettings.country)
        debugCountryInput.doOnTextChanged { text, start, before, count ->
            val country = text.toString().trim().ifEmpty { null }
            viewModel.setCountry(country)
            if (country == null || country.length == 2) {
                toastAfterChange()
            }
        }
        if (!BuildConfig.DEBUG) {
            debugCountryInput.visibility = View.GONE
        }

        view.findViewById<Button>(R.id.log_close).setOnClickListener {
            logView.visibility = View.GONE
        }

        view.findViewById<View>(R.id.import_legacy).setOnLongClickListener {
            valuesFromLegacy()
            true
        }

        view.findViewById<View>(R.id.card).setOnLongClickListener {
            viewModel.openCard()
            true
        }

        view.findViewById<View>(R.id.copy_firebase_push).setOnClickListener {
            copyFirebasePushToken()
        }

        view.findViewById<View>(R.id.copy_install_id).setOnClickListener {
            requireContext().copyToClipboard(viewModel.installId, true)
            navigation?.toast("Install ID copied to clipboard")
        }

        logCopy = view.findViewById(R.id.log_copy)

        view.findViewById<View>(R.id.feature_flags).apply {
             if (!BuildConfig.DEBUG) {
                 visibility = View.GONE
             }
            setOnClickListener {
                navigation?.add(DevSettingsFragment.newInstance(ROUTE_FEATURE_FLAGS))
            }
        }

        view.findViewById<View>(R.id.tooltips).apply {
            if (!BuildConfig.DEBUG) {
                visibility = View.GONE
            }
            setOnClickListener {
                navigation?.add(DevSettingsFragment.newInstance(ROUTE_TOOLTIPS))
            }
        }
    }

    private fun copyFirebasePushToken() {
        lifecycleScope.launch {
            val token = FirebasePush.requestToken()
            if (token.isNullOrBlank()) {
                navigation?.toast("Failed to get Firebase push token", requireContext().accentRedColor)
            } else {
                requireContext().copyToClipboard(token, true)
                navigation?.toast("Firebase push token copied to clipboard")
            }
        }
    }

    private fun toastAfterChange() {
        requireContext().showToast("Restart app to apply changes")
    }

    private fun valuesFromLegacy() {
        navigation?.migrationLoader(true)
        viewModel.getLegacyStorage {
            showLog(it)
            navigation?.migrationLoader(false)
        }
    }

    private fun importDApps() {
        navigation?.migrationLoader(true)
        viewModel.importApps {
            showLog(it)
            navigation?.migrationLoader(false)
        }
    }

    private fun importPasscode() {
        navigation?.migrationLoader(true)
        viewModel.importPasscode {
            navigation?.migrationLoader(false)
        }
    }

    private fun importMnemonicAgain(withDisplayMnemonic: Boolean) {
        navigation?.migrationLoader(true)
        viewModel.importMnemonicAgain(withDisplayMnemonic) {
            showLog(it)
            navigation?.migrationLoader(false)
        }
    }

    private fun showLog(message: String) {
        logView.visibility = View.VISIBLE
        logDataView.setText(message)

        logCopy.setOnClickListener {
            requireContext().copyToClipboard(message, true)
        }
    }

    private fun getDeviceLines(): List<String> {
        val list = mutableListOf<String>()
        list.add("Context locale: ${requireContext().locale.language}")
        list.add("Android version: ${android.os.Build.VERSION.RELEASE} (API level ${android.os.Build.VERSION.SDK_INT})")
        list.add("Device model: ${android.os.Build.MODEL}")
        list.add("Screen size: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        list.add("Device rooted: ${booleanToYesOrNo(Security.isDeviceRooted())}")
        list.add("Device strongbox: ${booleanToYesOrNo(Security.isSupportStrongBox(requireContext()))}")
        list.add("ADB enabled: ${booleanToYesOrNo(Security.isAdbEnabled(requireContext()))}")
        list.add("Package name: ${requireContext().packageName}")
        return list
    }

    private fun booleanToYesOrNo(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    companion object {

        fun newInstance() = DevScreen()
    }
}
