package ton

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.crypto.base64
import org.ton.mnemonic.Mnemonic

class WalletInfo(
    val words: List<String>,
    private val seed: ByteArray,
) {

    private companion object {
        private val v4r2Code =
            BagOfCells(base64("te6cckECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVGliJeU=")).first()
    }

    val workchain = 0
    val walletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain

    constructor(words: List<String>) : this(words, Mnemonic.toSeed(words))

    val privateKey: PrivateKeyEd25519 by lazy {
        PrivateKeyEd25519(seed)
    }

    val publicKey: PublicKeyEd25519 by lazy {
        privateKey.publicKey()
    }

    val contract: WalletV4R2Contract by lazy {
        WalletV4R2Contract(0, publicKey)
    }

    val stateInit: StateInit by lazy {
        val data = CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBits(publicKey.key)
            storeBit(false)
        }
        StateInit(
            code = v4r2Code,
            data = data
        )
    }

    val accountId: String by lazy {
        MsgAddressInt.toString(
            contract.address,
            userFriendly = false
        )
    }

    val address: String by lazy {
        if (false) {
            "EQD2NmD_lH5f5u1Kj3KfGyTvhZSX0Eg6qp2a5IQUKXxOG21n"
        } else {
            accountId
        }
    }

    fun isMyAddress(address: String): Boolean {
        return try {
            AddrStd.parse(this.address) == AddrStd.parse(address)
        } catch (e: Throwable) {
            false
        }
    }

    override fun toString(): String {
        return "WalletInfo(address='$address')"
    }
}