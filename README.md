# TV Launcher for Chromecast with Google TV (HD)

A minimal home screen: your apps in one row over your own photos, with TV power (HDMI-CEC),
Wi-Fi tools, app install/uninstall, remote-button shortcuts, and a way back to the stock launcher.

## Day-to-day

| Task | How |
|---|---|
| Ship a change | `.\scripts\deploy.ps1` (first time: `-Ip 10.0.0.93`) |
| Change the photos | Put JPEG/PNG files in `photos\`, run `.\scripts\prepare-photos.ps1`, then `deploy.ps1` |
| Back up settings | TV: Settings → Home & system → Export, then `.\scripts\config.ps1 pull` |
| Restore settings | `.\scripts\config.ps1 push`, then TV: Settings → Home & system → Import |

## Home modes (Settings → Home & system)

- **Google TV**: the stock launcher is home; this one is just an app ("Home" in the apps list).
- **This launcher**: the Home button opens this launcher. The stock launcher stays installed.
- **This launcher only**: the stock launcher packages are disabled.

**Revert to Google TV launcher** on the same screen undoes either one.

## If something goes wrong

- **The launcher is broken but adb still works:** `.\scripts\revert.ps1` restores the stock launcher
  (`-Uninstall` also removes this app).
- **adb over Wi-Fi is down:** unplug the Chromecast's power and plug its USB-C into the PC with a
  data cable, turn on *USB debugging* in Developer options, and run
  `.\scripts\revert.ps1 -Target 41101HFGN704WL`.
- **The launcher says "Shell access: Not paired":** on the TV, go to Developer options → Wireless
  debugging → Pair device with pairing code. Then run
  `.\scripts\pair-launcher.ps1 -Port <port> -Code <code>`.

## How the privileged bits work

A normal app can't use CEC, manage Wi-Fi, or disable packages. This launcher pairs with the
Chromecast's own **Wireless debugging** (once, using `pair-launcher.ps1`) and runs shell commands
over a localhost adb connection, using the [Kadb](https://github.com/flyfishxu/Kadb) library.
After a reboot it turns Wireless debugging back on and reconnects by itself. This needs
`WRITE_SECURE_SETTINGS`, which `deploy.ps1` grants. Developer options must stay enabled.

## Known limits

- **Joining a new Wi-Fi network** opens the system Wi-Fi screen, because this firmware only
  allows `cmd wifi connect-network` for root.
- **TV power over CEC** depends on the TV. If on/off stops working, try Settings → TV & power →
  Reset HDMI-CEC. On Samsung TVs, also turn Anynet+ off and on (Settings → General →
  External Device Manager).
