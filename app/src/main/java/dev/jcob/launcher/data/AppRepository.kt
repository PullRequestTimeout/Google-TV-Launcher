package dev.jcob.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LaunchableApp(
    val packageName: String,
    val label: String,
    /** 16:9 TV banner if the app ships one, otherwise null and [icon] is shown. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    val launchIntent: Intent,
    val isSystem: Boolean,
)

/** Every launchable app on the device, kept up to date as packages change. */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch { _apps.value = load() }
    }

    private fun load(): List<LaunchableApp> {
        // TV apps declare LEANBACK_LAUNCHER; phone-only sideloads only declare LAUNCHER.
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)
        val byPackage = LinkedHashMap<String, Pair<ResolveInfo, String>>()
        leanback.forEach { byPackage[it.activityInfo.packageName] = it to Intent.CATEGORY_LEANBACK_LAUNCHER }
        phone.forEach { byPackage.putIfAbsent(it.activityInfo.packageName, it to Intent.CATEGORY_LAUNCHER) }
        byPackage.remove(context.packageName)

        return byPackage.values.mapNotNull { (info, category) ->
            runCatching {
                val activity = info.activityInfo
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(activity.packageName, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                LaunchableApp(
                    packageName = activity.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = (activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm))
                        ?.toBitmap(320, 180)?.asImageBitmap(),
                    icon = info.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                    launchIntent = intent,
                    isSystem = activity.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category),
            PackageManager.ResolveInfoFlags.of(0),
        )
}
