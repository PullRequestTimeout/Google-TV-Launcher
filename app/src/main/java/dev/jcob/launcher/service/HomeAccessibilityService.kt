package dev.jcob.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.MainActivity
import dev.jcob.launcher.data.HomeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sees every remote button press before apps do. Used for:
 *  - redirecting Home to this launcher in [HomeMode.SOFT],
 *  - user-defined button remaps (short and long press),
 *  - idle tracking for auto-off, and the "TV turning off" countdown overlay.
 */
class HomeAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as LauncherApp

    /** Key currently held whose short action we deferred to see if it becomes a long press. */
    private var pendingKey: Int? = null
    private var pendingLongFired = false
    /** Set while re-injecting a deferred key so we let it through. */
    private var passThroughKey: Int? = null

    private var warning: TextView? = null
    private var warningJob: Job? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        lastInputAt = SystemClock.elapsedRealtime()
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount}")
        if (warning != null) {
            if (event.action == KeyEvent.ACTION_UP) cancelPowerOffWarning()
            return true
        }

        val code = event.keyCode
        capture?.let { listener ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                capture = null
                scope.launch { listener(code) }
            }
            return true
        }

        if (passThroughKey == code) {
            if (event.action == KeyEvent.ACTION_UP) passThroughKey = null
            return false
        }

        val config = app.config.config.value
        if (code == KeyEvent.KEYCODE_HOME && config.homeMode == HomeMode.SOFT) {
            if (event.action == KeyEvent.ACTION_UP) goHome()
            return true
        }

        val name = KeyEvent.keyCodeToString(code)
        val shortTarget = config.keyRemaps[name]
        val longTarget = config.longPressRemaps[name]
        if (shortTarget == null && longTarget == null) return false

        if (longTarget == null) {
            // Short-only remap: act immediately on press.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launch(shortTarget!!)
            return true
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    pendingKey = code
                    pendingLongFired = false
                } else if (pendingKey == code && !pendingLongFired && (event.isLongPress || event.repeatCount >= 2)) {
                    pendingLongFired = true
                    launch(longTarget)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (pendingKey == code && !pendingLongFired) {
                    if (shortTarget != null) launch(shortTarget) else reinject(code)
                }
                pendingKey = null
            }
        }
        return true
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun launch(target: String) {
        if (target == TargetHome) return goHome()
        val intent = packageManager.getLeanbackLaunchIntentForPackage(target)
            ?: packageManager.getLaunchIntentForPackage(target)
            ?: return
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A long-press-only remap swallowed a short press; send the original key back in. */
    private fun reinject(code: Int) {
        passThroughKey = code
        scope.launch { app.shell.run("input keyevent $code") }
    }

    /** Shows a countdown over whatever is playing; any button press cancels it. */
    fun showPowerOffWarning(reason: String, seconds: Int = 60) {
        if (warning != null) return
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(48, 28, 48, 28)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.argb(220, 20, 20, 24))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 64
        }
        getSystemService(WindowManager::class.java).addView(view, params)
        warning = view
        warningJob = scope.launch {
            for (left in seconds downTo 1) {
                view.text = "$reason – TV turns off in $left s. Press any button to cancel."
                delay(1_000)
            }
            removeWarning()
            app.tv.tvOff()
        }
    }

    private fun cancelPowerOffWarning() {
        warningJob?.cancel()
        removeWarning()
        PowerTimers.clearSleepTimer(this)
    }

    private fun removeWarning() {
        warning?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        warning = null
    }

    companion object {
        /** `adb shell setprop log.tag.HomeA11y DEBUG` to log every key press. */
        private const val TAG = "HomeA11y"
        const val TargetHome = "@home"

        @Volatile
        var instance: HomeAccessibilityService? = null
            private set

        /** Last remote input, in elapsedRealtime. Reset when the process starts. */
        @Volatile
        var lastInputAt: Long = SystemClock.elapsedRealtime()
            private set

        /** When set, the next button press is handed to this instead of being handled. */
        @Volatile
        var capture: (suspend (Int) -> Unit)? = null
    }
}
