package xendroid.compose.shots

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generated art for the example library: a 64 px "title icon" and a 3:4 "box cover" per game,
 * drawn from the game's palette and motif (no real artwork is bundled or downloaded).
 */
object SampleArt {
    fun iconPng(g: SampleGame): ByteArray = png(icon(g, 64))
    fun coverPng(g: SampleGame): ByteArray = png(cover(g, 330, 440))

    private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
    )

    private fun alpha(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun icon(g: SampleGame, size: Int): Bitmap {
        val (bg, mid, acc) = g.palette
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), mid, bg, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        motif(c, g, size.toFloat(), size.toFloat(), small = true)
        // A bold initial, the way many title icons carry the logo.
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = size * 0.42f
            color = Color.WHITE
            setShadowLayer(size * 0.06f, 0f, size * 0.02f, Color.argb(160, 0, 0, 0))
        }
        val initials = g.name.split(' ', ':').filter { it.isNotBlank() && it[0].isLetterOrDigit() }.take(2).joinToString("") { it.take(1) }.uppercase()
        val w = t.measureText(initials)
        c.drawText(initials, (size - w) / 2f, size * 0.86f, t)
        p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.04f; p.color = alpha(acc, 0.9f)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        return b
    }

    fun cover(g: SampleGame, w: Int, h: Int): Bitmap {
        val (bg, mid, acc) = g.palette
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), mid, bg, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.5f, h * 0.38f, w * 0.75f, alpha(mix(mid, acc, 0.35f), 0.55f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        motif(c, g, w.toFloat(), h.toFloat(), small = false)
        // Bottom shade for the title.
        p.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), Color.TRANSPARENT, alpha(bg, 0.92f), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // The green console band on top of every box.
        val band = h * 0.085f
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.rgb(0x2F, 0x7D, 0x0F), Color.rgb(0x7B, 0xC9, 0x2A), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), band, p)
        p.shader = null
        val bandText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD); textSize = band * 0.5f; color = Color.WHITE; letterSpacing = 0.12f
        }
        c.drawText("XBOX 360", w * 0.06f, band * 0.68f, bandText)
        // Title.
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = if (g.name.length > 18) w * 0.105f else w * 0.13f
            color = Color.WHITE
            letterSpacing = 0.02f
            setShadowLayer(w * 0.03f, 0f, w * 0.01f, Color.argb(200, 0, 0, 0))
        }
        val layout = StaticLayout.Builder.obtain(g.name.uppercase(), 0, g.name.length, title, (w * 0.86f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setLineSpacing(0f, 0.92f).build()
        c.save()
        c.translate(w * 0.07f, h * 0.93f - layout.height)
        layout.draw(c)
        c.restore()
        p.color = alpha(acc, 0.95f)
        c.drawRect(w * 0.38f, h * 0.95f, w * 0.62f, h * 0.955f, p)
        return b
    }

    private fun motif(c: Canvas, g: SampleGame, w: Float, h: Float, small: Boolean) {
        val (bg, mid, acc) = g.palette
        val r = Random(g.titleId.hashCode())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = minOf(w, h)
        when (g.motif) {
            "rings" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.05f
                p.color = alpha(acc, 0.85f)
                p.maskFilter = if (small) null else BlurMaskFilter(s * 0.01f, BlurMaskFilter.Blur.SOLID)
                c.drawOval(RectF(-w * 0.2f, h * 0.18f, w * 1.2f, h * 0.62f), p)
                p.strokeWidth = s * 0.015f; p.color = alpha(Color.WHITE, 0.6f)
                c.drawOval(RectF(-w * 0.1f, h * 0.26f, w * 1.1f, h * 0.55f), p)
                p.style = Paint.Style.FILL; p.maskFilter = null
                p.color = alpha(mix(acc, Color.WHITE, 0.4f), 0.9f)
                c.drawCircle(w * 0.5f, h * 0.4f, s * 0.12f, p)
            }
            "sun" -> {
                p.shader = RadialGradient(w * 0.5f, h * 0.48f, s * 0.42f, intArrayOf(alpha(acc, 1f), alpha(acc, 0.35f), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(w * 0.5f, h * 0.48f, s * 0.42f, p)
                p.shader = null
                p.color = alpha(bg, 0.85f)
                val road = Path().apply { moveTo(w * 0.42f, h * 0.55f); lineTo(w * 0.58f, h * 0.55f); lineTo(w, h); lineTo(0f, h); close() }
                c.drawRect(0f, h * 0.55f, w, h, p)
                p.color = alpha(mid, 0.9f); c.drawPath(road, p)
                p.color = alpha(acc, 0.9f); p.strokeWidth = s * 0.012f; p.style = Paint.Style.STROKE
                c.drawLine(w * 0.5f, h * 0.58f, w * 0.5f, h, p)
            }
            "mountains" -> {
                p.color = alpha(acc, 0.9f); c.drawCircle(w * 0.68f, h * 0.36f, s * 0.13f, p)
                for (layer in 0 until 3) {
                    val path = Path()
                    val base = h * (0.55f + layer * 0.12f)
                    path.moveTo(0f, h)
                    path.lineTo(0f, base)
                    var x = 0f
                    while (x < w) {
                        x += w * (0.15f + r.nextFloat() * 0.2f)
                        path.lineTo(x, base - h * (0.08f + r.nextFloat() * 0.16f))
                        x += w * 0.1f
                        path.lineTo(x, base)
                    }
                    path.lineTo(w, h); path.close()
                    p.color = mix(mid, bg, 0.35f + layer * 0.3f)
                    c.drawPath(path, p)
                }
            }
            "slash" -> {
                p.color = alpha(acc, 0.85f)
                p.strokeWidth = s * 0.035f; p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
                c.drawLine(w * 0.15f, h * 0.7f, w * 0.85f, h * 0.18f, p)
                p.strokeWidth = s * 0.012f; p.color = alpha(Color.WHITE, 0.7f)
                c.drawLine(w * 0.25f, h * 0.72f, w * 0.9f, h * 0.26f, p)
                p.style = Paint.Style.FILL; p.color = alpha(mix(mid, Color.BLACK, 0.3f), 0.7f)
                c.drawCircle(w * 0.5f, h * 0.45f, s * 0.2f, p)
            }
            "bokeh" -> repeat(if (small) 6 else 22) {
                p.color = alpha(if (r.nextBoolean()) acc else mix(mid, Color.WHITE, 0.3f), 0.12f + r.nextFloat() * 0.35f)
                c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.8f, s * (0.04f + r.nextFloat() * 0.12f), p)
            }
            "grid" -> {
                var x = 0f
                while (x < w) {
                    val bw = w * (0.08f + r.nextFloat() * 0.1f)
                    val bh = h * (0.25f + r.nextFloat() * 0.4f)
                    p.color = mix(bg, mid, 0.3f + r.nextFloat() * 0.4f)
                    c.drawRect(x, h - bh, x + bw, h, p)
                    p.color = alpha(acc, 0.7f)
                    var y = h - bh + h * 0.02f
                    while (y < h - h * 0.03f) {
                        if (r.nextFloat() > 0.4f) c.drawRect(x + bw * 0.2f, y, x + bw * 0.4f, y + h * 0.012f, p)
                        if (r.nextFloat() > 0.5f) c.drawRect(x + bw * 0.6f, y, x + bw * 0.8f, y + h * 0.012f, p)
                        y += h * 0.035f
                    }
                    x += bw + w * 0.01f
                }
            }
            "bands" -> for (i in 0 until 6) {
                val path = Path()
                val y0 = h * (0.25f + i * 0.1f)
                path.moveTo(0f, y0)
                path.cubicTo(w * 0.3f, y0 - h * 0.08f, w * 0.6f, y0 + h * 0.08f, w, y0 - h * 0.02f)
                path.lineTo(w, y0 + h * 0.06f)
                path.cubicTo(w * 0.6f, y0 + h * 0.14f, w * 0.3f, y0, 0f, y0 + h * 0.07f)
                path.close()
                p.color = alpha(if (i % 2 == 0) acc else mix(mid, Color.WHITE, 0.25f), 0.25f + 0.08f * i)
                c.drawPath(path, p)
            }
            "shards" -> repeat(if (small) 4 else 9) {
                val path = Path()
                val cx = r.nextFloat() * w; val cy = h * (0.15f + r.nextFloat() * 0.6f); val rr = s * (0.08f + r.nextFloat() * 0.2f)
                val n = 3 + r.nextInt(2)
                for (k in 0 until n) {
                    val a = (k * 2 * Math.PI / n + r.nextFloat()).toFloat()
                    val px = cx + cos(a) * rr; val py = cy + sin(a) * rr
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                p.color = alpha(if (r.nextFloat() > 0.6f) acc else mix(mid, Color.WHITE, 0.15f), 0.35f + r.nextFloat() * 0.4f)
                c.drawPath(path, p)
            }
            "neon" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.008f
                p.color = alpha(mid, 0.9f)
                var i = 0f
                while (i <= 1f) { c.drawLine(w * i, 0f, w * i, h, p); c.drawLine(0f, h * i, w, h * i, p); i += 0.1f }
                p.strokeWidth = s * 0.03f
                listOf(acc, Color.rgb(0xFF, 0x3E, 0xC8), Color.rgb(0xFF, 0xE1, 0x3E)).forEachIndexed { k, col ->
                    p.color = col
                    p.maskFilter = if (small) null else BlurMaskFilter(s * 0.02f, BlurMaskFilter.Blur.SOLID)
                    c.drawCircle(w * (0.3f + 0.2f * k), h * (0.35f + 0.1f * (k % 2)), s * (0.1f + 0.04f * k), p)
                }
                p.maskFilter = null
            }
            "pinata" -> {
                val cols = intArrayOf(acc, Color.rgb(0x3E, 0xC8, 0xFF), Color.rgb(0x9B, 0xE3, 0x7F), Color.rgb(0xFF, 0x7A, 0x3E), mix(mid, Color.WHITE, 0.3f))
                for (i in 0 until 9) {
                    p.color = cols[i % cols.size]
                    c.drawRect(w * 0.2f, h * (0.22f + i * 0.05f), w * 0.8f, h * (0.25f + i * 0.05f), p)
                }
            }
            "toy" -> {
                val path = Path()
                val cx = w * 0.5f; val cy = h * 0.42f; val ro = s * 0.3f; val ri = s * 0.13f
                for (k in 0 until 10) {
                    val a = (Math.PI / 2 + k * Math.PI / 5).toFloat()
                    val rad = if (k % 2 == 0) ro else ri
                    val px = cx + cos(a) * rad; val py = cy - sin(a) * rad
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                p.color = acc; c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.02f; p.color = Color.WHITE; c.drawPath(path, p)
            }
        }
    }
}
