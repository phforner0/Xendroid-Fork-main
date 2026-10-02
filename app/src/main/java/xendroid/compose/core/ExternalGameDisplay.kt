package xendroid.compose.core

import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.graphics.PixelFormat

/** Moves the ONE guest output surface; controls and menu stay on the handset.
 * Hot-unplug returns to the primary holder without rebooting the native core. */
class ExternalGameDisplay(
    private val activity: Activity,
    private val primary: SurfaceView,
    private val callback: SurfaceHolder.Callback,
    private val detach: () -> Unit,
    private val statusChanged: (String) -> Unit,
) : DisplayManager.DisplayListener, AutoCloseable {
    private val manager = activity.getSystemService(DisplayManager::class.java)
    private var presentation: Presentation? = null
    private var output: SurfaceView = primary
    private var closing = false
    private var detached = false
    val activeDisplay: Display? get() = presentation?.display
    fun owns(holder: SurfaceHolder) = holder === output.holder
    init { manager.registerDisplayListener(this, Handler(Looper.getMainLooper())) }

    fun choices(): List<Display> = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .filter { it.isValid && it.displayId != Display.DEFAULT_DISPLAY }
    fun cycle() {
        val displays = choices()
        if (displays.isEmpty()) { statusChanged(activity.getString(xendroid.compose.R.string.tv_none)); return }
        val index = displays.indexOfFirst { it.displayId == activeDisplay?.displayId }
        if (index + 1 >= displays.size) toPhone() else toDisplay(displays[index + 1])
    }
    private fun toDisplay(display: Display) {
        val next = try { Presentation(activity, display) } catch (e: RuntimeException) {
            statusChanged(activity.getString(xendroid.compose.R.string.tv_unavailable)); return
        }
        val surface = SurfaceView(next.context)
        detach()
        detached = true
        val old = presentation
        presentation = null
        output = surface
        next.setContentView(surface)
        next.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        surface.holder.addCallback(callback)
        presentation = next
        old?.dismiss()
        next.setOnDismissListener { if (!closing && presentation === next) toPhone() }
        try { next.show(); detached = false; statusChanged(activity.getString(xendroid.compose.R.string.tv_on, display.name, display.refreshRate.toString())) }
        catch (e: WindowManager.InvalidDisplayException) { toPhone() }
    }
    fun toPhone() {
        if (output === primary && presentation == null && !detached) return
        detach()
        val old = presentation
        presentation = null; output = primary
        old?.dismiss()
        if (primary.holder.surface.isValid) {
            callback.surfaceCreated(primary.holder)
            callback.surfaceChanged(primary.holder, PixelFormat.RGBA_8888, primary.width, primary.height)
        }
        detached = false
        statusChanged(activity.getString(xendroid.compose.R.string.tv_phone))
    }
    override fun onDisplayRemoved(id: Int) { if (activeDisplay?.displayId == id) toPhone() }
    override fun onDisplayAdded(id: Int) { if (activeDisplay == null) statusChanged(activity.getString(xendroid.compose.R.string.tv_available, choices().size)) }
    override fun onDisplayChanged(id: Int) {
        if (activeDisplay?.displayId == id && activeDisplay?.isValid == false) toPhone()
    }
    override fun close() {
        closing = true; manager.unregisterDisplayListener(this)
        presentation?.dismiss(); presentation = null
    }
}
