package com.pvolkov.imsforpixel.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface AdbEndpoint {
    val port: Int
    data class Connect(override val port: Int) : AdbEndpoint
    data class Pairing(override val port: Int) : AdbEndpoint
}

class AdbDiscovery(context: Context) {

    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)

    fun discover(): Flow<AdbEndpoint> = callbackFlow {
        val connectListener = discoveryListener(MATCH_CONNECT) { port ->
            trySend(AdbEndpoint.Connect(port))
        }
        val pairingListener = discoveryListener(MATCH_PAIRING) { port ->
            trySend(AdbEndpoint.Pairing(port))
        }

        runCatching {
            nsdManager.discoverServices(SERVICE_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener)
        }.onFailure { Log.e(TAG, "Failed to start connect discovery", it) }
        runCatching {
            nsdManager.discoverServices(SERVICE_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener)
        }.onFailure { Log.e(TAG, "Failed to start pairing discovery", it) }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(connectListener) }
            runCatching { nsdManager.stopServiceDiscovery(pairingListener) }
        }
    }

    private fun discoveryListener(
        serviceTypeMatch: String,
        onPortResolved: (Int) -> Unit,
    ) = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(type: String?, errorCode: Int) {
            Log.e(TAG, "Start discovery failed for $type: $errorCode")
        }

        override fun onStopDiscoveryFailed(type: String?, errorCode: Int) {
            Log.e(TAG, "Stop discovery failed for $type: $errorCode")
        }

        override fun onDiscoveryStarted(type: String?) = Unit
        override fun onDiscoveryStopped(type: String?) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.contains(serviceTypeMatch)) return
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for $serviceTypeMatch: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    onPortResolved(info.port)
                }
            })
        }
    }

    companion object {
        private const val TAG = "AdbDiscovery"
        private const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
        private const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
        private const val MATCH_CONNECT = "adb-tls-connect"
        private const val MATCH_PAIRING = "adb-tls-pairing"
    }
}
