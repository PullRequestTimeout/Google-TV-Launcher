package dev.jcob.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore("launcher")
private val ConfigKey = stringPreferencesKey("config")

class ConfigStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val config: StateFlow<LauncherConfig> = context.dataStore.data
        .map { prefs -> prefs[ConfigKey]?.let(::decode) ?: LauncherConfig() }
        // Read synchronously once so the first frame already has the user's layout.
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { current() })

    private suspend fun current(): LauncherConfig =
        context.dataStore.data.first()[ConfigKey]?.let(::decode) ?: LauncherConfig()

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val old = prefs[ConfigKey]?.let(::decode) ?: LauncherConfig()
                prefs[ConfigKey] = Json.encodeToString(LauncherConfig.serializer(), transform(old))
            }
        }
    }

    /** Writes the config to Downloads so it can be pulled with adb or copied to USB. */
    fun export(): File {
        val file = exportFile()
        file.parentFile?.mkdirs()
        file.writeText(PrettyJson.encodeToString(LauncherConfig.serializer(), config.value))
        return file
    }

    /** Returns false if the file is missing or not a valid config. */
    fun import(file: File = exportFile()): Boolean {
        val parsed = runCatching { file.readText() }.getOrNull()?.let(::decode) ?: return false
        update { parsed }
        return true
    }

    /** App-specific external dir: readable/writable by us and by `adb shell`, no permission needed. */
    fun exportFile(): File = File(context.getExternalFilesDir(null), "launcher-config.json")

    private fun decode(raw: String): LauncherConfig? =
        runCatching { LenientJson.decodeFromString(LauncherConfig.serializer(), raw) }.getOrNull()

    private companion object {
        val LenientJson = Json { ignoreUnknownKeys = true }
        val PrettyJson = Json { prettyPrint = true; encodeDefaults = true }
    }
}
