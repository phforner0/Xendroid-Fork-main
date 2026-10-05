package xendroid.compose.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The redesign's line icons (24 px grid, 1.9 px stroke, round ends), the same set as the
 * prototype's base.js. Kept as the SVG they were drawn in and turned into vectors once; tinted
 * by [androidx.compose.material3.Icon] like any Material icon. Only path, circle and rect (with
 * rx and one rotation) are used.
 */
object XdIcons {
    private val cache = HashMap<String, ImageVector>()

    private fun icon(name: String, svg: String): ImageVector = cache.getOrPut(name) { build(name, svg) }

    private val element = Regex("<(path|circle|rect)([^>]*)/>")
    private val attribute = Regex("([a-zA-Z-]+)=\"([^\"]*)\"")

    private fun build(name: String, svg: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (m in element.findAll(svg)) {
            val a = attribute.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            fun f(k: String) = a[k]?.toFloatOrNull() ?: 0f
            val d = when (m.groupValues[1]) {
                "circle" -> {
                    val cx = f("cx"); val cy = f("cy"); val r = f("r")
                    "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"
                }
                "rect" -> {
                    val x = f("x"); val y = f("y"); val w = f("width"); val h = f("height"); val rx = minOf(f("rx"), w / 2, h / 2)
                    if (rx <= 0f) "M$x,$y h$w v$h h${-w} Z"
                    else "M${x + rx},$y h${w - 2 * rx} a$rx,$rx 0 0 1 $rx,$rx v${h - 2 * rx} a$rx,$rx 0 0 1 ${-rx},$rx " +
                        "h${-(w - 2 * rx)} a$rx,$rx 0 0 1 ${-rx},${-rx} v${-(h - 2 * rx)} a$rx,$rx 0 0 1 $rx,${-rx} Z"
                }
                else -> a["d"] ?: continue
            }
            val nodes = PathParser().parsePathString(d).toNodes()
            val filled = a["fill"] == "currentColor"
            val stroked = a["stroke"] != "none"
            val rotate = a["transform"]?.let { Regex("rotate\\(([-\\d.]+) ([-\\d.]+) ([-\\d.]+)\\)").find(it) }
            val add: ImageVector.Builder.() -> Unit = {
                addPath(
                    pathData = nodes,
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = if (stroked) SolidColor(Color.Black) else null,
                    strokeLineWidth = if (stroked) 1.9f else 0f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (rotate != null) {
                val (deg, px, py) = rotate.destructured
                b.group(rotate = deg.toFloat(), pivotX = px.toFloat(), pivotY = py.toFloat()) { add() }
            } else b.add()
        }
        return b.build()
    }

    val play get() = icon("play", """<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>""")
    val star get() = icon("star", """<path d="M12 3.6l2.55 5.2 5.7.83-4.12 4.02.97 5.68L12 16.64l-5.1 2.69.97-5.68L3.75 9.63l5.7-.83z"/>""")
    val starFilled get() = icon("starF", """<path d="M12 3.6l2.55 5.2 5.7.83-4.12 4.02.97 5.68L12 16.64l-5.1 2.69.97-5.68L3.75 9.63l5.7-.83z" fill="currentColor"/>""")
    val search get() = icon("search", """<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.3-4.3"/>""")
    val gear get() = icon("gear", """<circle cx="12" cy="12" r="3.2"/><path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.85 1.85M16.65 16.65l1.85 1.85M5.5 18.5l1.85-1.85M16.65 7.35l1.85-1.85"/>""")
    val more get() = icon("more", """<circle cx="12" cy="5.5" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="18.5" r="1.5" fill="currentColor" stroke="none"/>""")
    val back get() = icon("back", """<path d="M14.5 5.5L8 12l6.5 6.5"/>""")
    val chevL get() = icon("chevL", """<path d="M14.5 6L8.5 12l6 6"/>""")
    val chevR get() = icon("chevR", """<path d="M9.5 6l6 6-6 6"/>""")
    val chevD get() = icon("chevD", """<path d="M6 9.5l6 6 6-6"/>""")
    val copy get() = icon("copy", """<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5V6A1.5 1.5 0 0 0 14 4.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5"/>""")
    val grid get() = icon("grid", """<rect x="4" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5"/>""")
    val home get() = icon("home", """<path d="M4 11l8-6.5 8 6.5v8.5a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>""")
    val clock get() = icon("clock", """<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>""")
    val layers get() = icon("layers", """<path d="M12 4l8.5 4.2L12 12.4 3.5 8.2z"/><path d="M3.5 12.2L12 16.4l8.5-4.2"/><path d="M3.5 16.2L12 20.4l8.5-4.2"/>""")
    val user get() = icon("user", """<circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c1.2-3.7 4-5.6 7-5.6s5.8 1.9 7 5.6"/>""")
    val chip get() = icon("chip", """<rect x="6.5" y="6.5" width="11" height="11" rx="2"/><path d="M9.5 3v3.5M14.5 3v3.5M9.5 17.5V21M14.5 17.5V21M3 9.5h3.5M3 14.5h3.5M17.5 9.5H21M17.5 14.5H21"/>""")
    val bolt get() = icon("bolt", """<path d="M13.2 3L5.5 13.4h5.6L10.4 21l8.1-10.6h-5.7z" fill="currentColor" stroke="none"/>""")
    val restart get() = icon("restart", """<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.5 4.5v4h4"/>""")
    val reset get() = icon("reset", """<path d="M9 14.5L4 9.5l5-5"/><path d="M4 9.5h10a6 6 0 0 1 0 12h-3"/>""")
    val pin get() = icon("pin", """<path d="M9.5 3.5h5l-.8 5.2 3.3 3.3v2H7v-2l3.3-3.3z"/><path d="M12 14v6.5"/>""")
    val info get() = icon("info", """<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.2M12 7.7v.2"/>""")
    val warn get() = icon("warn", """<path d="M12 4.2l8.8 15.3H3.2z"/><path d="M12 10v4.2M12 17v.2"/>""")
    val chart get() = icon("chart", """<path d="M4 4v16h16"/><path d="M8.5 16v-4.5M12.5 16V8.5M16.5 16v-6"/>""")
    val patch get() = icon("patch", """<rect x="2.6" y="8.6" width="18.8" height="6.8" rx="3.4" transform="rotate(-45 12 12)"/><path d="M10.6 10.6h.01M13.4 13.4h.01M10.6 13.4h.01M13.4 10.6h.01"/>""")
    val box get() = icon("box", """<path d="M4 7.8L12 4l8 3.8v8.4L12 20l-8-3.8z"/><path d="M4 7.8l8 3.9 8-3.9M12 11.7V20"/>""")
    val save get() = icon("save", """<path d="M5 4.5h10.8L19.5 8v11a.5.5 0 0 1-.5.5H5a.5.5 0 0 1-.5-.5V5a.5.5 0 0 1 .5-.5z"/><path d="M8 4.5v4.5h7V4.5M8 19.5v-6h8v6"/>""")
    val trash get() = icon("trash", """<path d="M4.5 7h15M10 7V4.5h4V7M6.5 7l.9 12.5h9.2L17.5 7"/>""")
    val code get() = icon("code", """<path d="M9 7.5L4.5 12 9 16.5M15 7.5l4.5 4.5-4.5 4.5"/>""")
    val wand get() = icon("wand", """<path d="M5 19L15.5 8.5"/><path d="M14 3.5v3M19.5 9h-3M18 5l-2 2M17.5 13v2.5M20.8 14.2h-2.5"/>""")
    val image get() = icon("image", """<rect x="4" y="4" width="16" height="16" rx="2.2"/><circle cx="9.2" cy="9.4" r="1.6"/><path d="M20 15.5l-4.8-4.8L5 20"/>""")
    val link get() = icon("link", """<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>""")
    val share get() = icon("share", """<circle cx="6.5" cy="12" r="2.5"/><circle cx="17.5" cy="6" r="2.5"/><circle cx="17.5" cy="18" r="2.5"/><path d="M8.7 10.8l6.6-3.6M8.7 13.2l6.6 3.6"/>""")
    val check get() = icon("check", """<path d="M5 12.5l4.5 4.5L19 7.5"/>""")
    val x get() = icon("x", """<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>""")
    val plus get() = icon("plus", """<path d="M12 5v14M5 12h14"/>""")
    val minus get() = icon("minus", """<path d="M5 12h14"/>""")
    val shift get() = icon("shift", """<path d="M12 4.5l7 7.5h-4v7H9v-7H5z"/>""")
    val capsLock get() = icon("capsLock", """<path d="M12 3.5l7 7.5h-4v5H9v-5H5z"/><path d="M9 20.5h6"/>""")
    val disc get() = icon("disc", """<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.3"/>""")
    val zip get() = icon("zip", """<path d="M7 3.5h7l4 4V20a.5.5 0 0 1-.5.5h-11A.5.5 0 0 1 6 20V4a.5.5 0 0 1 .5-.5z"/><path d="M11 6h2M11 9h2M11 12h2M11 15v2.5h2V15z"/>""")
    val sliders get() = icon("sliders", """<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>""")
    val folder get() = icon("folder", """<path d="M3.5 7a1.5 1.5 0 0 1 1.5-1.5h4.2l2 2H19a1.5 1.5 0 0 1 1.5 1.5v8.5A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"/>""")
    val gamepad get() = icon("gamepad", """<path d="M7.5 7.5h9a4.5 4.5 0 0 1 4.4 5.4l-.6 3a2.6 2.6 0 0 1-4.6 1.1L14.2 15H9.8l-1.5 2a2.6 2.6 0 0 1-4.6-1.1l-.6-3A4.5 4.5 0 0 1 7.5 7.5z"/><path d="M8 10.3v3M6.5 11.8h3"/><circle cx="15.3" cy="11" r=".9" fill="currentColor" stroke="none"/><circle cx="17.3" cy="13" r=".9" fill="currentColor" stroke="none"/>""")
    val speaker get() = icon("speaker", """<path d="M4 9.5h3.5L12 5.8v12.4l-4.5-3.7H4z"/><path d="M15.5 9.2a4 4 0 0 1 0 5.6M18 6.7a7.5 7.5 0 0 1 0 10.6"/>""")
    val monitor get() = icon("monitor", """<rect x="3" y="4.5" width="18" height="12" rx="2"/><path d="M8.5 20h7M12 16.5V20"/>""")
    val cpu get() = icon("cpu", """<rect x="7" y="7" width="10" height="10" rx="1.5"/><path d="M10 3.5V7M14 3.5V7M10 17v3.5M14 17v3.5M3.5 10H7M3.5 14H7M17 10h3.5M17 14h3.5"/>""")
    val timeline get() = icon("timeline", """<path d="M5 4v16"/><circle cx="5" cy="7" r="1.6" fill="currentColor" stroke="none"/><circle cx="5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="5" cy="17" r="1.6" fill="currentColor" stroke="none"/><path d="M9 7h10M9 12h7M9 17h9"/>""")
    val download get() = icon("download", """<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/>""")
    val flask get() = icon("flask", """<path d="M9.5 3.5h5M10.5 3.5v6l-5 8.6A1.6 1.6 0 0 0 6.9 20.5h10.2a1.6 1.6 0 0 0 1.4-2.4l-5-8.6v-6"/><path d="M7.8 15h8.4"/>""")
    val shield get() = icon("shield", """<path d="M12 3.5l7 2.8v5.4c0 4.4-3 7.6-7 8.8-4-1.2-7-4.4-7-8.8V6.3z"/><path d="M9 12l2.2 2.2L15.5 10"/>""")
    val wifi get() = icon("wifi", """<path d="M3.5 9.5a12.5 12.5 0 0 1 17 0"/><path d="M6.5 12.8a8 8 0 0 1 11 0"/><path d="M9.5 16a3.6 3.6 0 0 1 5 0"/><circle cx="12" cy="19" r="1.1" fill="currentColor" stroke="none"/>""")
    val phone get() = icon("phone", """<rect x="7" y="3" width="10" height="18" rx="2.2"/><path d="M11 17.5h2"/>""")
    val battery get() = icon("battery", """<rect x="3.5" y="7.5" width="15" height="9" rx="2"/><path d="M21 10.5v3"/><path d="M6.5 10.5v3M9.5 10.5v3M12.5 10.5v3"/>""")
    val thermo get() = icon("thermo", """<path d="M10 4.5a2 2 0 0 1 4 0v9.3a4 4 0 1 1-4 0z"/><path d="M12 9v6.5"/>""")
    val tv get() = icon("tv", """<rect x="3" y="5" width="18" height="12" rx="2"/><path d="M8 20.5h8"/><path d="M9 2.5l3 2.5 3-2.5"/>""")
    val eye get() = icon("eye", """<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>""")
    val eyeOff get() = icon("eyeOff", """<path d="M4 4l16 16"/><path d="M9.9 5.8A9.7 9.7 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.9 3.7M6.4 7.3A17 17 0 0 0 2.5 12S6 18.5 12 18.5a9.5 9.5 0 0 0 4.3-1"/>""")
    val move get() = icon("move", """<path d="M12 3v18M3 12h18"/><path d="M9 5.5L12 3l3 2.5M9 18.5L12 21l3-2.5M5.5 9L3 12l2.5 3M18.5 9L21 12l-2.5 3"/>""")
    val magnet get() = icon("magnet", """<path d="M6 4.5v7a6 6 0 0 0 12 0v-7"/><path d="M6 8h3.5M14.5 8H18"/><path d="M9.5 4.5v7a2.5 2.5 0 0 0 5 0v-7"/>""")
    val refresh get() = icon("refresh", """<path d="M19.5 12a7.5 7.5 0 0 1-12.9 5.2"/><path d="M4.5 12a7.5 7.5 0 0 1 12.9-5.2"/><path d="M17.5 3v4h-4M6.5 21v-4h4"/>""")
    val cloud get() = icon("cloud", """<path d="M7 18.5a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.5 1.6A3.8 3.8 0 0 1 17.5 18.5z"/>""")
    val upload get() = icon("upload", """<path d="M12 20V9M7 13.5l5-5 5 5M5 4h14"/>""")
    val key get() = icon("key", """<circle cx="8" cy="15" r="4"/><path d="M11 12l8.5-8.5M16 7l2.5 2.5M18.5 4.5l2 2"/>""")
    val bug get() = icon("bug", """<rect x="7" y="7.5" width="10" height="12" rx="5"/><path d="M12 7.5V19.5M3.5 13H7M17 13h3.5M5 8.5l2.5 1.5M19 8.5l-2.5 1.5M5 18l2.5-1.5M19 18l-2.5-1.5M9.5 7.5l-1-3M14.5 7.5l1-3"/>""")
    val ab get() = icon("ab", """<rect x="3" y="5" width="8" height="14" rx="2"/><rect x="13" y="5" width="8" height="14" rx="2"/><path d="M5.5 15l1.5-6 1.5 6M6 13h2M15.5 9v6h1.8a1.5 1.5 0 0 0 0-3H15.5h1.5a1.5 1.5 0 0 0 0-3z"/>""")
    val lock get() = icon("lock", """<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>""")
    val palette get() = icon("palette", """<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.4-1.3-1.8-1.3-3s1-2 2.2-2H18a2.5 2.5 0 0 0 2.5-2.5C20.5 7 16.7 3.5 12 3.5z"/><circle cx="7.5" cy="11" r="1.1" fill="currentColor" stroke="none"/><circle cx="10" cy="7.3" r="1.1" fill="currentColor" stroke="none"/><circle cx="14.5" cy="7.3" r="1.1" fill="currentColor" stroke="none"/>""")
    val spark get() = icon("spark", """<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>""")
    val flag get() = icon("flag", """<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>""")
    val pause get() = icon("pause", """<rect x="6.5" y="5" width="3.5" height="14" rx="1"/><rect x="14" y="5" width="3.5" height="14" rx="1"/>""")
    val exit get() = icon("exit", """<path d="M10 4.5H5.5a1 1 0 0 0-1 1v13a1 1 0 0 0 1 1H10"/><path d="M14.5 8l4 4-4 4M18.5 12H9"/>""")
    val mute get() = icon("mute", """<path d="M4 9.5h3.5L12 5.8v12.4l-4.5-3.7H4z"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>""")
    val vibrate get() = icon("vibrate", """<rect x="7.5" y="4" width="9" height="16" rx="2"/><path d="M4 9v6M2 10.5v3M20 9v6M22 10.5v3"/>""")
    val rotate get() = icon("rotate", """<path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v4.5h-4.5"/>""")
    val hand get() = icon("hand", """<path d="M8.5 13V5.8a1.5 1.5 0 0 1 3 0V11M11.5 10.5V4.3a1.5 1.5 0 0 1 3 0V11M14.5 11V5.8a1.5 1.5 0 0 1 3 0v7.7a7 7 0 0 1-7 7h-.6a6 6 0 0 1-4.6-2.2L3.6 15.4a1.5 1.5 0 0 1 2.2-2l2.7 2.6"/>""")
    val split get() = icon("split", """<rect x="4" y="3.5" width="16" height="17" rx="2"/><path d="M4 12h16"/>""")
    val globe get() = icon("globe", """<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.7 2.6 2.7 14.4 0 17M12 3.5c-2.7 2.6-2.7 14.4 0 17"/>""")
    val text get() = icon("text", """<path d="M4 7V5h11v2M9.5 5v14M7.5 19h4"/><path d="M14 12v-1.5h7V12M17.5 10.5V19M16 19h3"/>""")
    val checkCircle get() = icon("checkC", """<circle cx="12" cy="12" r="8.5"/><path d="M8 12.3l2.7 2.7L16 9.6"/>""")
    val xCircle get() = icon("xC", """<circle cx="12" cy="12" r="8.5"/><path d="M9 9l6 6M15 9l-6 6"/>""")
    val alert get() = icon("alert", """<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v5.5M12 16.3v.2"/>""")
    val hud get() = icon("hud", """<rect x="3" y="4.5" width="18" height="15" rx="2"/><path d="M6.5 8.5h4M6.5 11.5h2.5"/><path d="M13 15.5l2-3 2 2 2-4"/>""")
    val scene get() = icon("scene", """<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 5v14M3 9h4M3 13h4M3 17h4"/>""")
    val sd get() = icon("sd", """<path d="M7 3.5h7.5L18 7v12.5a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z"/><path d="M10 3.5v3.5M12.5 3.5v3.5M15 4v3"/>""")
    val inbox get() = icon("inbox", """<path d="M3.5 13.5l2.6-7.2A1.5 1.5 0 0 1 7.5 5.3h9a1.5 1.5 0 0 1 1.4 1l2.6 7.2v4.7a1.5 1.5 0 0 1-1.5 1.5h-14A1.5 1.5 0 0 1 3.5 18.2z"/><path d="M3.5 13.5h4.5l1.5 2.5h5l1.5-2.5h4.5"/>""")
    val keyboard get() = icon("keyboard", """<rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M6 9.5h.01M9 9.5h.01M12 9.5h.01M15 9.5h.01M18 9.5h.01M6 12.5h.01M9 12.5h.01M12 12.5h.01M15 12.5h.01M18 12.5h.01M8 15.5h8"/>""")
    val message get() = icon("message", """<path d="M4 5.5h16a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1H9.5L5 20.5V17H4a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z"/><path d="M7.5 10h9M7.5 13h6"/>""")
    val menu get() = icon("menu", """<path d="M4 7h16M4 12h16M4 17h16"/>""")
    val dpad get() = icon("dpad", """<path d="M9.5 3.5h5v6h6v5h-6v6h-5v-6h-6v-5h6z"/>""")
}
