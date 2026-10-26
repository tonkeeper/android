package com.tonapps.wallet.data.multichain.lighter

import android.util.Log
import com.tonapps.security.multichain.SessionKeyStore
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LighterCredentialsStoreTest {

    private val rows = mutableMapOf<String, ByteArray>()
    private val accounts = mutableMapOf<String, StoredLighterAccount>()

    private val dao = mockk<CredentialDao> {
        every { getData(any()) } answers { rows[firstArg()] }
        every { credentialExists(any()) } answers { firstArg<String>() in rows }
        every { insertCredential(any()) } answers { firstArg<CredentialEntity>().let { rows[it.id] = it.data } }
        every { insertCredentialIfAbsent(any()) } answers {
            val row = firstArg<CredentialEntity>()
            if (rows.putIfAbsent(row.id, row.data) == null) {
                1L
            } else {
                -1L
            }
        }
        every { deleteCredential(any()) } answers { rows.remove(firstArg<String>()) }
        every { deleteCredentialIfUnchanged(any(), any()) } answers {
            val id = firstArg<String>()
            if (rows[id]?.contentEquals(secondArg<ByteArray>()) == true) {
                rows.remove(id)
            }
        }
    }

    // Sealing is the identity here: what is under test is where each part lands.
    private val sessionKeyStore = mockk<SessionKeyStore> {
        coEvery { awaitUnlocked(any()) } returns true
        every { isUnlocked } returns true
        every { encrypt(any()) } answers { firstArg<ByteArray>().copyOf() }
        every { decrypt(any()) } answers { firstArg<ByteArray>().copyOf() }
    }

    private val prefs = mockk<LighterAccountPrefs> {
        every { load(any()) } answers { accounts[firstArg()] }
        every { save(any(), any()) } answers { accounts[firstArg()] = secondArg() }
        every { remove(any()) } answers { accounts.remove(firstArg<String>()) }
    }

    private val store = LighterCredentialsStore(dao, sessionKeyStore, prefs)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun anActivationKeepsTheKeySealedAndTheAccountInPrefs() = runTest {
        store.saveActivation(WALLET_ID, KEY, ACCOUNT)

        assertArrayEquals(KEY, rows[L2_KEY_ID])
        assertEquals(ACCOUNT, accounts[WALLET_ID])
        assertFalse(LEGACY_ACCOUNT_ID in rows)
        assertEquals(ACCOUNT, store.loadAccount(WALLET_ID))
    }

    @Test
    fun aSealedAccountMovesToPrefsOnItsFirstRead() = runTest {
        rows[LEGACY_ACCOUNT_ID] = legacyPayload(ACCOUNT)

        assertEquals(ACCOUNT, store.loadAccount(WALLET_ID))

        assertEquals(ACCOUNT, accounts[WALLET_ID])
        assertFalse(LEGACY_ACCOUNT_ID in rows)
    }

    @Test
    fun aMalformedSealedAccountIsDropped() = runTest {
        rows[LEGACY_ACCOUNT_ID] = byteArrayOf(9, 9, 9)

        assertNull(store.loadAccount(WALLET_ID))

        assertTrue(accounts.isEmpty())
        assertFalse(LEGACY_ACCOUNT_ID in rows)
    }

    @Test
    fun noAccountIsReadWithoutWaitingForTheSessionKey() = runTest {
        coEvery { sessionKeyStore.awaitUnlocked(any()) } returns false
        accounts[WALLET_ID] = ACCOUNT

        assertEquals(ACCOUNT, store.loadAccount(WALLET_ID))
        assertNull(store.loadAccount(OTHER_WALLET_ID))
    }

    @Test
    fun aKeptKeyNeverReplacesTheRegisteredOne() = runTest {
        store.saveActivation(WALLET_ID, KEY, ACCOUNT)

        store.keepL2Key(WALLET_ID, byteArrayOf(7, 7, 7))

        assertArrayEquals(KEY, store.loadL2PrivateKey(WALLET_ID))
    }

    @Test
    fun aKeptKeyIsTheOneAnActivationFinds() = runTest {
        assertFalse(store.hasL2Key(WALLET_ID))

        store.keepL2Key(WALLET_ID, KEY)

        assertTrue(store.hasL2Key(WALLET_ID))
        assertArrayEquals(KEY, store.loadL2PrivateKey(WALLET_ID))
        assertNull(store.loadAccount(WALLET_ID))
    }

    @Test
    fun anUnreadableKeyIsDropped() = runTest {
        rows[L2_KEY_ID] = STALE
        every { sessionKeyStore.decrypt(match { it.contentEquals(STALE) }) } throws IllegalStateException("stale")

        assertNull(store.loadL2PrivateKey(WALLET_ID))

        assertFalse(L2_KEY_ID in rows)
    }

    @Test
    fun aKeyWrittenWhileAnUnreadableOneIsReadStays() = runTest {
        rows[L2_KEY_ID] = STALE
        every { sessionKeyStore.decrypt(match { it.contentEquals(STALE) }) } answers {
            // An activation lands between the read and the drop.
            rows[L2_KEY_ID] = KEY.copyOf()
            throw IllegalStateException("stale")
        }

        assertNull(store.loadL2PrivateKey(WALLET_ID))

        assertArrayEquals(KEY, rows[L2_KEY_ID])
    }

    private fun legacyPayload(account: StoredLighterAccount): ByteArray {
        return ByteBuffer.allocate(1 + Long.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(2)
            .putLong(account.accountIndex)
            .putInt(account.apiKeyIndex)
            .putLong(0L)
            .array()
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val OTHER_WALLET_ID = "other"
        val KEY = byteArrayOf(1, 2, 3, 4)
        val STALE = byteArrayOf(9, 8, 7)
        val ACCOUNT = StoredLighterAccount(accountIndex = 738_887L, apiKeyIndex = 254)
        val L2_KEY_ID = CredentialIds.lighterL2Key(WALLET_ID, CredentialIds.LIGHTER_MAINNET)
        val LEGACY_ACCOUNT_ID = CredentialIds.lighterAccount(WALLET_ID, CredentialIds.LIGHTER_MAINNET)
    }
}
