package dev.jcob.launcher.shell

/** Install/uninstall through the shell, so there are no system confirmation dialogs. */
class Packages(private val shell: ShellClient) {
    /** .apk files on USB drives and in Downloads. Shell can read these without storage permissions. */
    suspend fun findApks(): List<String> {
        val out = shell.output(
            "find /storage -maxdepth 4 -iname '*.apk' -not -path '/storage/emulated/*' 2>/dev/null; " +
                "find /sdcard/Download -maxdepth 2 -iname '*.apk' 2>/dev/null"
        ) ?: return emptyList()
        return out.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    /** Returns null on success or the error output. */
    suspend fun installPath(path: String): String? {
        val result = shell.run("pm install -r '${path.replace("'", "'\\''")}'").getOrNull()
            ?: return "Shell unavailable"
        return if (result.exitCode == 0 && result.output.contains("Success")) null else result.allOutput.trim()
    }

    suspend fun uninstall(packageName: String): String? {
        val result = shell.run("pm uninstall $packageName").getOrNull() ?: return "Shell unavailable"
        return if (result.exitCode == 0) null else result.allOutput.trim()
    }
}
