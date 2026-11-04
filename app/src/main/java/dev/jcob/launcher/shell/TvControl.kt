package dev.jcob.launcher.shell

/** HDMI-CEC and power actions. All need the shell; each returns false if it couldn't run. */
class TvControl(private val shell: ShellClient) {
    /** Wakes the TV and switches it to this input ("One Touch Play"). */
    suspend fun tvOn(): Boolean = ok("cmd hdmi_control onetouchplay")

    /**
     * Puts the Chromecast to sleep. With CEC power_control_mode=broadcast (the default here) the
     * TV goes to standby with it.
     */
    suspend fun tvOff(): Boolean = ok("input keyevent KEYCODE_SLEEP")

    /** Asks the TV to show its input picker. TV support for this over CEC varies. */
    suspend fun inputMenu(): Boolean = ok("input keyevent KEYCODE_TV_INPUT")

    suspend fun restart(): Boolean = ok("svc power reboot")

    /**
     * Turns the Chromecast's CEC stack off and on again. Samsung Anynet+ in particular tends to
     * stop answering after a while; re-announcing often brings the link back.
     */
    suspend fun resetCec(): Boolean = ok(
        "cmd hdmi_control cec_setting set hdmi_cec_enabled 0 && sleep 3 && " +
            "cmd hdmi_control cec_setting set hdmi_cec_enabled 1 && sleep 2 && cmd hdmi_control onetouchplay"
    )

    /** Media session states 3 (playing) mean something is actively playing. */
    suspend fun isMediaPlaying(): Boolean =
        shell.output("dumpsys media_session | grep -c 'state=PlaybackState {state=3'")
            ?.trim()?.toIntOrNull()?.let { it > 0 } ?: false

    private suspend fun ok(command: String) = shell.run(command).getOrNull()?.exitCode == 0
}
