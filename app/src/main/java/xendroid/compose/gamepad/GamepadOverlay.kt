package xendroid.compose.gamepad

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** Pure layout math: control center in px for the given surface size. */
fun controlCenterPx(c: OnScreenControl, size: IntSize): Offset =
    Offset(c.xFraction * size.width, c.yFraction * size.height)

/** Pure layout math: hit radius in px. baseSizeDp is a diameter, so radius = dp/2 * scale.
 *  Sticks/dpad get a slightly larger grab radius (1.15x). */
fun controlRadiusPx(c: OnScreenControl, density: Density): Float {
    val base = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    return when (c) {
        is OnScreenControl.AnalogStick, is OnScreenControl.Dpad -> base * 1.15f
        else -> base
    }
}

/** Pure hit-test: first visible control whose center is within its radius of pos; the editor
 *  also finds the hidden ones ([includeHidden]), which it still draws. */
fun hitTest(
    controls: List<OnScreenControl>, pos: Offset, size: IntSize, density: Density, includeHidden: Boolean = false,
): OnScreenControl? = controls.firstOrNull { c ->
    if (!c.visible && !includeHidden) return@firstOrNull false
    val center = controlCenterPx(c, size)
    hypot(pos.x - center.x, pos.y - center.y) <= controlRadiusPx(c, density)
}

