package dev.jcob.launcher.shell

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import android.util.Log
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.coroutines.resume

private const val TAG = "ShellClient"

/**
 * A privileged shell obtained by connecting to this device's own adbd over Wireless debugging.
 *
 * The app holds its own adb identity (persisted in filesDir). It must be paired once, which
 * scripts/pair-launcher.ps1 does by sending the pairing code from the PC via a broadcast.
 */
class ShellClient(private val context: Context) {
    sealed interface State {
        data object Unpaired : State
        data object Disconnected : State
        data object Connecting : State
        data class Connected(val port: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("shell", Context.MODE_PRIVATE)
    private var kadb: Kadb? = null

    val isPaired: Boolean get() = prefs.getBoolean("paired", false)

    private val _state: MutableStateFlow<State>
    val state: StateFlow<State>

    init {
        loadIdentity()
        _state = MutableStateFlow(if (isPaired) State.Disconnected else State.Unpaired)
        state = _state
        reconnectWhenWifiReturns()
    }

    /**
     * Android turns Wireless debugging off whenever Wi-Fi drops (including at boot, before Wi-Fi
     * has connected), so re-enable it and reconnect each time Wi-Fi comes back.
     */
    private fun reconnectWhenWifiReturns() {
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        context.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!isPaired || kadb != null) return
                    scope.launch {
                        delay(3_000)
                        mutex.withLock {
                            attempt { connection() }
                                .onSuccess { Log.i(TAG, "reconnected after Wi-Fi came up") }
                                .onFailure { Log.w(TAG, "reconnect after Wi-Fi came up failed", it) }
                        }
                    }
                }
            },
        )
    }

    /**
     * Runs [command] via the device shell. Connects (and reconnects once) as needed.
     *
     * The work runs in this client's own scope: if the caller (say, a dialog) goes away mid-command
     * the command still finishes and, more importantly, the shared connection isn't torn down.
     */
    suspend fun run(command: String): Result<AdbShellResponse> = scope.async {
        mutex.withLock {
            attempt { connection().shell(command) }.recoverCatching {
                Log.w(TAG, "shell failed, reconnecting", it)
                dropConnection()
                connection().shell(command)
            }.onFailure {
                Log.e(TAG, "shell failed: $command", it)
                dropConnection()
                _state.value = State.Failed(it.message ?: it.javaClass.simpleName)
            }
        }
    }.await()

    /** Streams [apk] to the package manager over adb, like `adb install -r`. */
    suspend fun install(apk: File): Result<Unit> = scope.async {
        mutex.withLock {
            attempt { connection().install(apk, "-r") }
                .onFailure { Log.e(TAG, "install failed", it); dropConnection() }
        }
    }.await()

    /** stdout of [command], or null if it failed or exited non-zero. */
    suspend fun output(command: String): String? =
        run(command).getOrNull()?.takeIf { it.exitCode == 0 }?.output

    fun connectInBackground() {
        if (!isPaired) return
        scope.launch {
            mutex.withLock {
                attempt { connection() }.onFailure { Log.w(TAG, "background connect failed", it) }
            }
        }
    }

    /** Like runCatching, but lets cancellation propagate instead of reporting it as a failure. */
    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    suspend fun pair(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Kadb.pair("127.0.0.1", port, code)
            prefs.edit().putBoolean("paired", true).apply()
            _state.value = State.Disconnected
        }.onFailure { Log.e(TAG, "pairing failed", it) }
    }

    private suspend fun connection(): Kadb {
        kadb?.let { return it }
        _state.value = State.Connecting
        ensureWirelessDebuggingOn()
        var port = findPort()
        if (port == null) {
            // The setting can read 1 while adbd isn't actually listening (Android refuses to start
            // it before Wi-Fi is up but doesn't always reset the flag), so toggle it and retry.
            ensureWirelessDebuggingOn(forceRestart = true)
            port = findPort()
        }
        if (port == null) error("Wireless debugging port not found. Is Wireless debugging on?")
        Log.i(TAG, "connecting to adbd on port $port")
        // A socket timeout stops a stalled TLS handshake from holding the mutex forever.
        val created = Kadb.create("127.0.0.1", port, connectTimeout = 5_000, socketTimeout = 15_000)
        // Fails fast with an auth/TLS error if we're not trusted.
        check(created.shell("echo ok").output.trim() == "ok") { "Shell handshake failed" }
        kadb = created
        prefs.edit().putInt("last_port", port).putBoolean("paired", true).apply()
        _state.value = State.Connected(port)
        return created
    }

    private fun dropConnection() {
        runCatching { kadb?.close() }
        kadb = null
        _state.value = if (isPaired) State.Disconnected else State.Unpaired
    }

    /** Re-enables Wireless debugging after reboots (needs WRITE_SECURE_SETTINGS, granted at deploy). */
    private fun ensureWirelessDebuggingOn(forceRestart: Boolean = false) {
        runCatching {
            val resolver = context.contentResolver
            // Android forgets adb keys unused for 7 days by default; our key must never expire.
            if (Settings.Global.getLong(resolver, "adb_allowed_connection_time", -1) != 0L) {
                Settings.Global.putLong(resolver, "adb_allowed_connection_time", 0)
            }
            if (forceRestart) {
                Log.i(TAG, "restarting Wireless debugging")
                Settings.Global.putInt(resolver, "adb_wifi_enabled", 0)
                Thread.sleep(1_000)
            }
            if (forceRestart || Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) != 1) {
                Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)
                Thread.sleep(2_500)
            }
        }.onFailure { Log.w(TAG, "can't toggle adb_wifi_enabled", it) }
    }

    /** Wireless debugging picks a random port on every start; try the cheap sources first. */
    private suspend fun findPort(): Int? {
        val candidates = listOfNotNull(systemProperty("service.adb.tls.port"), prefs.getInt("last_port", 0))
        candidates.filter { it > 0 }.firstOrNull(::isOpen)?.let { return it }
        return discoverViaMdns()?.takeIf(::isOpen)
    }

    private fun isOpen(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
    }.isSuccess

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): Int? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as String
    }.getOrNull()?.toIntOrNull()

    private suspend fun discoverViaMdns(): Int? = withTimeoutOrNull(6_000) {
        val nsd = context.getSystemService(NsdManager::class.java)
        val ownAddresses = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }
            .toSet()
        suspendCancellableCoroutine { cont ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = info.host?.hostAddress
                            if (host in ownAddresses && cont.isActive) cont.resume(info.port)
                        }
                    })
                }
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onStartDiscoveryFailed(type: String, code: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(type: String, code: Int) {}
            }
            nsd.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
        }
    }

    private fun loadIdentity() {
        val certFile = File(context.filesDir, "adb_cert")
        val keyFile = File(context.filesDir, "adb_key")
        if (certFile.exists() && keyFile.exists()) {
            // KadbCert.set() stores the pair *then* runs checkValidity(). Right after boot the clock
            // can be behind (no NTP yet) so that throws "not yet valid", but adbd only compares the
            // key, never the dates. Never regenerate here: a new identity silently loses the pairing.
            runCatching { KadbCert.set(certFile.readBytes(), keyFile.readBytes()) }
                .onFailure { Log.w(TAG, "certificate date check failed (clock behind?); using it anyway", it) }
            Log.i(TAG, "identity loaded, paired=$isPaired")
            return
        }
        val (cert, key) = KadbCert.get(
            cn = "TV Launcher", ou = "Launcher", o = "Launcher", l = "Home", st = "Home", c = "US",
            notAfter = System.currentTimeMillis() + 30L * 365 * 24 * 3600 * 1000,
        )
        certFile.writeBytes(cert)
        keyFile.writeBytes(key)
        prefs.edit().putBoolean("paired", false).apply()
        Log.i(TAG, "generated a new adb identity; pairing required")
    }
}
