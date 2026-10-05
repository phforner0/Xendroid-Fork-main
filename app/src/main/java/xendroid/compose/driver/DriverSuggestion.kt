package xendroid.compose.driver

/**
 * 15p (Bannerlator `54c1dd35`): which listed driver suits this phone's GPU, read from the names
 * driver builders give their files and releases ("a7xx", "Adreno 740", "A8XX"). It is a hint, not
 * a guarantee: a build named for the GPU's family is preferred, then one named for no family
 * (most Turnip builds), and builds named for another family are never suggested. Among equals,
 * the newest release wins. Only Adreno GPUs get a suggestion (custom drivers load only there).
 * Pure, tested on the JVM.
 */
object DriverSuggestion {
    /** How a driver's names relate to the GPU, best first. */
    enum class Fit { MODEL, FAMILY, ANY, OTHER_FAMILY }

    /** The Adreno model in a Vulkan device name ("Adreno (TM) 740", "Turnip Adreno (TM) 830"); null otherwise. */
    fun adrenoModel(gpu: String?): Int? =
        gpu?.let { Regex("(?i)adreno[^0-9]{0,8}(\\d{3,4})").find(it) }?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 100..9999 }

    /** 6 for 6xx, 7 for 7xx, 8 for 8xx (an Adreno 702 is a 7xx). */
    fun family(model: Int): Int = if (model >= 1000) model / 1000 else model / 100

    private val FAMILY = Regex("(?i)(?<![a-z0-9])a?([5-9])xx(?![a-z0-9])")
    private val MODEL = Regex("(?i)(?:(?<![a-z0-9])a|adreno[ _-]?)(\\d{3})(?!\\d)")

    fun fit(driver: DriverInfo, model: Int): Fit {
        val text = driver.name + " " + driver.version
        if (MODEL.findAll(text).any { it.groupValues[1].toInt() == model }) return Fit.MODEL
        val families = FAMILY.findAll(text).map { it.groupValues[1].toInt() }.toSet()
        return when {
            family(model) in families -> Fit.FAMILY
            families.isEmpty() -> Fit.ANY
            else -> Fit.OTHER_FAMILY
        }
    }

    /** The suggestion for [gpu] among [drivers], or null (not an Adreno, or nothing that fits). */
    fun suggest(drivers: List<DriverInfo>, gpu: String?): DriverInfo? {
        val model = adrenoModel(gpu) ?: return null
        return drivers.withIndex()
            .map { (index, driver) -> Triple(driver, fit(driver, model), index) }
            .filter { it.second != Fit.OTHER_FAMILY }
            // Best fit, then the newest release (ISO dates sort as text), then the source's own order.
            .sortedWith(compareBy<Triple<DriverInfo, Fit, Int>> { it.second.ordinal }
                .thenByDescending { it.first.publishedAt }
                .thenBy { it.third })
            .firstOrNull()?.first
    }
}