@Composable
fun GamepadOverlay(
    controls: List<OnScreenControl>,
    opacity: Float,
    onKeyEvent: (Int, Boolean, Int) -> Unit,
    modifier: Modifier = Modifier,
    contrast: () -> Float = { 0f },        // 0 = dark, 1 = white scene: tints controls grey. A lambda
                                           // so the draw phase reads it: redraws without recomposing.
    onUserInteraction: () -> Unit = {},   // resets auto-hide timer
    editMode: Boolean = false,
    adaptiveSticks: Boolean = false,      // opt-in: spawn near the saved stick anchor at touch-down
    touchCamera: Boolean = false,         // U07 opt-in: free right side of the screen = right stick by finger speed
    cameraSensitivity: Float = 1f,        // U07: 0.5–2, from the layout's globals
    cameraAreaStart: Float = TouchCamera.DEFAULT_AREA_START,  // U07: where the area starts (fraction of the width)
    slideButtons: Boolean = false,        // 15f: slide from button to button / onto the d-pad
    slideSticks: Boolean = false,         // 15f: a free finger sliding onto a stick takes it
    gridStepsX: Int = 0,                  // editor: snap-grid cell count per axis (0 = no grid).
    gridStepsY: Int = 0,                  // x/y differ so the cells are square on a non-1:1 screen.
    selectedId: ControlId? = null,
    onSelect: (ControlId?) -> Unit = {},
    onTranslate: (ControlId, dxFrac: Float, dyFrac: Float) -> Unit = { _, _, _ -> },
    onScale: (ControlId, factor: Float) -> Unit = { _, _ -> },
    onDragEnd: (ControlId) -> Unit = {},
    /** Round 2: dark glass buttons (modern, the default) or the coloured ones (classic). */
    style: ControlStyle = ControlStyle.MODERN,
) {
    // Create the emitter ONCE. The host's onKeyEvent lambda is unstable (captures the
    // Activity), so remember(onKeyEvent) would recreate the emitter on every touch (poke
    // -> tick++ -> recomposition) -- which, with the dispose-release below, would fire
    // releaseAll() on EVERY touch and make the pad feel dead. rememberUpdatedState keeps
    // the sink current without churning the emitter.
    val latestOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val emitter = remember { GamepadEmitter { code, pressed, value -> latestOnKeyEvent(code, pressed, value) } }
    // Observable (mutableStateMapOf) so the Canvas re-draws the stick knob on every move/
    // claim change -- plain maps record no snapshot read, so the knob would sit frozen.
    val claims = remember { mutableStateMapOf<Long, ControlId>() }
    val pointerPos = remember { mutableStateMapOf<Long, Offset>() }
    val stickOrigins = remember { mutableStateMapOf<ControlId, Offset>() }
    // Rebuilding these per draw pass (display-refresh rate during a drag) was a GC-pause stutter storm.
    val drawCache = remember { GamepadDrawCache() }

    // per-dpad last-pressed sector set, for diffing.
    val dpadState = remember { mutableMapOf<ControlId, Set<Int>>() }
    val density = LocalDensity.current
    // U07: full deflection at 1.2 dp per ms of finger travel (a brisk swipe), over the sensitivity.
    val camera = remember(density, cameraSensitivity) {
        TouchCamera(fullSpeedPxPerMs = TouchCamera.fullSpeed(with(density) { 1.2.dp.toPx() }, cameraSensitivity))
    }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val slide = remember(slideButtons, slideSticks) { TouchSlide(slideButtons, slideSticks) }
    // Latest controls WITHOUT restarting the pointerInput: in edit mode every drag frame
    // produces a new `controls` list; if it keyed the pointerInput, the gesture would cancel
    // mid-drag (the button moves one frame then stutters/stops). Read this inside instead.
    val controlsState = rememberUpdatedState(controls)
    // Edit callbacks must stay CURRENT for the long-lived pointer coroutine: pointerInput is
    // keyed on (editMode, sizePx) and is NOT relaunched per drag frame, so a raw capture would
    // freeze them to the gesture-start composition (the dragged control would only ever move by
    // one frame's delta from its origin). Mirror the onKeyEvent pattern.
    val onSelectState = rememberUpdatedState(onSelect)
    val onTranslateState = rememberUpdatedState(onTranslate)
    val onScaleState = rememberUpdatedState(onScale)
    val onDragEndState = rememberUpdatedState(onDragEnd)

    // On teardown (overlay leaves composition: emulator exit / booted->false), release
    // every code so a control held at dispose can't stick. Keyed on Unit so it fires ONLY
    // at real disposal -- NOT on every recomposition (which would release mid-press).
    DisposableEffect(Unit) { onDispose { emitter.releaseAll() } }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
            // Keyed only on editMode+sizePx (stable during a gesture). controls is read live
            // via controlsState so a drag (which mutates controls every frame) never restarts
            // the gesture. selectedId is not needed here (only the draw uses it).
            .pointerInput(editMode, sizePx, adaptiveSticks, touchCamera, camera, cameraAreaStart, slide) {
                if (editMode) {
                    editPointerLoop(
                        controlsState, sizePx, density,
                        { id -> onSelectState.value(id) },
                        { id, dx, dy -> onTranslateState.value(id, dx, dy) },
                        { id, f -> onScaleState.value(id, f) },
                        { id -> onDragEndState.value(id) },
                    )
                } else {
                    try {
                    awaitPointerEventScope {
                        while (true) {
                            // While the camera finger is down, a quiet moment means it rests: stop turning.
                            val ev = if (camera.active) withTimeoutOrNull(camera.idleMs) { awaitPointerEvent() } else awaitPointerEvent()
                            if (ev == null) {
                                camera.idle(android.os.SystemClock.uptimeMillis())?.let { emitter.stick(false, it.x, it.y) }
                                continue
                            }
                            onUserInteraction()
                            for (ch in ev.changes) {
                                val pid = ch.id.value
                                when {
                                    ch.changedToDownIgnoreConsumed() -> {
                                        val layout = controlsState.value
                                        val hit = if (adaptiveSticks) {
                                            // Buttons and D-pad win over a stick's expanded grab zone.
                                            hitTest(layout.filterNot { it is OnScreenControl.AnalogStick },
                                                ch.position, sizePx, density)
                                                ?: layout.firstOrNull { c ->
                                                    c is OnScreenControl.AnalogStick && c.visible &&
                                                        !claims.containsValue(c.id) &&
                                                        hypot(ch.position.x - controlCenterPx(c, sizePx).x,
                                                            ch.position.y - controlCenterPx(c, sizePx).y) <=
                                                        controlRadiusPx(c, density) * 1.65f
                                                }
                                        } else hitTest(layout, ch.position, sizePx, density)
                                        // The on-screen right stick wins over the camera area.
                                        if (hit is OnScreenControl.AnalogStick && !hit.isLeft && camera.active) camera.reset()
                                        if (hit == null && touchCamera && TouchCamera.inArea(ch.position.x, sizePx.width, cameraAreaStart) &&
                                            claims.values.none { id -> layout.any { it.id == id && it is OnScreenControl.AnalogStick && !it.isLeft } } &&
                                            camera.down(pid, ch.position.x, ch.position.y, ch.uptimeMillis)
                                        ) {
                                            ch.consume()
                                        }
                                        if (hit != null) {
                                            if (adaptiveSticks && hit is OnScreenControl.AnalogStick) {
                                                val r = with(density) { hit.baseSizeDp.dp.toPx() } / 2f * hit.scale
                                                stickOrigins[hit.id] = Offset(
                                                    ch.position.x.coerceIn(minOf(r, sizePx.width / 2f), maxOf(sizePx.width - r, sizePx.width / 2f)),
                                                    ch.position.y.coerceIn(minOf(r, sizePx.height / 2f), maxOf(sizePx.height - r, sizePx.height / 2f)),
                                                )
                                            }
                                            claims[pid] = hit.id
                                            pointerPos[pid] = ch.position
                                            dispatchDown(emitter, hit, ch.position, sizePx, density, dpadState, stickOrigins[hit.id])
                                            ch.consume()
                                        }
                                    }
                                    // up OR cancellation (Home/focus-loss sends ACTION_CANCEL,
                                    // which is !pressed but not changedToUp) -> release the claim.
                                    !ch.pressed && camera.up(pid) != null -> {
                                        emitter.releaseStick(false)
                                        ch.consume()
                                    }
                                    !ch.pressed -> {
                                        pointerPos.remove(pid)
                                        claims.remove(pid)?.let { id ->
                                            // Only release if NO other finger still holds this
                                            // control (two fingers on one stick: lifting one
                                            // must not zero the input the other is still driving).
                                            if (claims.none { it.value == id }) {
                                                stickOrigins.remove(id)
                                                controlsState.value.firstOrNull { it.id == id }?.let {
                                                    dispatchUp(emitter, it, dpadState)
                                                }
                                            }
                                            ch.consume()
                                        }
                                    }
                                    ch.pressed && camera.active && claims[pid] == null -> {
                                        camera.move(pid, ch.position.x, ch.position.y, ch.uptimeMillis)?.let { d ->
                                            emitter.stick(false, d.x, d.y)
                                            ch.consume()
                                        }
                                    }
                                    ch.pressed -> {            // MOVE
                                        val layout = controlsState.value
                                        val id = claims[pid]
                                        if (id == null) {
                                            // 15f: a free finger sliding onto a control presses it.
                                            if (!slide.any) continue
                                            val target = TouchSlide.target(layout, ch.position, sizePx, density, slide,
                                                leaving = null, held = claims.values) ?: continue
                                            claims[pid] = target.id
                                            pointerPos[pid] = ch.position
                                            dispatchDown(emitter, target, ch.position, sizePx, density, dpadState, stickOrigins[target.id])
                                            ch.consume()
                                            continue
                                        }
                                        pointerPos[pid] = ch.position
                                        val current = layout.firstOrNull { it.id == id } ?: continue
                                        if ((slide.releasesOnExit(current) || slide.handsOver(current)) &&
                                            TouchSlide.hasLeft(current, ch.position, sizePx, density)) {
                                            // 15f: off a button (or off the d-pad onto another control):
                                            // let go of it, and press what the finger is on now.
                                            val target = TouchSlide.target(layout, ch.position, sizePx, density, slide,
                                                leaving = id, held = claims.filterKeys { it != pid }.values)
                                            if (target != null || slide.releasesOnExit(current)) {
                                                claims.remove(pid)
                                                if (claims.none { it.value == id }) {
                                                    stickOrigins.remove(id)
                                                    dispatchUp(emitter, current, dpadState)
                                                }
                                                if (target != null) {
                                                    claims[pid] = target.id
                                                    dispatchDown(emitter, target, ch.position, sizePx, density, dpadState, stickOrigins[target.id])
                                                } else {
                                                    pointerPos.remove(pid)
                                                }
                                                ch.consume()
                                                continue
                                            }
                                        }
                                        dispatchMove(emitter, current, ch.position, sizePx, density, dpadState, stickOrigins[id])
                                        ch.consume()
                                    }
                                }
                            }
                        }
                    }
                    } finally {
                        // Resizing, switching mode or cancelling without a pointer-up must
                        // release every claim and axis, including adaptive stick origins.
                        emitter.releaseAll()
                        claims.clear()
                        pointerPos.clear()
                        stickOrigins.clear()
                        dpadState.clear()
                        camera.reset()
                    }
                }
            }
    ) {
        // Auto-hidden (alpha animated to 0): emit NO draw ops. The overlay stays MOUNTED
        // (pointerInput above keeps catching the wake tap / held-release), but the Canvas
        // must not record a full-screen layer of alpha-0 shapes -- that keeps the host
        // ComposeView window content-bearing, so SurfaceFlinger GPU-composites it over the
        // game SurfaceView EVERY refresh, forever. That is the "slight, constant" stutter:
        // legacy parity was the empty host window (its gamepad was a separate SurfaceView).
        // Skipping draws here leaves the window transparent -> the present is punched
        // straight through to the game layer, no per-frame blend.
        if (opacity <= 0.01f) return@Canvas
        // Editor snap grid: faint lines so "Snap" visibly aligns controls to a grid. The caller
        // passes per-axis cell counts (computed from the actual screen size) so the cells are
        // SQUARE rather than stretched. Drawn behind the controls; edit mode only.
        if (editMode && gridStepsX > 0 && gridStepsY > 0 && sizePx.width > 0 && sizePx.height > 0) {
            val grid = Color.White.copy(alpha = 0.16f)
            for (i in 1 until gridStepsX) {
                val x = sizePx.width.toFloat() * i / gridStepsX
                drawLine(grid, Offset(x, 0f), Offset(x, sizePx.height.toFloat()), strokeWidth = 1f)
            }
            for (j in 1 until gridStepsY) {
                val y = sizePx.height.toFloat() * j / gridStepsY
                drawLine(grid, Offset(0f, y), Offset(sizePx.width.toFloat(), y), strokeWidth = 1f)
            }
        }
        // Reverse lookup: claimed control id -> active pointer position (for stick knob).
        val activePos: (ControlId) -> Offset? = { id ->
            claims.entries.lastOrNull { it.value == id }?.key?.let { pointerPos[it] }
        }
        val contrastNow = contrast()   // draw-phase read: animation frames only re-draw
        for (c in controls) {
            // The editor still shows a hidden control, faint and ringed, so it can be shown again.
            if (!c.visible && !editMode) continue
            if (adaptiveSticks && !editMode && c is OnScreenControl.AnalogStick && stickOrigins[c.id] == null) continue
            if (!c.visible) drawHiddenMark(c, sizePx, density)
            drawControl(
                c, if (c.visible) opacity else opacity * 0.3f, contrastNow, sizePx, density, drawCache,
                pressed = claims.containsValue(c.id),
                dpadDirs = if (c is OnScreenControl.Dpad) dpadState[c.id] ?: emptySet() else emptySet(),
                activePos = activePos(c.id),
                centerOverride = stickOrigins[c.id],
                style = style,
            )
            if (editMode && c.id == selectedId) drawSelection(c, sizePx, density)
        }
    }
}

