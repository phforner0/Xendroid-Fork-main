package xendroid.compose.ui.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A game's box art at 3:4. With a cover the player picked, the art fills it. With only the game's
 * own 64 px icon ([smart]), the box is composed: the icon blurred behind, the icon sharp in the
 * middle and the name under it, so a library of icon-only games still reads as covers.
 */
@Composable
fun GameCover(
    art: Any?,
    name: String,
    smart: Boolean,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    discLabel: String? = null,
    radius: Dp? = null,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(radius ?: c.coverRadius)
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shape).background(c.solid(c.s2))
    ) {
        val w = maxWidth
        if (!smart) {
            AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val saturate = remember {
                ColorMatrix().apply {
                    setToSaturation(1.5f)
                    // brightness .62
                    val m = values
                    for (i in 0..2) for (j in 0..2) m[i * 5 + j] *= 0.62f
                }
            }
            AsyncImage(
                art, null,
                Modifier.requiredSize(w * 1.4f, w * 1.4f / 0.75f).blur(14.dp),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(saturate),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            val icon = w * 0.46f
            AsyncImage(
                art, null,
                Modifier.align(Alignment.TopCenter).offset(y = w / 0.75f * 0.38f - icon / 2).size(icon)
                    .shadow(8.dp, RoundedCornerShape(icon * 0.14f)).clip(RoundedCornerShape(icon * 0.14f)),
                contentScale = ContentScale.Crop,
            )
            val size = with(LocalDensity.current) { (w * 0.11f).toSp() }
            Text(
                name.uppercase(),
                style = XdText.h1.copy(
                    fontSize = size, lineHeight = size * 1.02f, letterSpacing = 0.01.em, fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 10f, offset = androidx.compose.ui.geometry.Offset(0f, 2f)),
                ),
                color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = w * 0.07f, end = w * 0.07f, bottom = w / 0.75f * 0.08f),
            )
        }
        if (favorite) {
            val d = (w * 0.13f).coerceIn(18.dp, 26.dp)
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = w * 0.06f, end = w * 0.06f).size(d).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(XdIcons.starFilled, null, Modifier.size(d * 0.62f), tint = Color(0xFFF3CF55)) }
        }
        if (discLabel != null) {
            val size = with(LocalDensity.current) { maxOf(9.sp.toDp(), w * 0.075f).toSp() }
            Text(
                discLabel, style = XdText.tiny.copy(fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em),
                color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(start = w * 0.06f, top = w * 0.06f)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), shape))
    }
}

/** A cover's two colours for controller mode: [dyn] (deep, behind) and [accent] (vivid). */
data class CoverColors(val dyn: Color, val accent: Color)

private val coverColorCache = LruCache<String, CoverColors>(64)

/** The colours of [art], read once per image (null until read, or for no image). */
@Composable
fun rememberCoverColors(art: Any?): CoverColors? {
    val context = LocalContext.current
    val key = remember(art) { art?.let { a -> if (a is java.io.File) "${a.path}:${a.lastModified()}" else a.toString() } }
    var colors by remember(key) { mutableStateOf(key?.let { coverColorCache.get(it) }) }
    LaunchedEffect(key) {
        if (key == null || colors != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(art).allowHardware(false).size(48).build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.let { it as? BitmapDrawable }?.bitmap
            ?: return@LaunchedEffect
        val read = withContext(Dispatchers.Default) { coverColorsOf(bitmap) }
        coverColorCache.put(key, read)
        colors = read
    }
    return colors
}

/** Average of the darker half for [CoverColors.dyn]; the most saturated bright pixels for the accent. */
fun coverColorsOf(bitmap: Bitmap): CoverColors {
    val small = if (bitmap.width > 24 || bitmap.height > 24) Bitmap.createScaledBitmap(bitmap, 24, 32, true) else bitmap
    val px = IntArray(small.width * small.height)
    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
    val hsv = FloatArray(3)
    var dr = 0f; var dg = 0f; var db = 0f; var dn = 0
    var best = 0; var bestScore = -1f
    for (p in px) {
        val r = (p shr 16 and 255); val g = (p shr 8 and 255); val b = (p and 255)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        if (hsv[2] < 0.6f) { dr += r; dg += g; db += b; dn++ }
        val score = hsv[1] * 0.7f + hsv[2] * 0.3f
        if (hsv[2] > 0.45f && score > bestScore) { bestScore = score; best = p }
    }
    val dyn = if (dn > 0) Color(dr / dn / 255f, dg / dn / 255f, db / dn / 255f) else Color(0xFF1D4F3A)
    val accent = if (bestScore >= 0f) Color(best or (0xFF shl 24)) else Color(0xFF9BE37F)
    // The background colour is kept deep but not black: lift a very dark average a little.
    val lifted = if (dyn.toArgb().let { android.graphics.Color.luminance(it) } < 0.02f) lerp(dyn, accent, 0.25f) else dyn
    return CoverColors(lifted, accent)
}
