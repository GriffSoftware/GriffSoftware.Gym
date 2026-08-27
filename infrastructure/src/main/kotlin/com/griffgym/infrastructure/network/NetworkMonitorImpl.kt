package com.griffgym.infrastructure.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.griffgym.domain.repository.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device currently believes it has a usable connection.
 *
 * **Advisory only.** `NET_CAPABILITY_VALIDATED` is the strongest signal Android offers and it
 * still means "something answered", not "the Griff Gym API is reachable" — a captive portal, a
 * dead server and a DNS failure can all sit behind a network the platform calls healthy. So
 * nothing here decides whether to attempt a request; every call still handles its own failure.
 * This exists to explain to a lifter why the backup indicator is waiting, which is a question
 * they will otherwise ask of a spinner that cannot answer.
 *
 * Registered as a *default*-network callback rather than for a specific transport, because
 * which one the phone is on is none of this class's business and enumerating them is how a
 * monitor ends up not knowing about the next one.
 */
@Singleton
class NetworkMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun observeIsOnline(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            // No ConnectivityManager at all is not something to hang on. Reporting offline once
            // and closing lets the UI say something honest instead of waiting forever.
            trySend(false)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                trySend(manager.isOnline())
            }

            override fun onLost(network: Network) {
                trySend(manager.isOnline())
            }

            /**
             * The one that actually matters on a gym's Wi-Fi: a network can be *available*
             * for several seconds before it validates, and treating that window as online
             * produces a sync attempt that fails for no visible reason.
             */
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(manager.isOnline())
            }
        }

        // The current state first: a collector that subscribes while already online would
        // otherwise see nothing until the connection next changed.
        trySend(manager.isOnline())

        manager.registerDefaultNetworkCallback(callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    override suspend fun isOnline(): Boolean = connectivityManager?.isOnline() == true

    /**
     * Both capabilities, not just `INTERNET`. `INTERNET` says the network intends to provide
     * it; `VALIDATED` says Android checked and something answered. A hotel portal has the
     * first and not the second.
     */
    private fun ConnectivityManager.isOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