/** Edit-mode pointer loop: DOWN selects + claims; single-pointer drag translates the
 *  claimed control; two-pointer pinch scales the selected control by span ratio.
 *  onDragEnd fires once when a single-finger drag is released, so the editor can snap
 *  the final resting position to the grid (snap is NOT applied per frame -- that would
 *  round every sub-half-step delta back to the same cell and freeze the control). */
private suspend fun PointerInputScope.editPointerLoop(
    controlsState: State<List<OnScreenControl>>,
    size: IntSize,
    density: Density,
    onSelect: (ControlId?) -> Unit,
    onTranslate: (ControlId, Float, Float) -> Unit,
    onScale: (ControlId, Float) -> Unit,
    onDragEnd: (ControlId) -> Unit,
) {
    var dragId: ControlId? = null
    var lastDrag: Offset? = null
    var lastSpan: Float? = null
    var didDrag = false                         // a single-finger translate actually happened
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.filter { it.pressed }
            // DOWN: hit-test + select + start a drag on the hit control.
            ev.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { ch ->
                val hit = hitTest(controlsState.value, ch.position, size, density, includeHidden = true)
                onSelect(hit?.id)
                dragId = hit?.id
                lastDrag = if (hit != null) ch.position else null
                lastSpan = null
                didDrag = false
                if (hit != null) ch.consume()
            }
            when {
                pressed.size >= 2 && dragId != null -> {
                    // Pinch: scale the selected control by the span ratio. A pinch ends the
                    // single-finger drag phase WITHOUT committing a snap (no position change).
                    val a = pressed[0].position; val b = pressed[1].position
                    val span = hypot(a.x - b.x, a.y - b.y)
                    val prev = lastSpan
                    if (prev != null && prev > 0f) onScale(dragId!!, span / prev)
                    lastSpan = span
                    lastDrag = null
                    didDrag = false
                    pressed.forEach { it.consume() }
                }
                pressed.size == 1 && dragId != null -> {
                    val ch = pressed[0]
                    val prev = lastDrag
                    if (prev != null && size.width > 0 && size.height > 0) {
                        val dx = (ch.position.x - prev.x) / size.width
                        val dy = (ch.position.y - prev.y) / size.height
                        if (dx != 0f || dy != 0f) { onTranslate(dragId!!, dx, dy); didDrag = true }
                    }
                    lastDrag = ch.position
                    lastSpan = null
                    ch.consume()
                }
                pressed.isEmpty() -> {
                    // Pointer(s) lifted: commit the grid-snap of the final position (once).
                    val ended = dragId
                    if (ended != null && didDrag) onDragEnd(ended)
                    dragId = null; lastDrag = null; lastSpan = null; didDrag = false
                }
            }
        }
    }
}

