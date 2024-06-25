package com.tonapps.ledger.ton

import io.ktor.util.decodeBase64Bytes
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.CellBuilder
import java.nio.ByteBuffer

data class AccountPath(
    val index: Int,
    val isTestnet: Boolean = false,
    val workchain: Int = 0
) {
    private fun getPath(): List<Int> {
        val network = if (isTestnet) 1 else 0
        val chain = if (workchain == -1) 255 else 0
        return listOf(44, 607, network, chain, index, 0)
    }

    fun toByteArray(): ByteArray {
        val paths = getPath()
        return ByteBuffer.allocate(1 + paths.size * 4).apply {
            put(paths.size.toByte())
            paths.map { (it + 0x80000000).toInt() }.forEach(this::putInt)
        }.array()
    }

    fun getInit(publicKey: PublicKeyEd25519): StateInit {
        val codeBase64 = "te6ccgECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVA=="
        val data = CellBuilder.beginCell()
            .storeUInt(0, 32)
            .storeUInt(698983191 + workchain, 32)
            .storeBytes(publicKey.key.toByteArray())
            .storeBit(false) // Empty plugins dict
            .endCell()

        return StateInit(BagOfCells(codeBase64.decodeBase64Bytes()).first(), data)
    }
}
