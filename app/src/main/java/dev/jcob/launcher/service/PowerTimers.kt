package dev.jcob.launcher.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.MainActivity
import dev.jcob.launcher.data.HomeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Sleep timer and idle auto-off, both driven by AlarmManager so they survive process death. */
object PowerTimers {
    private const val ActionSleep = "dev.jcob.launcher.SLEEP_TIMER"
    private const val ActionIdleCheck = "dev.jcob.launcher.IDLE_CHECK"
    private const val IdleCheckIntervalMs = 15 * 60_000L

    fun setSleepTimer(context: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleepIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleepIntent(context))
        }
        prefs(context).edit().putLong("sleep_at", at).apply()
    }

    fun clearSleepTimer(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(sleepIntent(context))
        prefs(context).edit().remove("sleep_at").apply()
    }

    /** Wall-clock millis the sleep timer fires at, or null if none is set. */
    fun sleepTimerAt(context: Context): Long? =
        prefs(context).getLong("sleep_at", 0).takeIf { it > System.currentTimeMillis() }

    /** (Re)arms the repeating idle check. Cheap to call often. */
    fun scheduleIdleCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + IdleCheckIntervalMs,
            IdleCheckIntervalMs,
            pending(context, ActionIdleCheck),
        )
    }

    private fun sleepIntent(context: Context) = pending(context, ActionSleep)

    private fun pending(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, Receiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) = context.getSharedPreferences("timers", Context.MODE_PRIVATE)

    private fun warnThenPowerOff(context: Context, reason: String) {
        val service = HomeAccessibilityService.instance
        if (service != null) {
            service.showPowerOffWarning(reason)
        } else {
            val app = context.applicationContext as LauncherApp
            CoroutineScope(Dispatchers.IO).launch { app.tv.tvOff() }
        }
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext as LauncherApp
            when (intent.action) {
                ActionSleep -> {
                    prefs(context).edit().remove("sleep_at").apply()
                    warnThenPowerOff(context, "Sleep timer")
                }
                ActionIdleCheck -> {
                    val hours = app.config.config.value.autoOffHours
                    if (hours <= 0 || !app.isScreenOn()) return
                    val idleMs = SystemClock.elapsedRealtime() - HomeAccessibilityService.lastInputAt
                    if (idleMs >= hours * 3_600_000L) warnThenPowerOff(context, "No activity for $hours h")
                }
            }
        }
    }

    /** Restores shell access, timers and (in soft mode) shows our home after a reboot. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
            val app = context.applicationContext as LauncherApp
            scheduleIdleCheck(context)
            app.shell.connectInBackground()
            if (app.config.config.value.homeMode == HomeMode.SOFT) {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