// ---- Gameplay dispatch (down/move/up) ----

private fun dispatchDown(
    emitter: GamepadEmitter, c: OnScreenControl, pos: Offset,
    size: IntSize, density: Density, dpadState: MutableMap<ControlId, Set<Int>>,
    stickOrigin: Offset? = null,
) {
    when (c) {
        is OnScreenControl.Button -> emitter.pressDigital(c.keyCode)
        is OnScreenControl.Dpad -> updateDpad(emitter, c, pos, size, density, dpadState)
        is OnScreenControl.AnalogStick -> updateStick(emitter, c, pos, size, density, stickOrigin)
    }
}

private fun dispatchMove(
    emitter: GamepadEmitter, c: OnScreenControl, pos: Offset,
    size: IntSize, density: Density, dpadState: MutableMap<ControlId, Set<Int>>,
    stickOrigin: Offset? = null,
) {
    when (c) {
        is OnScreenControl.Button -> Unit                 // sticky press while held
        is OnScreenControl.Dpad -> updateDpad(emitter, c, pos, size, density, dpadState)
        is OnScreenControl.AnalogStick -> updateStick(emitter, c, pos, size, density, stickOrigin)
    }
}

private fun dispatchUp(
    emitter: GamepadEmitter, c: OnScreenControl, dpadState: MutableMap<ControlId, Set<Int>>,
) {
    when (c) {
        is OnScreenControl.Button -> emitter.releaseDigital(c.keyCode)
        is OnScreenControl.Dpad -> {
            emitter.applyDpad(dpadState[c.id] ?: emptySet(), emptySet())
            emitter.releaseAllDpad()
            dpadState[c.id] = emptySet()
        }
        is OnScreenControl.AnalogStick -> emitter.releaseStick(c.isLeft)
    }
}

private fun updateDpad(
    emitter: GamepadEmitter, c: OnScreenControl.Dpad, pos: Offset,
    size: IntSize, density: Density, dpadState: MutableMap<ControlId, Set<Int>>,
) {
    val center = controlCenterPx(c, size)
    // Normalize the touch offset to [-1,1] across the pad (half-extent = the visual radius),
    // then the 3x3 grid decides the direction (legacy "press the arm of the cross").
    val radius = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    val nx = (pos.x - center.x) / radius
    val ny = (pos.y - center.y) / radius
    val now = emitter.dpadSectors(nx, ny, c.deadZone)
    emitter.applyDpad(dpadState[c.id] ?: emptySet(), now)
    dpadState[c.id] = now
}

private fun updateStick(
    emitter: GamepadEmitter, c: OnScreenControl.AnalogStick, pos: Offset,
    size: IntSize, density: Density,
    origin: Offset? = null,
) {
    val center = origin ?: controlCenterPx(c, size)
    // Normalize by the VISUAL ring (matches drawControl), NOT the 1.15x grab radius, so
    // full deflection == reaching the drawn ring (and the knob == the emitted value).
    val radius = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    val dxN = (pos.x - center.x) / radius
    val dyN = (pos.y - center.y) / radius
    // No source dead-zone by default (PPSSPP/legacy have none -- the emulator owns thumbstick
    // dead-zone; a second one here double-dead-zones). 15f: a stick may have one of its own,
    // which the player sets in the editor. Release happens only on touch-up.
    val (x, y) = TouchSlide.stickDeadZone(dxN, dyN, c.deadZone)
    emitter.stick(c.isLeft, x, y)            // emitter circular-clamps
}

// ---- Drawing ----

// Xbox face-button colours (A green, B red, X blue, Y yellow).
private val XBOX_GREEN = Color(0xFF5EAE3A)
private val XBOX_RED = Color(0xFFC23B3B)
private val XBOX_BLUE = Color(0xFF3E78C2)
private val XBOX_YELLOW = Color(0xFFD8A21E)
private val PILL_IDS = setOf(ControlId.LB, ControlId.RB)
private val TRIGGER_IDS = setOf(ControlId.LT, ControlId.RT)

