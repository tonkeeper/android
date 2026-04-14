package com.tonapps.tonkeeper.ui.screen.root

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.component.SnackBarView
import com.tonapps.tonkeeper.ui.base.BaseWalletActivity
import com.tonapps.wallet.localization.Localization

class GooglePlayUpdateHelper(
    private val activity: RootActivity,
    private val viewModel: RootViewModel,
    private val environment: Environment,
) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val appUpdateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            notifyUpdateReadyIfAllowed()
        }
    }

    fun onStart() {
        if (!isAvailable()) {
            return
        }

        appUpdateManager.registerListener(appUpdateListener)
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    notifyUpdateReadyIfAllowed()
                }
            }
            .addOnFailureListener(::onUpdateFailed)
    }

    fun onStop() {
        appUpdateManager.unregisterListener(appUpdateListener)
    }

    fun checkForUpdates() {
        if (!isAvailable() || !viewModel.canShowGooglePlayUpdatePrompt()) {
            return
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    notifyUpdateReadyIfAllowed()
                    return@addOnSuccessListener
                }

                if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    startUpdateFlow(updateInfo)
                }
            }
            .addOnFailureListener(::onUpdateFailed)
    }

    private fun showSnackbar(): Boolean {
        if (activity.isFinishing) {
            return false
        }

        if (BaseWalletActivity.findBaseView(activity) == null) {
            return false
        }

        SnackBarView.show(
            context = activity,
            text = activity.getString(Localization.app_update_downloaded),
            buttonText = activity.getString(Localization.restart),
            durationMs = UPDATE_SNACKBAR_DURATION_MS,
            onClickListener = {
                completeUpdate()
            }
        )
        return true
    }

    private fun completeUpdate() {
        appUpdateManager.completeUpdate()
            .addOnFailureListener(::onUpdateFailed)
    }

    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) {
        try {
            val started = appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                0
            )
            if (started) {
                viewModel.onGooglePlayUpdatePromptShown()
            }
        } catch (e: Throwable) {
            onUpdateFailed(e)
        }
    }

    private fun notifyUpdateReadyIfAllowed() {
        if (!viewModel.canShowGooglePlayDownloadedPrompt()) {
            return
        }

        if (showSnackbar()) {
            viewModel.onGooglePlayDownloadedPromptShown()
        }
    }

    private fun isAvailable(): Boolean {
        return environment.isFromGooglePlay && environment.isGooglePlayServicesAvailable
    }

    private fun onUpdateFailed(error: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(error)
    }

    private companion object {
        private const val UPDATE_SNACKBAR_DURATION_MS = 10_000L
    }
}
