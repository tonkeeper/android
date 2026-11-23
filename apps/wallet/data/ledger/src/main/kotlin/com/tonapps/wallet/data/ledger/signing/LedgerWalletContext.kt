package com.tonapps.wallet.data.ledger.signing

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor

internal class LedgerWalletContext(
    val walletId: String,
    val descriptor: HardwareWalletDescriptor,
    private val accounts: List<AccountEntity>,
) {

    val accountIndex: Int
        get() = descriptor.accountIndex

    fun account(chain: Chain): AccountEntity = accounts.firstOrNull { it.chain == chain }
        ?: throw LedgerFailureException(LedgerFailure.Unsupported("${chain.name} is not added to this Ledger wallet"))

    fun path(chain: Chain): Bip32Path = descriptor.derivationPath(chain)?.let(Bip32Path::parse)
        ?: LedgerDerivation.defaultPath(chain, accountIndex)

    fun tonPublicKey(): ByteArray = publicKey(Chain.Ton.Mainnet)

    fun publicKey(chain: Chain): ByteArray = account(chain).publicKey.hexToBytes()

    fun request(purpose: LedgerPurpose): LedgerRequest = LedgerRequest(
        deviceId = descriptor.deviceId,
        deviceName = descriptor.deviceName,
        purpose = purpose,
        walletId = walletId,
    )

    companion object {

        suspend fun load(repository: McAccountRepository, walletId: String): LedgerWalletContext {
            val descriptor = repository.getHardwareDescriptor(walletId)
                ?: throw LedgerFailureException(LedgerFailure.Unexpected("Wallet is not paired with a Ledger"))
            val accounts = repository.getCoinAccounts(walletId)
            return LedgerWalletContext(walletId, descriptor, accounts)
        }
    }
}