// Optical (not geometric) centering, as a fraction of the button radius; + = right.
private const val ABXY_LABEL_NUDGE_X = 0.02f

// Bright-scene ink: white-on-white vanishes, so controls lerp from white toward this grey.
private val OVERLAY_INK_BRIGHT = Color(0xFF8A8A8A)

/** Cache of draw objects invariant per (control, layout). Brushes are built at full alpha and
 *  modulated by the draw call's alpha (mathematically identical). Geometry changes only in the
 *  editor (drag/pinch), where the size-capped clear()s bound the maps. */
private class GamepadDrawCache {
    class Label(val paint: Paint, val cx: Float, val cy: Float, val opticalDx: Float)
    private data class LabelKey(val text: String, val r: Int, val bold: Boolean, val fill: Int)
    private val labels = HashMap<LabelKey, Label>()
    fun label(text: String, radius: Float, bold: Boolean, fill: Float?): Label {
        if (labels.size > 64) labels.clear()
        return labels.getOrPut(
            LabelKey(text, radius.toRawBits(), bold, (fill ?: -1f).toRawBits())
        ) {
            val paint = Paint().apply {
                isAntiAlias = true
                isFakeBoldText = bold
                color = android.graphics.Color.WHITE   // RGB fixed; alpha set per draw
                textAlign = if (fill != null) Paint.Align.CENTER else Paint.Align.LEFT
                textSize = radius * 0.7f
            }
            val bounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            if (fill != null) {                                  // size the glyph's extent to fill * button
                val diag = hypot(bounds.width().toFloat(), bounds.height().toFloat())
                if (diag > 0) {
                    paint.textSize = paint.textSize * (fill * 2f * radius) / diag
                    paint.getTextBounds(text, 0, text.length, bounds)
                }
            }
            if (bold) {                                          // extra weight for the face-button letters
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = paint.textSize * 0.05f
            }
            val opticalDx = when (text) {
                "◀" -> -bounds.width() / 6f
                "▶" -> bounds.width() / 6f
                else -> 0f
            }
            Label(paint, bounds.exactCenterX(), bounds.exactCenterY(), opticalDx)
        }
    }

    class Geom(center: Offset, radius: Float) {
        val clipOval: Path = Path().apply { addOval(Rect(center, radius)) }
        val cross: Path by lazy(LazyThreadSafetyMode.NONE) {
            dpadCrossPath(center, radius * 1.2f, radius * 0.324f)
        }
        // Indexed by Kc.DPAD_LEFT/UP/RIGHT/DOWN (0..3).
        val armClips: Array<Path> by lazy(LazyThreadSafetyMode.NONE) {
            val b = radius * 2f
            val tl = Offset(center.x - b, center.y - b); val tr = Offset(center.x + b, center.y - b)
            val bl = Offset(center.x - b, center.y + b); val br = Offset(center.x + b, center.y + b)
            fun wedge(p1: Offset, p2: Offset) = Path().apply {
                moveTo(center.x, center.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close()
            }
            arrayOf(wedge(tl, bl), wedge(tl, tr), wedge(tr, br), wedge(bl, br))
        }
    }
    private data class GeomKey(val x: Int, val y: Int, val r: Int)
    private val geoms = HashMap<GeomKey, Geom>()
    fun geom(center: Offset, radius: Float): Geom {
        if (geoms.size > 64) geoms.clear()
        return geoms.getOrPut(
            GeomKey(center.x.toRawBits(), center.y.toRawBits(), radius.toRawBits())
        ) { Geom(center, radius) }
    }

    private data class BrushKey(val kind: Int, val color: Int, val x: Int, val y: Int, val r: Int)
    private val brushes = HashMap<BrushKey, Brush>()
    private inline fun brush(key: BrushKey, build: () -> Brush): Brush {
        if (brushes.size > 128) brushes.clear()
        return brushes.getOrPut(key) { build() }
    }
    /** Face-button convex base at full alpha; draw with alpha = pressed/opacity factor. */
    fun faceBrush(face: Color, center: Offset, radius: Float): Brush = brush(
        BrushKey(0, face.toArgb(), center.x.toRawBits(), center.y.toRawBits(), radius.toRawBits())
    ) {
        Brush.radialGradient(
            colors = listOf(
                lerp(face, Color.White, 0.35f), face, lerp(face, Color.Black, 0.28f),
            ),
            center = Offset(center.x, center.y - radius * 0.35f),
            radius = radius * 1.35f,
        )
    }
    /** Glossy specular at base alpha 0.5 -> 0; draw with alpha = opacity. */
    fun specularBrush(ink: Color, center: Offset, radius: Float): Brush = brush(
        BrushKey(1, ink.toArgb(), center.x.toRawBits(), center.y.toRawBits(), radius.toRawBits())
    ) {
        Brush.verticalGradient(
            colors = listOf(ink.copy(alpha = 0.5f), ink.copy(alpha = 0f)),
            startY = center.y - radius * 0.78f, endY = center.y - radius * 0.02f,
        )
    }
    /** D-pad pressed-arm highlight at base alpha 0.85; draw with alpha = opacity. */
    fun armBrush(ink: Color, center: Offset, radius: Float): Brush = brush(
        BrushKey(2, ink.toArgb(), center.x.toRawBits(), center.y.toRawBits(), radius.toRawBits())
    ) {
        Brush.radialGradient(
            colors = listOf(Color.Transparent, ink.copy(alpha = 0.85f)),
            center = center, radius = radius,
        )
    }

