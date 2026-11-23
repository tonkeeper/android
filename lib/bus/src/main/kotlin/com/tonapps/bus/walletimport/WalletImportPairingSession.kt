package com.tonapps.bus.walletimport

import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerTransport
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingAction
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingEntry
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingExitReason
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingLastStep
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource

class WalletImportPairingSession(
    val walletMode: WalletFlowWalletMode,
    val walletSource: WalletFlowWalletSource,
    val from: WalletFlowFrom,
    val entry: WalletFlowPairingEntry,
    private val hasPairingScreen: Boolean,
    private val walletFlow: () -> Events.WalletFlow,
) {

    enum class Blocker(val reason: WalletFlowPairingExitReason) {
        BluetoothOff(WalletFlowPairingExitReason.BluetoothOff),
        BluetoothPermissionDenied(WalletFlowPairingExitReason.BluetoothPermissionDenied),
        CameraPermissionDenied(WalletFlowPairingExitReason.CameraPermissionDenied),
        ConnectionError(WalletFlowPairingExitReason.Error),
        ImportError(WalletFlowPairingExitReason.Error),
    }

    private val lock = Any()
    private var isStarted = false
    private var isFinished = false
    private var isSucceeded = false
    private var hasAction = false
    private var isAwaitingSigner = false
    private val foundDeviceIds = HashSet<String>()
    private var lastStep = WalletFlowPairingLastStep.None
    private val blockers = ArrayList<Blocker>()

    val isActive: Boolean
        get() = synchronized(lock) { isStarted && !isFinished }

    val hasSucceeded: Boolean
        get() = synchronized(lock) { isSucceeded }

    val isAwaitingSignerReturn: Boolean
        get() = synchronized(lock) { isStarted && !isFinished && isAwaitingSigner }

    private val WalletFlowPairingLastStep.rank: Int
        get() = when (this) {
            WalletFlowPairingLastStep.None -> 0
            WalletFlowPairingLastStep.Action -> 1
            WalletFlowPairingLastStep.DeviceFound -> 2
            WalletFlowPairingLastStep.Connected -> 3
            WalletFlowPairingLastStep.AppOpened -> 4
            WalletFlowPairingLastStep.AccountsView -> 5
        }

    fun start() {
        synchronized(lock) {
            if (isStarted || isFinished) {
                return
            }
            isStarted = true
            walletFlow().walletImportStarted(
                walletMode = walletMode,
                walletSource = walletSource,
                from = from,
                pairingEntry = entry,
            )
        }
    }

    fun action(action: WalletFlowPairingAction) {
        synchronized(lock) {
            if (!isActiveLocked() || !hasPairingScreen || hasAction) {
                return
            }
            hasAction = true
            advanceLocked(WalletFlowPairingLastStep.Action)
            walletFlow().walletImportPairingAction(
                walletMode = walletMode,
                walletSource = walletSource,
                from = from,
                pairingAction = action,
            )
        }
    }

    fun signerAppOpened() {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            isAwaitingSigner = true
            action(WalletFlowPairingAction.SignerAppOpened)
        }
    }

    fun deviceFound(
        deviceId: String,
        transport: WalletFlowLedgerTransport,
        ledgerModel: WalletFlowLedgerModel?,
    ) {
        synchronized(lock) {
            if (!isActiveLocked() || !hasPairingScreen || !foundDeviceIds.add(deviceId.lowercase())) {
                return
            }
            advanceLocked(WalletFlowPairingLastStep.DeviceFound)
            walletFlow().walletImportLedgerDeviceFound(
                walletMode = walletMode,
                walletSource = walletSource,
                from = from,
                ledgerTransport = transport,
                ledgerModel = ledgerModel,
            )
        }
    }

    fun connected() {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            isAwaitingSigner = false
            blockers.clear()
            advanceLocked(WalletFlowPairingLastStep.Connected)
        }
    }

    fun cameraStarted() {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            blockers.remove(Blocker.CameraPermissionDenied)
            action(WalletFlowPairingAction.CameraOpened)
        }
    }

    fun advance(step: WalletFlowPairingLastStep) {
        synchronized(lock) {
            if (isActiveLocked()) {
                advanceLocked(step)
            }
        }
    }

    fun setBlocked(blocker: Blocker, isBlocked: Boolean = true) {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            blockers.remove(blocker)
            if (isBlocked) {
                blockers.add(blocker)
            }
        }
    }

    fun success(ledgerModel: WalletFlowLedgerModel? = null) {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            isFinished = true
            isSucceeded = true
            walletFlow().walletImportSuccess(
                walletMode = walletMode,
                walletSource = walletSource,
                from = from,
                pairingEntry = entry,
                ledgerModel = ledgerModel,
            )
        }
    }

    fun exit(reason: WalletFlowPairingExitReason = WalletFlowPairingExitReason.Back) {
        synchronized(lock) {
            if (!isActiveLocked()) {
                return
            }
            isFinished = true
            if (!hasPairingScreen) {
                return
            }
            walletFlow().walletImportPairingExit(
                walletMode = walletMode,
                walletSource = walletSource,
                from = from,
                pairingLastStep = lastStep,
                pairingExitReason = blockers.lastOrNull()?.reason ?: reason,
            )
        }
    }

    private fun isActiveLocked() = isStarted && !isFinished

    private fun advanceLocked(step: WalletFlowPairingLastStep) {
        if (step.rank > lastStep.rank) {
            lastStep = step
        }
    }
}
