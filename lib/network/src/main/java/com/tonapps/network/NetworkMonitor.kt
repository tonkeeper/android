package com.tonapps.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.Flow
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@SuppressLint("MissingPermission")
class NetworkMonitor(
    context: Context,
    scope: CoroutineScope
):  ConnectivityManager.NetworkCallback() {

    private val networks = mutableSetOf<Network>()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager

    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: Flow<Boolean> = _isOnlineFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, this)
        val isOnline = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        _isOnlineFlow.value = isOnline == true
    }

    override fun onAvailable(network: Network) {
        networks += network
        _isOnlineFlow.value = true
    }

    override fun onLost(network: Network) {
        networks -= network
        _isOnlineFlow.value = networks.isNotEmpty()
    }
}