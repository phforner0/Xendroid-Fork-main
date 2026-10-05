package xendroid.compose.driver

import java.io.File

/** Whether this phone can load a custom Vulkan driver: an Adreno GPU (KGSL), through adrenotools. */
object CustomDrivers {
    /** The screen tests draw an Adreno phone on the JVM, which has no GPU; null = ask the device. */
    @Volatile var forced: Boolean? = null

    val supported: Boolean
        get() = forced ?: runCatching { File("/dev/kgsl-3d0").exists() }.getOrDefault(false)
}