    private val strokes = HashMap<Int, Stroke>()
    fun stroke(w: Float): Stroke = strokes.getOrPut(w.toRawBits()) { Stroke(w) }
}

private fun DrawScope.drawControl(
    c: OnScreenControl, opacity: Float, contrast: Float, size: IntSize, density: Density,
    cache: GamepadDrawCache,
    pressed: Boolean, dpadDirs: Set<Int>, activePos: Offset?,
    centerOverride: Offset? = null,
    style: ControlStyle = ControlStyle.CLASSIC,
) {
    val center = centerOverride ?: controlCenterPx(c, size)
    val radius = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    val strokeW = with(density) { 2.dp.toPx() }
    if (style == ControlStyle.MODERN) {
        val rim = with(density) { 1.5.dp.toPx() }
        when (c) {
            is OnScreenControl.Button -> drawGlassButton(c, center, radius, rim, opacity, pressed, cache)
            is OnScreenControl.Dpad -> drawGlassDpad(center, radius, rim, opacity, dpadDirs, cache)
            is OnScreenControl.AnalogStick -> drawGlassStick(center, radius, rim, opacity, activePos)
        }
        return
    }
    // Coloured face buttons keep their face colour; only their light accents follow the ink.
    val ink = lerp(Color.White, OVERLAY_INK_BRIGHT, contrast)
    when (c) {
        is OnScreenControl.Button -> drawButton(c, center, radius, strokeW, opacity, pressed, ink, cache)
        is OnScreenControl.Dpad -> drawDpad(center, radius, strokeW, opacity, dpadDirs, ink, cache)
        is OnScreenControl.AnalogStick -> drawStick(center, radius, strokeW, opacity, activePos, ink, cache)
    }
}

private fun DrawScope.drawButton(
    c: OnScreenControl.Button, center: Offset, radius: Float, strokeW: Float,
    opacity: Float, pressed: Boolean, ink: Color, cache: GamepadDrawCache,
) {
    fun white(a: Float) = ink.copy(alpha = a * opacity)
    val face = when (c.id) {
        ControlId.A -> XBOX_GREEN; ControlId.B -> XBOX_RED
        ControlId.X -> XBOX_BLUE; ControlId.Y -> XBOX_YELLOW
        else -> null
    }
    when {
        face != null -> {                                   // glossy convex colour face button + letter
            val a = (if (pressed) 0.9f else 0.68f) * opacity
            // Convex base: lit from above so the disc reads domed.
            drawCircle(
                brush = cache.faceBrush(face, center, radius),
                radius = radius, center = center, alpha = a,
            )
            clipPath(cache.geom(center, radius).clipOval) {
                val gTop = center.y - radius * 0.78f
                val gBot = center.y - radius * 0.02f
                drawOval(
                    brush = cache.specularBrush(ink, center, radius),
                    topLeft = Offset(center.x - radius * 0.6f, gTop),
                    size = Size(radius * 1.2f, gBot - gTop),
                    alpha = opacity,
                )
            }
            drawCircle(white(0.55f), radius, center, style = cache.stroke(strokeW))
            if (pressed) drawCircle(white(0.9f), radius + strokeW, center, style = cache.stroke(strokeW))
            drawLabel(cache, c.label, center, radius, white(0.95f), bold = true, fill = 0.68f)
        }
        c.id in TRIGGER_IDS -> {                            // trigger: ~2x-long, thicker bar with softened corners
            val w = radius * 4.2f; val h = radius * 1.625f
            val tl = Offset(center.x - w / 2f, center.y - h / 2f)
            val cr = CornerRadius(radius * 0.42f, radius * 0.42f)
            drawRoundRect(white(if (pressed) 0.42f else 0.18f), tl, Size(w, h), cr)
            drawRoundRect(white(0.5f), tl, Size(w, h), cr, style = cache.stroke(strokeW))
            drawLabel(cache, c.label, center, radius, white(0.85f))
        }
        c.id in PILL_IDS -> {                               // bumper: straight bar with softened corners
            val w = radius * 3.15f; val h = radius * 1.15f
            val tl = Offset(center.x - w / 2f, center.y - h / 2f)
            val cr = CornerRadius(radius * 0.42f, radius * 0.42f)
            drawRoundRect(white(if (pressed) 0.42f else 0.18f), tl, Size(w, h), cr)
            drawRoundRect(white(0.5f), tl, Size(w, h), cr, style = cache.stroke(strokeW))
            drawLabel(cache, c.label, center, radius, white(0.85f))
        }
        else -> {                                           // L3/R3/Back/Start: small circle
            val rr = radius * 0.82f
            drawCircle(white(if (pressed) 0.42f else 0.16f), rr, center)
            drawCircle(white(0.5f), rr, center, style = cache.stroke(strokeW))
            drawLabel(cache, c.label, center, rr, white(0.8f))
        }
    }
}

/** Xbox 360-style d-pad: a round disc with a raised plus/cross; the pressed arm lights up. */
private fun DrawScope.drawDpad(
    center: Offset, radius: Float, strokeW: Float, opacity: Float, dirs: Set<Int>, ink: Color,
    cache: GamepadDrawCache,
) {
    val g = cache.geom(center, radius)
    drawCircle(ink.copy(alpha = 0.08f * opacity), radius, center)                          // disc
    clipPath(g.clipOval) {                                                                 // cut by the disc
        drawPath(g.cross, ink.copy(alpha = 0.16f * opacity))                              // cross fill
        drawPath(g.cross, ink.copy(alpha = 0.5f * opacity), style = cache.stroke(strokeW))// cross outline
        // Pressed-arm highlight: the cross clipped to that arm's wedge, tapering to the center.
        if (dirs.isNotEmpty()) {
            val hiBrush = cache.armBrush(ink, center, radius)
            for (code in dirs) {
                val clip = g.armClips.getOrNull(code) ?: continue
                clipPath(clip) { drawPath(g.cross, brush = hiBrush, alpha = opacity) }
            }
        }
    }
    drawCircle(ink.copy(alpha = 0.40f * opacity), radius, center, style = cache.stroke(strokeW)) // ring on top
}

/** A plus/cross outline with concave armpits (Xbox 360 look): arms extend |reach|
 *  from |center| with half-width |hw| at the base, tapering to |t| at the tip so the
 *  armpits open ~100deg (not a square 90deg plus); |f| is the inner-corner curve radius. */
private fun dpadCrossPath(center: Offset, reach: Float, hw: Float): Path {
    val cx = center.x; val cy = center.y
    val f = hw * 1.1f
    val t = hw * 0.8f
    return Path().apply {
        moveTo(cx - t, cy - reach); lineTo(cx + t, cy - reach)        // up arm
        lineTo(cx + hw, cy - hw - f); quadraticBezierTo(cx + hw, cy - hw, cx + hw + f, cy - hw)
        lineTo(cx + reach, cy - t); lineTo(cx + reach, cy + t)        // right arm
        lineTo(cx + hw + f, cy + hw); quadraticBezierTo(cx + hw, cy + hw, cx + hw, cy + hw + f)
        lineTo(cx + t, cy + reach); lineTo(cx - t, cy + reach)        // down arm
        lineTo(cx - hw, cy + hw + f); quadraticBezierTo(cx - hw, cy + hw, cx - hw - f, cy + hw)
        lineTo(cx - reach, cy + t); lineTo(cx - reach, cy - t)        // left arm
        lineTo(cx - hw - f, cy - hw); quadraticBezierTo(cx - hw, cy - hw, cx - hw, cy - hw - f)
        close()
    }
}

private fun DrawScope.drawStick(
    center: Offset, radius: Float, strokeW: Float, opacity: Float, activePos: Offset?, ink: Color,
    cache: GamepadDrawCache,
) {
    drawCircle(ink.copy(alpha = 0.10f * opacity), radius, center)                          // dish
    drawCircle(ink.copy(alpha = 0.45f * opacity), radius, center, style = cache.stroke(strokeW)) // range ring
    val knob = activePos?.let { p ->
        var dx = p.x - center.x; var dy = p.y - center.y
        val len = hypot(dx, dy)
        if (len > radius) { dx = dx / len * radius; dy = dy / len * radius }
        Offset(center.x + dx, center.y + dy)
    } ?: center
    val active = activePos != null
    val knobR = radius * 0.58f
    drawCircle(ink.copy(alpha = (if (active) 0.5f else 0.32f) * opacity), knobR, knob)
    drawCircle(ink.copy(alpha = 0.7f * opacity), knobR, knob, style = cache.stroke(strokeW))
    // Cardinal cap markers (Xbox 360 stick).
    val dotD = knobR * 0.6f; val dotR = radius * 0.018f
    val dot = ink.copy(alpha = 0.6f * opacity)
    for (p in listOf(
        Offset(knob.x, knob.y - dotD), Offset(knob.x, knob.y + dotD),
        Offset(knob.x - dotD, knob.y), Offset(knob.x + dotD, knob.y),
    )) drawCircle(dot, dotR, p)
}

// ---- Round 2: the modern look (dark glass) ----

// Glass: a dark translucent body with a light rim, readable over bright and dark scenes alike.
private val GLASS = Color(0xFF0B0F0D)
private val GLASS_PRESSED = Color(0xFF26302B)
// The face letters keep the Xbox colours, lighter so they read on the dark glass.
private val LETTER_A = Color(0xFF7BDA55)
private val LETTER_B = Color(0xFFFF6A5F)
private val LETTER_X = Color(0xFF5DA9FF)
private val LETTER_Y = Color(0xFFFFD447)

private fun DrawScope.glassDisc(center: Offset, radius: Float, rim: Float, opacity: Float, pressed: Boolean, rimColor: Color = Color.White) {
    drawCircle((if (pressed) GLASS_PRESSED else GLASS).copy(alpha = (if (pressed) 0.72f else 0.46f) * opacity), radius, center)
    drawCircle(rimColor.copy(alpha = (if (pressed) 0.85f else 0.32f) * opacity), radius - rim / 2f, center, style = Stroke(rim))
}

private fun DrawScope.drawGlassButton(
    c: OnScreenControl.Button, center: Offset, radius: Float, rim: Float, opacity: Float, pressed: Boolean, cache: GamepadDrawCache,
) {
    val letter = when (c.id) {
        ControlId.A -> LETTER_A; ControlId.B -> LETTER_B
        ControlId.X -> LETTER_X; ControlId.Y -> LETTER_Y
        else -> null
    }
    when {
        letter != null -> {
            glassDisc(center, radius, rim, opacity, pressed, rimColor = if (pressed) letter else Color.White)
            drawLabel(cache, c.label, center, radius, letter.copy(alpha = 0.96f * opacity), bold = true, fill = 0.56f, tinted = true)
        }
        c.id in TRIGGER_IDS || c.id in PILL_IDS -> {
            val trigger = c.id in TRIGGER_IDS
            val w = radius * (if (trigger) 4.0f else 3.1f); val h = radius * (if (trigger) 1.5f else 1.1f)
            val tl = Offset(center.x - w / 2f, center.y - h / 2f)
            val cr = CornerRadius(h / 2f, h / 2f)
            drawRoundRect((if (pressed) GLASS_PRESSED else GLASS).copy(alpha = (if (pressed) 0.72f else 0.46f) * opacity), tl, Size(w, h), cr)
            drawRoundRect(Color.White.copy(alpha = (if (pressed) 0.85f else 0.32f) * opacity),
                Offset(tl.x + rim / 2f, tl.y + rim / 2f), Size(w - rim, h - rim), cr, style = Stroke(rim))
            drawLabel(cache, c.label, center, radius, Color.White.copy(alpha = 0.9f * opacity))
        }
        else -> {                                           // L3/R3/Back/Start: a small disc
            val rr = radius * 0.8f
            glassDisc(center, rr, rim, opacity, pressed)
            drawLabel(cache, c.label, center, rr, Color.White.copy(alpha = 0.88f * opacity))
        }
    }
}

/** Four arrow pads around a hub, each lit while pressed. */
private fun DrawScope.drawGlassDpad(center: Offset, radius: Float, rim: Float, opacity: Float, dirs: Set<Int>, cache: GamepadDrawCache) {
    val g = cache.geom(center, radius)
    drawCircle(GLASS.copy(alpha = 0.26f * opacity), radius, center)
    clipPath(g.clipOval) {
        drawPath(g.cross, GLASS.copy(alpha = 0.5f * opacity))
        for (code in dirs) {
            val clip = g.armClips.getOrNull(code) ?: continue
            clipPath(clip) { drawPath(g.cross, Color.White.copy(alpha = 0.32f * opacity)) }
        }
        drawPath(g.cross, Color.White.copy(alpha = 0.3f * opacity), style = Stroke(rim))
    }
    // An arrow on each arm, pointing out.
    val tip = radius * 0.86f; val base = radius * 0.6f; val half = radius * 0.13f
    for (dir in 0..3) {
        val (dx, dy) = when (dir) { 0 -> -1f to 0f; 1 -> 0f to -1f; 2 -> 1f to 0f; else -> 0f to 1f }
        val path = Path().apply {
            moveTo(center.x + dx * tip, center.y + dy * tip)
            lineTo(center.x + dx * base - dy * half, center.y + dy * base + dx * half)
            lineTo(center.x + dx * base + dy * half, center.y + dy * base - dx * half)
            close()
        }
        drawPath(path, Color.White.copy(alpha = (if (dir in dirs) 0.95f else 0.6f) * opacity))
    }
}

/** A dark well with a ring and a domed knob that lights its rim while held. */
private fun DrawScope.drawGlassStick(center: Offset, radius: Float, rim: Float, opacity: Float, activePos: Offset?) {
    drawCircle(GLASS.copy(alpha = 0.34f * opacity), radius, center)
    drawCircle(Color.White.copy(alpha = 0.22f * opacity), radius - rim / 2f, center, style = Stroke(rim))
    val knob = activePos?.let { p ->
        var dx = p.x - center.x; var dy = p.y - center.y
        val len = hypot(dx, dy)
        if (len > radius) { dx = dx / len * radius; dy = dy / len * radius }
        Offset(center.x + dx, center.y + dy)
    } ?: center
    val active = activePos != null
    val knobR = radius * 0.56f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A443F), GLASS), center = Offset(knob.x, knob.y - knobR * 0.35f), radius = knobR * 1.3f),
        knobR, knob, alpha = (if (active) 0.92f else 0.78f) * opacity)
    drawCircle(Color.White.copy(alpha = (if (active) 0.8f else 0.4f) * opacity), knobR - rim / 2f, knob, style = Stroke(rim))
}

