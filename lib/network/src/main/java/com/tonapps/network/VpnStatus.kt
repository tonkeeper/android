package com.tonapps.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tonapps.log.L

@SuppressLint("MissingPermission")
fun Context.isVpnActive(): Boolean {
    return try {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (e: SecurityException) {
        L.w(e, "isVpnActive: network capabilities are unavailable")
        false
    }
}
