package ton.contract

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.base64
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef
import kotlin.jvm.JvmField

class WalletV4R2Contract(
    workchain: Int,
    publicKey: PublicKeyEd25519,
) : BaseWalletContract(workchain, publicKey) {

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBits(publicKey.key)
            storeBit(false)
        }
    }

    override fun getCode(): Cell {
        return CODE
    }

    override fun createTransferMessageBody(
        privateKey: PrivateKeyEd25519,
        validUntil: Long,
        seqno: Int,
        vararg gifts: WalletTransfer
    ): Cell {
        val unsignedBody = CellBuilder.createCell {
            storeUInt(walletId, 32)
            storeUInt(validUntil, 32)
            storeUInt(seqno, 32)
            storeUInt(0, 8)
            for (gift in gifts) {
                var sendMode = 3
                if (gift.sendMode > -1) {
                    sendMode = gift.sendMode
                }
                val intMsg = CellRef(createIntMsg(gift))

                storeUInt(sendMode, 8)
                storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
            }
        }
        val signature = BitString(privateKey.sign(unsignedBody.hash()))

        return CellBuilder.createCell {
            storeBits(signature)
            storeBits(unsignedBody.bits)
            storeRefs(unsignedBody.refs)
        }
    }

    companion object {
        @JvmField
        val CODE =
            BagOfCells(base64("te6cckECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVGliJeU=")).first()
    }
}