private fun DrawScope.drawSelection(c: OnScreenControl, size: IntSize, density: Density) {
    val center = controlCenterPx(c, size)
    val radius = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    val ring = with(density) { 3.dp.toPx() }
    drawCircle(Color(0xFF79DD5F), radius + ring, center, style = Stroke(ring))
}

/** A dashed ring around a control the player hid (editor only). */
private fun DrawScope.drawHiddenMark(c: OnScreenControl, size: IntSize, density: Density) {
    val center = controlCenterPx(c, size)
    val radius = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
    val w = with(density) { 1.5.dp.toPx() }
    val dash = with(density) { 5.dp.toPx() }
    drawCircle(Color.White.copy(alpha = 0.55f), radius + w, center,
        style = Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))))
}

private fun DrawScope.drawLabel(
    cache: GamepadDrawCache, label: String, center: Offset, radius: Float, color: Color,
    bold: Boolean = false, fill: Float? = null, tinted: Boolean = false,
) {
    if (label.isEmpty()) return
    // Only color.alpha varies per draw; label RGB stays white regardless of the bright-scene ink,
    // unless [tinted] (the modern look's coloured A/B/X/Y letters).
    val l = cache.label(label, radius, bold, fill)
    drawIntoCanvas { canvas ->
        l.paint.color = if (tinted) color.copy(alpha = 1f).toArgb() else android.graphics.Color.WHITE
        l.paint.alpha = (color.alpha * 255).toInt()
        val nudgeX = if (fill != null) ABXY_LABEL_NUDGE_X * radius else 0f
        val baseline = center.y - l.cy                    // center the glyph itself, not the font line
        val x = (if (fill != null) center.x else center.x - l.cx + l.opticalDx) + nudgeX
        canvas.nativeCanvas.drawText(label, x, baseline, l.paint)
    }
}
