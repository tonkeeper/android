package com.tonapps.wallet.data.passcode

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.extensions.logError
import com.tonapps.log.L
import com.tonapps.security.DeviceCredentialRequiredException
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.security.multichain.McPasscodeStore
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation

// TODO rename to PasscodeRepository
class PasscodeManager(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val helper: PasscodeHelper,
    private val rnLegacy: RNLegacy,
    private val multichainVault: McPasscodeStore,
    private val mcAccountRepository: McAccountRepository,
    private val perpetualRepository: PerpetualRepository,
    private val scope: CoroutineScope
) {

    private val vaultMutex = Mutex()

    @Volatile
    private var walletBindingsJob: Job? = null

    @Volatile
    private var walletBindingsSynced = false

    private val lockscreen = LockScreen(this, settingsRepository)

    val lockscreenFlow: Flow<LockScreen.State>
        get() = lockscreen.stateFlow

    val lockscreenHiddenFlow: StateFlow<Boolean> // TODO to state e.g. None, Shown, Passed
        get() = lockscreen.hiddenFlow

    val isLockScreenOptional: Boolean
        get() = multichainVault.canRestoreSession

    // Drives the new networks screen shown after the lock screen. Null when there is nothing to show.
    private val _newChainsFlow = MutableStateFlow<NewChainsSync?>(null)
    val newChainsFlow: StateFlow<NewChainsSync?> = _newChainsFlow.asStateFlow()

    internal val hasNewChains: Boolean
        get() = _newChainsFlow.value != null

    // Adding the chains the wallets miss is part of the lock screen: it needs the PIN, which only the
    // lock screen has. The PIN stays here while the new networks screen is up, for "Try again", and
    // is zeroed when the screen goes. Each sync works on its own copy, so closing the screen mid-sync
    // doesn't pull the PIN from under it.
    @Volatile
    private var newChainsPin: CharArray? = null

    // The chains the wallets miss, read once per lock screen and before the first unlock attempt: a
    // valid PIN starts the binding sync, which may add them before the new networks screen gets to
    // show them. Nothing changes them while the lock screen is up.
    private val lockMissingChains = scope.async(start = CoroutineStart.LAZY) { missingChainsOrEmpty() }

    init {
        settingsRepository.isMigratedFlow.onEach {
            lockscreen.init()
            lockMissingChains.start()
            ensureMultichainVault()
        }.launchIn(scope)
    }

    // Best-effort and detached from any UI lifecycle: a failure must never block the user, it is
    // retried on the next session start.
    @Synchronized
    private fun syncWalletBindings(code: String?) {
        walletBindingsJob?.let { job ->
            if (job.isActive || walletBindingsSynced) {
                return
            }
        }

        walletBindingsJob = scope.launch(Dispatchers.IO) {
            try {
                walletBindingsSynced = syncWallets(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    private suspend fun missingChainsOrEmpty(): List<Chain> {
        return try {
            mcAccountRepository.missingChains()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            emptyList()
        }
    }

    // [missing] is read before the unlock: the unlock itself starts a binding sync that adds them.
    private fun startNewChains(missing: List<Chain>, code: String): Boolean {
        if (missing.isEmpty()) {
            return false
        }
        val pin = code.toCharArray()
        newChainsPin?.fill('\u0000')
        newChainsPin = pin
        _newChainsFlow.value = NewChainsSync.Running(missing)
        scope.launch { addNewChains(missing, pin.copyOf()) }
        return true
    }

    // Takes ownership of [pin] and zeroes it.
    private suspend fun addNewChains(missing: List<Chain>, pin: CharArray) {
        withContext(Dispatchers.IO) {
            try {
                syncWallets(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // syncWallets throws after registering what it could; the diff below tells what landed.
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                pin.fill('\u0000')
            }
        }
        val remaining = missingChainsOrEmpty().toSet()
        // The screen may be closed while the sync runs (the close button): a late result must not
        // bring it back.
        _newChainsFlow.update { current ->
            current?.let { NewChainsSync.Finished(missing, missing.filter { it !in remaining }) }
        }
    }

    // No PIN: no lock screen, so the chains are checked right at start, with the vault's default key.
    internal suspend fun startNewChainsWithoutPin(): Boolean {
        val missing = missingChainsOrEmpty()
        if (missing.isEmpty()) {
            return false
        }
        ensureMultichainVault()
        return startNewChains(missing, DEFAULT_PIN)
    }

    /** "Try again" on the new networks screen. */
    fun retryNewChains() {
        val pin = newChainsPin?.copyOf() ?: return
        val missing = _newChainsFlow.value?.chains ?: run {
            pin.fill('\u0000')
            return
        }
        _newChainsFlow.value = NewChainsSync.Running(missing)
        scope.launch { addNewChains(missing, pin) }
    }

    /** "Open wallet": closes the new networks screen and with it the lock screen. */
    fun newChainsShown() {
        newChainsPin?.fill('\u0000')
        newChainsPin = null
        _newChainsFlow.value = null
        lockscreen.newChainsDone()
    }

    private suspend fun syncWallets(code: String?): Boolean {
        if (code == null) {
            val hasMissingChains = mcAccountRepository.hasMissingChains()
            mcAccountRepository.syncWallets(mnemonicCoder = null, firstWalletId = accountRepository.getSelectedWalletId())
            return !hasMissingChains
        }

        val pin = code.toCharArray()
        try {
            syncWallets(pin)
        } finally {
            pin.fill('\u0000')
        }
        return true
    }

    // The vault is unlocked only when a wallet misses chains or its Lighter key: deriving either
    // needs its mnemonic. The caller keeps ownership of [pin].
    private suspend fun syncWallets(pin: CharArray) {
        val firstWalletId = accountRepository.getSelectedWalletId()
        val withoutTradingKey = perpetualRepository.walletsWithoutTradingKey(mcAccountRepository.getWallets().map { it.id })
        if (!mcAccountRepository.hasMissingChains() && withoutTradingKey.isEmpty()) {
            mcAccountRepository.syncWallets(mnemonicCoder = null, firstWalletId = firstWalletId)
            return
        }

        vaultMutex.withLock { multichainVault.unlock(pin) }
            .use { coder ->
                keepTradingKeys(withoutTradingKey, coder)
                mcAccountRepository.syncWallets(coder, firstWalletId)
            }
    }

    // A key that fails to keep is tried again on the next entry: the unlock itself goes on, since
    // what fails here is perps, not the wallets the lock screen opens.
    private suspend fun keepTradingKeys(walletIds: List<String>, coder: MnemonicCoder) {
        for (walletId in walletIds) {
            try {
                val wallet = mcAccountRepository.getCryptoWallet(walletId, coder) ?: continue
                perpetualRepository.saveTradingKey(walletId, wallet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
            }
        }
    }


    // The vault must exist from app start. No PIN anywhere → seal it under DEFAULT_PIN and start
    // the session; with a PIN it is created on the first successful entry instead
    // (startMultichainSession), since sealing needs the PIN itself.
    private suspend fun ensureMultichainVault() = withContext(Dispatchers.IO) {
        try {
            vaultMutex.withLock {
                if (hasPinCode()) {
                    // Nothing to seal without the PIN, but a device-unlock-bound session from an
                    // earlier run can be attached right away instead of waiting for the lockscreen.
                    multichainVault.restoreSession()
                    return@withContext
                }

                if (multichainVault.hasVault()) {
                    multichainVault.unlockSession(DEFAULT_PIN.toCharArray())
                } else {
                    multichainVault.setPin(DEFAULT_PIN.toCharArray())
                        .use { }
                }
            }
        } catch (e: Throwable) {
            AnalyticsHelper.Default.captureException(
                AnalyticException.Vault("vault bootstrap failed", e)
            )
        }
    }

    fun lockscreenBiometric() {
        scope.launch {
            try {
                val code = helper.getPinCode()
                    ?: throw IllegalStateException("biometric unlock without a stored pin")

                val missingChains = lockMissingChains.await()
                startMultichainSession(code)
                startNewChains(missingChains, code)
                lockscreen.biometric()
            } catch (e: Throwable) {
                AnalyticsHelper.Default.captureException(
                    AnalyticException.Passcode("biometric unlock failed", e)
                )
            }
        }
    }

    fun lockscreenHidden() {
        lockscreen.hidden()
    }

    suspend fun lockscreenRetryWithoutPasscode(): Boolean {
        return lockscreen.retryWithoutPasscode()
    }

    internal suspend fun restoreSessionWithoutPasscode(): Boolean = withContext(Dispatchers.IO) {
        if (!settingsRepository.lockScreenDisabled) {
            return@withContext false
        }

        // A Lighter key is derived from the mnemonic, which only the PIN opens: a wallet with none
        // kept yet takes this one entry, and the unlock keeps it.
        if (owesTradingKey()) {
            return@withContext false
        }

        // The chains a wallet misses are derived from its mnemonic too: the PIN entry adds them and
        // shows the new networks screen, as it does with the lock screen on.
        if (lockMissingChains.await().isNotEmpty()) {
            return@withContext false
        }

        val restored = try {
            vaultMutex.withLock {
                multichainVault.restoreSession()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AnalyticsHelper.Default.captureException(
                AnalyticException.Vault("vault session restore failed", e)
            )
            false
        }

        if (restored) {
            syncWalletBindings(code = null)
        }

        restored
    }

    // Runs under LockScreen.init, where a throw leaves the lock screen without a state: a read that
    // fails counts as owing, and the PIN entry is the way through.
    private suspend fun owesTradingKey(): Boolean {
        return try {
            val walletIds = mcAccountRepository.getWallets().map { it.id }
            perpetualRepository.walletsWithoutTradingKey(walletIds).isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            true
        }
    }

    fun lockscreenCheck(context: Context, code: String) {
        scope.launch {
            val missingChains = lockMissingChains.await()
            lockscreen.check(context, code, onValid = { startNewChains(missingChains, code) })
        }
    }

    suspend fun hasPinCode(): Boolean = withContext(Dispatchers.IO) {
        try {
            hasPinCodeStrict()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    private suspend fun hasPinCodeStrict(): Boolean {
        return helper.hasPinCode || rnLegacy.hasPinCode()
    }

    private suspend fun isRequestMigration(): Boolean = withContext(Dispatchers.IO) {
        !helper.hasPinCode && rnLegacy.hasPinCode()
    }

    suspend fun requestValidPasscode(context: Context): String = withContext(Dispatchers.Main) {
        if (!unlockSecureStorage(context, withVault = false)) {
            throw CancellationException("device credential request cancelled")
        }

        // Dismissing the dialog is a user cancellation, not a failure: callers must be able to tell
        // it apart from a wrong passcode to avoid showing an error.
        val code = requestPasscodeByBiometric(context)
            ?: PasscodeDialog.request(context)
            ?: throw CancellationException("passcode request cancelled")

        if (!isValid(context, code)) {
            throw Exception("invalid passcode")
        }

        code
    }

    private suspend fun requestPasscodeByBiometric(context: Context): String? {
        if (!settingsRepository.biometric) {
            return null
        }

        if (isRequestMigration()) {
            return legacyGetPasscodeByBiometry()?.takeIf { it.isNotBlank() }
        }

        if (!PasscodeBiometric.isAvailableOnDevice(context)) {
            return null
        }

        return try {
            val authenticated = PasscodeBiometric.showPrompt(
                context = context,
                title = context.getString(Localization.app_name),
                passcodeFallback = true
            )

            if (authenticated) {
                helper.getPinCode()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    suspend fun <T> unlockMultichainVault(
        context: Context,
        block: suspend (MnemonicCoder) -> T
    ): T {
        val pin = requestValidPasscode(context).toCharArray()
        return try {
            multichainVault.unlock(pin).use { coder ->
                block(coder)
            }
        } finally {
            pin.fill('\u0000')
        }
    }

    /**
     * Vault access for flows that already hold the PIN (onboarding), so no dialog is shown.
     * The caller keeps ownership of [pin].
     */
    suspend fun <T> unlockOrCreateMultichainVault(
        pin: CharArray,
        block: suspend (MnemonicCoder) -> T
    ): T {
        val coder = vaultMutex.withLock {
            if (multichainVault.hasVault()) {
                multichainVault.unlock(pin)
            } else {
                multichainVault.setPin(pin)
            }
        }

        return coder.use { block(it) }
    }

    suspend fun isValid(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        if (!unlockSecureStorage(context, withVault = false)) {
            return@withContext false
        }

        val valid = if (!isRequestMigration()) {
            helper.isValid(code)
        } else {
            try {
                migration(context, code)
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                context.logError(e)
                false
            }
        }

        if (valid) {
            startMultichainSession(code)
        }

        valid
    }

    // Starts the app-lifetime session on a successful PIN entry. A user updating with a legacy
    // PIN has no vault yet — the first successful entry creates it.
    private suspend fun startMultichainSession(code: String) {
        if (multichainVault.isSessionUnlocked) {
            // A session restored without a passcode skips the vault work but still owes the binding
            // sync a PIN unlock would have kicked off; it dedupes itself.
            syncWalletBindings(code)
            return
        }

        val pin = code.toCharArray()
        try {
            vaultMutex.withLock {
                if (multichainVault.hasVault()) {
                    try {
                        multichainVault.unlockSession(pin)
                    } catch (e: Throwable) {
                        recoverMultichainVault(pin, e)
                    }
                } else {
                    multichainVault.setPin(pin)
                        .use { }
                }

                helper.setPendingPinChange(null)
            }

            syncWalletBindings(code)
        } catch (e: Throwable) {
            // The legacy PIN store accepted a code the vault can't open (stores desynced, e.g. by
            // a PIN change that predates the vault re-seal fix); the session simply stays locked.
            AnalyticsHelper.Default.captureException(
                AnalyticException.Vault("vault session unlock failed", e)
            )
        } finally {
            pin.fill('\u0000')
        }
    }

    // The legacy store accepted [pin] but the vault did not open with it — an interrupted save()
    // or change() left the vault under DEFAULT_PIN or the marked target PIN; re-seal it under the
    // accepted one. A genuinely foreign vault (restored backup) fails all candidates and rethrows.
    private suspend fun recoverMultichainVault(pin: CharArray, cause: Throwable) {
        try {
            multichainVault.changePin(DEFAULT_PIN.toCharArray(), pin)
            return
        } catch (e: Throwable) {
            cause.addSuppressed(e)
        }

        val pending = helper.getPendingPinChange()

        if (pending != null) {
            val pendingPin = pending.toCharArray()
            try {
                multichainVault.changePin(pendingPin, pin)
                return
            } catch (e: Throwable) {
                cause.addSuppressed(e)
            } finally {
                pendingPin.fill('\u0000')
            }
        }

        throw cause
    }

    suspend fun change(context: Context, old: String, new: String): Boolean = withContext(Dispatchers.IO) {
        if (isRequestMigration() && !migration(context, old)) {
            return@withContext false
        }

        vaultMutex.withLock {
            // Pre-save new PIN
            helper.setPendingPinChange(new)

            // Vault first: opening it is a cryptographic check of the old PIN, and a failure aborts
            // the change with nothing modified.
            if (!changeMultichainVaultPin(old, new)) {
                helper.setPendingPinChange(null)
                return@withContext false
            }

            if (!helper.change(old, new)) {
                // The legacy store rejected the change — roll the vault back to the old PIN. A failed
                // rollback keeps the marker, so the desync is healed on the next unlock.
                if (changeMultichainVaultPin(new, old)) {
                    helper.setPendingPinChange(null)
                }
                return@withContext false
            }

            helper.setPendingPinChange(null)
        }

        try {
            rnLegacy.changePasscode(old, new)
            if (settingsRepository.biometric) {
                rnLegacy.setupBiometry(new)
            }
        } catch (e: Throwable) {
            // The vault and the legacy store — the sources of truth — are already re-sealed, so
            // the change did happen; the RN copy is best-effort.
            FirebaseCrashlytics.getInstance().recordException(e)
            context.logError(e)
        }
        true
    }

    private suspend fun changeMultichainVaultPin(old: String, new: String): Boolean {
        val oldPin = old.toCharArray()
        val newPin = new.toCharArray()
        return try {
            if (multichainVault.hasVault()) {
                multichainVault.changePin(oldPin, newPin)
            }
            true
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        } finally {
            oldPin.fill('\u0000')
            newPin.fill('\u0000')
        }
    }

    suspend fun create(code: String): Boolean = withContext(Dispatchers.IO) {
        if (hasPinCodeStrict()) {
            return@withContext false
        }

        helper.save(code)
        if (sealVaultWithFirstPin(code)) {
            true
        } else {
            withContext(NonCancellable) {
                helper.reset()
            }
            false
        }
    }

    suspend fun save(code: String) {
        helper.save(code)
        sealVaultWithFirstPin(code)
    }

    // Create the vault under the first PIN or re-seal the DEFAULT_PIN one (changePin keeps the
    // master key). Callers that require an atomic first-PIN write can roll back when this is false.
    private suspend fun sealVaultWithFirstPin(code: String): Boolean = withContext(Dispatchers.IO) {
        val pin = code.toCharArray()
        try {
            vaultMutex.withLock {
                if (multichainVault.hasVault()) {
                    multichainVault.changePin(DEFAULT_PIN.toCharArray(), pin)
                } else {
                    multichainVault.setPin(pin).use { }
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AnalyticsHelper.Default.captureException(
                AnalyticException.Vault("vault first pin seal failed", e)
            )
            false
        } finally {
            pin.fill('\u0000')
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        settingsRepository.lockScreen = false
        settingsRepository.lockScreenDisabled = false
        settingsRepository.biometric = false
        helper.reset()
        rnLegacy.clearMnemonic()
        resetMultichainVault()
    }

    // Drop the vault with the PIN — a masterKey sealed under a deleted PIN would brick the next
    // onboarding — and recreate it under DEFAULT_PIN right away.
    private suspend fun resetMultichainVault() {
        try {
            vaultMutex.withLock {
                multichainVault.deleteAll()
                multichainVault.setPin(DEFAULT_PIN.toCharArray()).use { }
            }
        } catch (e: Throwable) {
            AnalyticsHelper.Default.captureException(
                AnalyticException.Vault("vault reset failed", e)
            )
        }
    }

    suspend fun isBiometricRequest(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!PasscodeBiometric.isAvailableOnDevice(context)) {
            false
        } else if (isRequestMigration()) {
            rnLegacy.getWallets().biometryEnabled
        } else {
            settingsRepository.biometric
        }
    }

    suspend fun confirmationByBiometric(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            if (isRequestMigration()) {
                val passcode = rnLegacy.exportPasscodeWithBiometry()
                if (passcode.isBlank()) {
                    throw Exception("failed to request passcode")
                }
                migration(context, passcode)
            } else {
                PasscodeBiometric.showPrompt(context, title, passcodeFallback = true)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    suspend fun confirmation(
        context: Context,
        title: String
    ): Boolean = withContext(Dispatchers.Main) {
        if (!unlockSecureStorage(context, withVault = true)) {
            return@withContext false
        }

        if (isRequestMigration()) {
            return@withContext confirmationMigration(context)
        }

        if (settingsRepository.biometric && PasscodeBiometric.isAvailableOnDevice(context) && PasscodeBiometric.showPrompt(context, title, passcodeFallback = true)) {
            true
        } else {
            val passcode = PasscodeDialog.request(context)
            if (passcode.isNullOrBlank()) {
                false
            } else {
                isValid(context, passcode)
            }
        }
    }

    suspend fun legacyGetPasscode(
        context: Context
    ): String? {
        return legacyGetPasscodeByBiometry() ?: PasscodeDialog.request(context)
    }

    private suspend fun legacyGetPasscodeByBiometry(): String? {
        try {
            return rnLegacy.exportPasscodeWithBiometry()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return null
        }
    }

    private suspend fun confirmationMigration(
        context: Context,
    ): Boolean = withContext(Dispatchers.Main) {
        val passcodeByBiometric: String? = try {
            if (settingsRepository.biometric) {
                rnLegacy.exportPasscodeWithBiometry()
            } else {
                null
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }

        val passcodeByDialog: String? = try {
            if (passcodeByBiometric.isNullOrEmpty()) {
                PasscodeDialog.request(context)
            } else {
                null
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
        try {
            val passcode = passcodeByBiometric ?: passcodeByDialog
            if (passcode.isNullOrBlank()) {
                throw Exception("failed to request passcode")
            }
            migration(context, passcode)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            context.logError(e)
            false
        }
    }

    private suspend fun unlockSecureStorage(context: Context, withVault: Boolean): Boolean {
        if (!requiresDeviceCredential(withVault)) {
            return true
        }
        if (!DeviceCredential.confirm(context, context.getString(Localization.app_name))) {
            return false
        }
        return !requiresDeviceCredential(withVault)
    }

    private suspend fun requiresDeviceCredential(withVault: Boolean): Boolean = withContext(Dispatchers.IO) {
        requiresDeviceCredential { helper.hasPinCode } ||
            (withVault && requiresDeviceCredential { accountRepository.openVault() })
    }

    private fun requiresDeviceCredential(open: () -> Unit): Boolean {
        return try {
            open()
            false
        } catch (_: DeviceCredentialRequiredException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun migration(
        context: Context,
        code: String
    ): Boolean = withContext(Dispatchers.Main) {
        val navigation = Navigation.from(context)
        navigation?.migrationLoader(true)
        if (accountRepository.importPrivateKeysFromRNLegacy(code)) {
            save(code)
            navigation?.migrationLoader(false)
            true
        } else {
            navigation?.migrationLoader(false)
            false
        }
    }

    private companion object {
        const val DEFAULT_PIN = "5125ae74e03bdfea094f06d571f6ce0db64a17c7eb113e4fade4fc1625a3f613"
    }
}
