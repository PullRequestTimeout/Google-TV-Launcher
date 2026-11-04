package dev.jcob.launcher.data

/** Apps in the user's order (unordered ones appended alphabetically), with or without hidden ones. */
fun orderedApps(apps: List<LaunchableApp>, config: LauncherConfig, includeHidden: Boolean = false): List<LaunchableApp> {
    val position = config.order.withIndex().associate { (i, pkg) -> pkg to i }
    return apps
        .filter { includeHidden || it.packageName !in config.hidden }
        .sortedWith(compareBy<LaunchableApp> { position[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
}

/** Moves [pkg] by [delta] places within the full ordering and returns the new order list. */
fun LauncherConfig.moved(apps: List<LaunchableApp>, pkg: String, delta: Int): LauncherConfig {
    val current = orderedApps(apps, this, includeHidden = true).map { it.packageName }.toMutableList()
    val from = current.indexOf(pkg)
    if (from < 0) return this
    // Skip over hidden apps so a visible move always changes what's on screen.
    var to = from
    var remaining = kotlin.math.abs(delta)
    val step = if (delta > 0) 1 else -1
    while (remaining > 0) {
        to += step
        if (to !in current.indices) return this
        if (current[to] !in hidden) remaining--
    }
    current.removeAt(from)
    current.add(to, pkg)
    return copy(order = current)
}
