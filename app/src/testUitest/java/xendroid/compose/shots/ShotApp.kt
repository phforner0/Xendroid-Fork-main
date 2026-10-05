package xendroid.compose.shots

/**
 * The application of the screen tests: the app's own [xendroid.compose.Application] probes the
 * GPU through a native library at start, which the JVM does not have. This one names a GPU,
 * "loads" the faked core ([FakeCore]) and seeds the global config from the bundled template, as
 * the first start on a phone does.
 */
class ShotApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        xendroid.compose.Application.ctx = this
        xendroid.compose.Application.gpu_device_name_vk = "Adreno (TM) 740"
        // All Files Access granted, as the app asks for it on a phone.
        org.robolectric.shadows.ShadowEnvironment.addExternalDir("external")
        org.robolectric.shadows.ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED)
        FakeCore.reset()
        if (xendroid.compose.Emulator.get == null) xendroid.compose.Emulator.get = xendroid.compose.Emulator()
        val global = xendroid.compose.Application.get_global_config_file()
        if (!global.exists()) {
            global.parentFile?.mkdirs()
            global.writeText(assets.open("config/default_config.toml").use { it.readBytes().toString(Charsets.UTF_8) })
        }
    }
}
