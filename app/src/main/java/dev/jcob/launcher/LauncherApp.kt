package dev.jcob.launcher

import android.app.Application
import android.os.PowerManager
import dev.jcob.launcher.data.AppRepository
import dev.jcob.launcher.data.ConfigStore
import dev.jcob.launcher.service.PowerTimers
import dev.jcob.launcher.shell.HomeModeController
import dev.jcob.launcher.shell.Packages
import dev.jcob.launcher.shell.ShellClient
import dev.jcob.launcher.shell.TvControl
import dev.jcob.launcher.shell.WifiShell

/** Holds the app-wide singletons; there's no DI framework in a project this size. */
class LauncherApp : Application() {
    lateinit var apps: AppRepository
        private set
    lateinit var config: ConfigStore
        private set
    lateinit var shell: ShellClient
        private set
    lateinit var tv: TvControl
        private set
    lateinit var wifi: WifiShell
        private set
    lateinit var packages: Packages
        private set
    lateinit var homeMode: HomeModeController
        private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepository(this)
        config = ConfigStore(this)
        shell = ShellClient(this)
        tv = TvControl(shell)
        wifi = WifiShell(shell)
        packages = Packages(shell)
        homeMode = HomeModeController(this, shell, config)
        shell.connectInBackground()
        PowerTimers.scheduleIdleCheck(this)
    }

    fun isScreenOn(): Boolean = getSystemService(PowerManager::class.java).isInteractive
}
