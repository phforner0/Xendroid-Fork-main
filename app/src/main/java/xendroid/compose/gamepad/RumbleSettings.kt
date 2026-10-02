package xendroid.compose.gamepad

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * U08: how strongly each controller plays the guest's rumble. A controller the player set on
 * its own (by its Android input descriptor, so it is the same pad after reconnecting) keeps
 * that; every other one uses [default], which the in-game menu changes. Pure, tested on the JVM.
 */
data class RumbleSettings(
    val default: RumbleIntensity = RumbleIntensity.MEDIUM,
    /** Descriptor to intensity, oldest first. */
    val perDevice: Map<String, RumbleIntensity> = emptyMap(),
) {
    fun forDevice(descriptor: String?): RumbleIntensity = descriptor?.let { perDevice[it] } ?: default

    /** Whether any controller vibrates at all (so the guest's rumble is worth reading). */
    val anyOn: Boolean get() = default != RumbleIntensity.OFF || perDevice.values.any { it != RumbleIntensity.OFF }

    fun cycleDefault(): RumbleSettings = copy(default = default.next())

    /** This controller: default → Off → Low → Medium → High → default again. */
    fun cycleDevice(descriptor: String): RumbleSettings {
        if (descriptor.isBlank()) return this
        val next = when (val own = perDevice[descriptor]) {
            null -> RumbleIntensity.OFF
            RumbleIntensity.HIGH -> null
            else -> own.next()
        }
        val others = perDevice - descriptor
        if (next == null) return copy(perDevice = others)
        // Bounded: the controller set longest ago gives its place.
        val kept = if (others.size >= MAX_DEVICES) others.entries.drop(others.size - MAX_DEVICES + 1).associate { it.toPair() } else others
        return copy(perDevice = kept + (descriptor to next))
    }

    /** The per-controller part, for preferences. */
    fun encodeDevices(): String = json.encodeToString(MapSerializer(String.serializer(), String.serializer()),
        perDevice.mapValues { it.value.name })

    companion object {
        /** The default: written by the game process (its menu), with its other touch options. */
        const val PREFS = "touch_options"
        const val DEFAULT_KEY = "controller_rumble"
        /** Each controller's own: a file of its own, written only by the main process (Test
         *  controllers) and read by a game when it starts. Preferences are cached per process
         *  and written whole, so two processes must never write the same file. */
        const val DEVICES_PREFS = "controller_rumble"
        const val DEVICES_KEY = "devices"
        const val MAX_DEVICES = 32
        private val json = Json { ignoreUnknownKeys = true }

        /** From preferences; anything unreadable counts as not set. */
        fun decode(default: String?, devices: String?): RumbleSettings {
            val map = devices?.let {
                runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull()
            }.orEmpty()
            val parsed = map.entries.mapNotNull { (descriptor, name) ->
                RumbleIntensity.entries.firstOrNull { it.name == name }?.let { descriptor to it }
            }.filter { it.first.isNotBlank() }.takeLast(MAX_DEVICES).toMap()
            return RumbleSettings(RumbleIntensity.parse(default), parsed)
        }
    }
}
