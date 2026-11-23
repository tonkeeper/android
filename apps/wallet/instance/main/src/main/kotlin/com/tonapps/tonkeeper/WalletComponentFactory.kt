package com.tonapps.tonkeeper

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.CoreComponentFactory

@RequiresApi(Build.VERSION_CODES.P)
class WalletComponentFactory : CoreComponentFactory() {

    override fun instantiateActivity(cl: ClassLoader, className: String, intent: Intent?): Activity {
        if (NativeLibraries.isUnavailable) {
            return ReinstallActivity()
        }
        return super.instantiateActivity(cl, className, intent)
    }

    override fun instantiateService(cl: ClassLoader, className: String, intent: Intent?): Service {
        if (NativeLibraries.isUnavailable) {
            return NoOpService()
        }
        return super.instantiateService(cl, className, intent)
    }

    override fun instantiateReceiver(cl: ClassLoader, className: String, intent: Intent?): BroadcastReceiver {
        if (NativeLibraries.isUnavailable) {
            return NoOpReceiver()
        }
        return super.instantiateReceiver(cl, className, intent)
    }

    private class NoOpService : Service() {

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private class NoOpReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) = Unit
    }
}
