package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add

import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.ur.ResultType
import com.tonapps.ur.URDecoder
import com.tonapps.ur.registry.CryptoHDKey
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex
import uikit.base.BaseFragment
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation

class KeystoneAddScreen: QRCameraScreen(R.layout.fragment_add_keystone), BaseFragment.BottomSheet {

    private data class KeystoneData(
        val publicKey: PublicKeyEd25519,
        val xfp: String,
        val path: String,
        val name: String?,
    )

    private val urDecoder = URDecoder()

    override lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        val aboutView = view.findViewById<View>(R.id.about)
        aboutView.backgroundTintList = constantWhiteColor
        aboutView.setOnClickListener { navigation?.openURL("https://keyst.one/") }

        cameraView = view.findViewById(R.id.camera)

        observeQRCode()
    }

    private fun observeQRCode() {
        readerFlow.map { urDecoder.receivePart(fixReceivedPart(it)) }
            .filter { it }
            .map { urDecoder.result }
            .filter { it.type == ResultType.SUCCESS }
            .map { it.ur.decodeFromRegistry() as? CryptoHDKey }
            .filterNotNull()
            .filter { !it.isPrivateKey }
            .map {
                KeystoneData(
                    publicKey = PublicKeyEd25519(it.key),
                    xfp = hex(it.origin.sourceFingerprint),
                    path = "m/" + it.origin.path,
                    name = it.name
                )
            }
            .flowOn(Dispatchers.IO)
            .catch { navigation?.toast(Localization.unknown_error) }
            .onEach(::addAccount)
            .launchIn(lifecycleScope)
    }

    private fun fixReceivedPart(part: String): String {
        if (part.startsWith("http://", ignoreCase = true)) {
            return part.removePrefix("http://")
        } else if (part.startsWith("https://", ignoreCase = true)) {
            return part.removePrefix("https://")
        }
        return part
    }

    private fun addAccount(data: KeystoneData) {
        navigation?.add(InitScreen.newInstance(
            type = InitArgs.Type.Keystone,
            publicKeyEd25519 = data.publicKey,
            name = data.name,
            keystone = WalletEntity.Keystone(data.xfp, data.path)
        ))
        finish()
    }

    companion object {
        fun newInstance() = KeystoneAddScreen()
    }
}