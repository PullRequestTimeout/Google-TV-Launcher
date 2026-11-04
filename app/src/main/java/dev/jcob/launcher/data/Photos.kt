package dev.jcob.launcher.data

import android.content.Context

/** Photos bundled under assets/photos by scripts/prepare-photos.ps1. */
object Photos {
    fun assetNames(context: Context): List<String> =
        (context.assets.list("photos") ?: emptyArray())
            .filter { it.endsWith(".jpg", ignoreCase = true) || it.endsWith(".webp", ignoreCase = true) }
            .sorted()

    /** Coil-loadable URLs for [assetNames]. */
    fun list(context: Context): List<String> =
        assetNames(context).map { "file:///android_asset/photos/$it" }
}
