package com.forge.workout.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The plan data carries design-relative paths ("assets/0426-A6wtbuL.gif"); the APK stores the
 * same files under `assets/media/`. Resolve in exactly one place.
 */
fun mediaPath(ref: String): String = "media/" + ref.substringAfterLast('/')

/** 33 thumbnails at 180×180 — small enough to keep decoded for the life of the process. */
private val thumbCache = HashMap<String, ImageBitmap>()

private fun loadThumb(context: Context, path: String): ImageBitmap? = synchronized(thumbCache) {
    thumbCache[path] ?: runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { thumbCache[path] = it }
}

/** Static 180×180 thumbnail from the bundled dataset. */
@Composable
fun ExerciseThumb(ref: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = remember(ref) { mediaPath(ref) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadThumb(context, path) }
    }
    Box(modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Animated exercise demo — the 720×720 animated WebP made by tools/upscale_media.py (a GIF works
 * too). Uses the platform ImageDecoder (API 28+) so no image library is needed;
 * [AnimatedImageDrawable] has to be started explicitly every time it is decoded.
 */
@Composable
fun ExerciseGif(ref: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = remember(ref) { mediaPath(ref) }
    val drawable by produceState<Drawable?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, path))
            }.getOrNull()
        }
    }
    LaunchedEffect(drawable) { (drawable as? AnimatedImageDrawable)?.start() }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            view.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.start()
        },
    )
}
