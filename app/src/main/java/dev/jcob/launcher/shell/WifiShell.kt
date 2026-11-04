package dev.jcob.launcher.shell

import kotlinx.coroutines.delay

data class ScanResult(val ssid: String, val bssid: String, val signalDbm: Int, val frequencyMhz: Int, val security: String) {
    val isSecured get() = security.contains("PSK") || security.contains("SAE") || security.contains("EAP")
    val band get() = if (frequencyMhz >= 5000) "5 GHz" else "2.4 GHz"
    /** 0..4 bars. */
    val bars get() = when {
        signalDbm >= -55 -> 4
        signalDbm >= -66 -> 3
        signalDbm >= -77 -> 2
        signalDbm >= -88 -> 1
        else -> 0
    }
}

data class SavedNetwork(val id: Int, val ssid: String, val security: String)

/**
 * Wi-Fi management through `cmd wifi`. Note: this firmware's shell can't *connect* to a network
 * (connect-network is root-only), so connecting is handed off to the system Wi-Fi screen.
 */
class WifiShell(private val shell: ShellClient) {
    suspend fun setEnabled(enabled: Boolean): Boolean =
        shell.run("cmd wifi set-wifi-enabled ${if (enabled) "enabled" else "disabled"}").getOrNull()?.exitCode == 0

    suspend fun scan(): List<ScanResult> {
        shell.run("cmd wifi start-scan")
        delay(4_000)
        val raw = shell.output("cmd wifi list-scan-results") ?: return emptyList()
        return parseScanResults(raw)
            .filter { it.ssid.isNotBlank() }
            // One row per network name, keeping the strongest access point.
            .groupBy { it.ssid }
            .map { (_, aps) -> aps.maxBy { it.signalDbm } }
            .sortedByDescending { it.signalDbm }
    }

    suspend fun savedNetworks(): List<SavedNetwork> {
        val raw = shell.output("cmd wifi list-networks") ?: return emptyList()
        // Format: "Network Id      SSID                         Security type"
        return raw.lines().drop(1).mapNotNull { line ->
            val m = Regex("""^\s*(\d+)\s+(.+?)\s{2,}(\S.*)$""").find(line) ?: return@mapNotNull null
            SavedNetwork(m.groupValues[1].toInt(), m.groupValues[2].trim(), m.groupValues[3].trim())
        }
    }

    /** Android hides the SSID from apps when location is off; the shell can still see it. */
    suspend fun connectedSsid(): String? =
        shell.output("cmd wifi status")?.let { Regex("""Wifi is connected to "(.*?)"""").find(it)?.groupValues?.get(1) }

    suspend fun forget(id: Int): Boolean = shell.run("cmd wifi forget-network $id").getOrNull()?.exitCode == 0

    private fun parseScanResults(raw: String): List<ScanResult> {
        // Header: "    BSSID              Frequency      RSSI           Age(sec)     SSID                    Flags"
        return raw.lines().mapNotNull { line ->
            val m = Regex("""^\s*([0-9a-f:]{17})\s+(\d+)\s+(-?\d+)(?:\(.*?\))?\s+\S+\s+(.*?)\s+(\[.*)$""").find(line)
                ?: return@mapNotNull null
            ScanResult(
                bssid = m.groupValues[1],
                frequencyMhz = m.groupValues[2].toInt(),
                signalDbm = m.groupValues[3].toInt(),
                ssid = m.groupValues[4].trim(),
                security = m.groupValues[5],
            )
        }
    }
}
