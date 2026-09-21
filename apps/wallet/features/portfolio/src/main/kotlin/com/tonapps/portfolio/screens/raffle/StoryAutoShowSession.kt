package com.tonapps.portfolio.screens.raffle

class StoryAutoShowSession {

    enum class Owner {
        Raffle,
        Config,
    }

    private val ownerByWalletId = mutableMapOf<String, Owner>()

    @Synchronized
    fun hasShown(walletId: String): Boolean = walletId in ownerByWalletId

    @Synchronized
    fun tryAcquire(walletId: String, owner: Owner): Boolean {
        val current = ownerByWalletId[walletId]
        if (current == null) {
            ownerByWalletId[walletId] = owner
            return true
        }
        return current == owner
    }

    @Synchronized
    fun release(walletId: String, owner: Owner) {
        if (ownerByWalletId[walletId] == owner) {
            ownerByWalletId.remove(walletId)
        }
    }

    @Synchronized
    fun reset() {
        ownerByWalletId.clear()
    }
}
