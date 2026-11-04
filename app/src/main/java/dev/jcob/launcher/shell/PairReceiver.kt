package dev.jcob.launcher.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.jcob.launcher.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the Wireless debugging pairing code from the PC:
 * `am broadcast -a dev.jcob.launcher.PAIR -n dev.jcob.launcher/.shell.PairReceiver --ei port P --es code C`
 *
 * The code is useless to anyone who can't already see the TV's pairing dialog, so an exported
 * receiver is fine here.
 */
class PairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val port = intent.getIntExtra("port", 0)
        val code = intent.getStringExtra("code").orEmpty()
        if (port == 0 || code.isEmpty()) return
        val shell = (context.applicationContext as LauncherApp).shell
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val result = shell.pair(port, code)
            Log.i("PairReceiver", "pairing on port $port: ${if (result.isSuccess) "OK" else result.exceptionOrNull()}")
            if (result.isSuccess) {
                val check = shell.run("echo shell-ok").getOrNull()?.output?.trim()
                Log.i("PairReceiver", "post-pair shell check: $check")
            }
            pending.finish()
        }
    }
}
