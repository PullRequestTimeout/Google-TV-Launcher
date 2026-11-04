package dev.jcob.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address

data class WifiStatus(
    val enabled: Boolean,
    val connected: Boolean,
    val ssid: String?,
    /** 0..4 */
    val bars: Int,
    val ip: String?,
    val linkMbps: Int?,
    val ethernet: Boolean,
)

/** Snapshot of the current connection. SSID needs ACCESS_FINE_LOCATION (granted at deploy). */
fun readWifiStatus(context: Context): WifiStatus {
    val wifi = context.getSystemService(WifiManager::class.java)
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val network = cm.activeNetwork
    val caps = network?.let(cm::getNetworkCapabilities)
    val link: LinkProperties? = network?.let(cm::getLinkProperties)
    val info = caps?.transportInfo as? WifiInfo
    @Suppress("DEPRECATION")
    val legacy = wifi.connectionInfo
    val ssid = (info?.ssid ?: legacy?.ssid)?.trim('"')?.takeUnless { it == WifiManager.UNKNOWN_SSID.trim('<', '>') || it.startsWith("<") }
    val rssi = info?.rssi ?: legacy?.rssi ?: -127
    return WifiStatus(
        enabled = wifi.isWifiEnabled,
        connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        ssid = ssid,
        bars = wifi.calculateSignalLevel(rssi).coerceIn(0, 4),
        ip = link?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress,
        linkMbps = (info ?: legacy)?.linkSpeed?.takeIf { it > 0 },
        ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
    )
}
