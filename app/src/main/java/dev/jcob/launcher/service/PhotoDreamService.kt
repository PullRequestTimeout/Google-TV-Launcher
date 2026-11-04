package dev.jcob.launcher.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextClock
import dev.jcob.launcher.LauncherApp
import dev.jcob.launcher.data.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screensaver: the bundled photos, cross-fading with a slow pan, plus a clock. */
class PhotoDreamService : DreamService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val layers = List(2) { ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f } }
        val clock = TextClock(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            setShadowLayer(12f, 0f, 2f, Color.BLACK)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layers.forEach { addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
            addView(clock, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, 72, 56)
            })
        }
        setContentView(root)

        val photos = Photos.assetNames(this).shuffled()
        if (photos.isEmpty()) return
        val seconds = (application as LauncherApp).config.config.value.screensaverSeconds.coerceAtLeast(5)
        scope.launch {
            var front = 0
            var i = 0
            while (true) {
                val bitmap = withContext(Dispatchers.IO) { load(photos[i % photos.size]) }
                val view = layers[front]
                val other = layers[1 - front]
                view.setImageBitmap(bitmap)
                view.scaleX = 1.0f; view.scaleY = 1.0f
                view.animate().alpha(1f).setDuration(2_000).start()
                other.animate().alpha(0f).setDuration(2_000).start()
                // Slow Ken Burns zoom for the whole time the photo is on screen.
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(seconds * 1_000L + 2_000).start()
                delay(seconds * 1_000L)
                front = 1 - front
                i++
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun load(name: String): Bitmap? = runCatching {
        assets.open("photos/$name").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
