package com.tonapps.wallet.data.events

object TxActionType {
    const val TonTransfer = "TonTransfer"
    const val JettonTransfer = "JettonTransfer"
    const val JettonBurn = "JettonBurn"
    const val JettonMint = "JettonMint"
    const val NftItemTransfer = "NftItemTransfer"
    const val ContractDeploy = "ContractDeploy"
    const val Subscribe = "Subscribe"
    const val UnSubscribe = "UnSubscribe"
    const val AuctionBid = "AuctionBid"
    const val NftPurchase = "NftPurchase"
    const val DepositStake = "DepositStake"
    const val WithdrawStake = "WithdrawStake"
    const val WithdrawStakeRequest = "WithdrawStakeRequest"
    const val JettonSwap = "JettonSwap"
    const val SmartContractExec = "SmartContractExec"
    const val ElectionsRecoverStake = "ElectionsRecoverStake"
    const val ElectionsDepositStake = "ElectionsDepositStake"
    const val DomainRenew = "DomainRenew"
    const val InscriptionTransfer = "InscriptionTransfer"
    const val InscriptionMint = "InscriptionMint"
    const val Unknown = "Unknown"
}