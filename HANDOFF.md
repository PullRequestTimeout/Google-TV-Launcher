# Handoff (2026-11-04, not a git repo)

## Goal
A custom Projectivy-style launcher APK for the user's Chromecast with Google TV HD ("Living Room TV", `boreal`, Android 14 / API 34, 32-bit armeabi-v7a, 10.0.0.93), shipped over Wi-Fi. **Next task: refine the UI.**

## Status
- Done and tested on the device:
  - Home screen: photo wallpaper (12 user photos bundled), one row of app banners, clock and date, Wi-Fi status, and three round icons (power, Wi-Fi, settings).
  - Settings (7 sections), soft and hard home modes, and the in-app revert.
  - Self-ADB shell, which reconnects by itself after reboot.
  - Wi-Fi scan, the TV-off command (the Chromecast sleeps), and pairing.
- Built but untested: install from URL or USB, weather (API verified from the PC only), PIN, backup and import, categories, move mode, remote remaps (YouTube button = `KEYCODE_BUTTON_3`), sleep and idle timers with the overlay countdown, and the photo screensaver (it couldn't be triggered without root; the user will check by idling).
- Current device state: Home mode = OFF (reverted to stock as the last test). The launcher is paired and the shell is connected. PC adb is on `10.0.0.93:40299` (saved in `scripts/.target`). The port changes on reboot, and `Connect-Tv` scans for it.
- The user is moving the Chromecast from USB-to-PC back to normal power.

## Decisions
- **Stack:** Kotlin, Compose with tv-material 1.0.0, AGP 8.5.2, Gradle 8.7, Kotlin 2.2.21, compileSdk/targetSdk 34. The user's Android Studio is 2022.x (JBR 17), so the build runs from the CLI only.
- **Shell:** Kadb **1.3.0**, not 2.x. Kadb 2.x needs compileSdk 36–37 and Kotlin 2.3+. dadb doesn't do Wireless-debugging TLS.
- **Pairing:** the PC sends the pairing code to the app (`PairReceiver` broadcast, `pair-launcher.ps1`). In-app pairing isn't possible because the pairing dialog closes when focus leaves.
- **Home modes:** both, user-selectable. The accessibility service intercepts Home in SOFT; HARD disables `launcherx` and `setupwraith`.
- **Wi-Fi:** "Connect" hands off to the system Wi-Fi screen (user chose this). `cmd wifi connect-network` is root-only on this firmware.
- **Photos:** bundled in the APK via `prepare-photos.ps1`.
- **Look:** a single bottom row.
- **Optional extras:** every home widget is optional (the user asked).
- **Lock:** a PIN for settings only.
- **Release builds:** always deploy release (R8, about 14 MB). The debug build is 54 MB.

## Gotchas and rejected approaches
- **Never drive the UI with blind key presses.** That wrongly turned off and forgot the user's Wi-Fi. Use scratchpad `tvnav.ps1` (`Get-Focused` before every OK). `input keyevent` from adb **bypasses** the accessibility key filter, so test Home and remaps with the real remote.
- Kadb `KadbCert.set()` calls `checkValidity()`, which fails at boot because the clock is behind before NTP. **Never regenerate the identity** when the files exist (fixed in `ShellClient.loadIdentity`).
- Android refuses `adb_wifi_enabled=1` before Wi-Fi connects, and the flag may still read 1. `ShellClient` toggles it after Wi-Fi comes up, using the network callback and a forced restart.
- The network also had to be accepted with "Always allow on this network" (done).
- `adb_allowed_connection_time` is set to 0, so keys don't expire after 7 days.
- Shell calls run in `ShellClient`'s own scope (`async`/`await`). UI-scope cancellation used to tear down the connection.
- CEC: the Chromecast sends `<Standby>` 4F:36 and `<Text View On>`, but the Samsung TV ignores them and sends nothing back. This is a TV-side Anynet+ fault. "Reset HDMI-CEC" was added; the user still needs to toggle Anynet+ on the TV.
- Lint vital is disabled: AGP 8.5's lint can't read Kotlin 2.2 metadata.
- `material-icons-extended` is needed for the power and Wi-Fi icons; R8 strips the unused ones.
- No Python on this machine. The Bash heredoc failed on one long file, so use the Write tool for those.
- In `common.ps1`, `Invoke-Adb` uses `$args`, so adb flags like `-a` and `-n` aren't bound as PowerShell parameters.

## Next step
UI refinement with the user. On-device checks: `.\scripts\deploy.ps1`, a screenshot via `adb exec-out screencap -p`, and the UI tree via `uiautomator dump`. Per the user's global CLAUDE.md, **ask** before running the ui-ux-reviewer. Known UI nits: the label above the row repeats the app name even when the banner already shows it; the app is called "Home" in Google TV's app list; the top icons are hard to see on bright photos.

## Key files
- `app/src/main/java/dev/jcob/launcher/shell/ShellClient.kt`: self-ADB connection, identity, reconnect logic.
- `app/src/main/java/dev/jcob/launcher/shell/HomeModeController.kt`: OFF/SOFT/HARD and revert, accessibility, screensaver setting.
- `app/src/main/java/dev/jcob/launcher/service/HomeAccessibilityService.kt`: Home intercept, remaps, idle tracking, the power-off overlay.
- `app/src/main/java/dev/jcob/launcher/ui/home/HomeScreen.kt`: the home layout (the main target for UI work).
- `app/src/main/java/dev/jcob/launcher/ui/settings/`: settings screens. `SettingsScreen.kt` has the custom focus handling for ◀ and ▶.
- `app/src/main/java/dev/jcob/launcher/ui/components/Components.kt`: shared TV rows, dialogs and the PIN pad.
- `scripts/`: deploy, pair-launcher, revert, config, prepare-photos. `common.ps1` holds `Connect-Tv` and the port scan.
- `README.md`: the user-facing guide and recovery steps.

## Open questions
- Preferred home mode: SOFT ("This launcher", recommended) or HARD?
- Rename the app label from "Home"?
- Does the user want a UI/UX review after refinement?
- CEC result after toggling Samsung Anynet+.
