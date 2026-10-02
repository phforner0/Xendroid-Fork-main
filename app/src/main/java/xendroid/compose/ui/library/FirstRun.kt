package xendroid.compose.ui.library

import android.content.Context
import androidx.core.content.edit
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

/**
 * L01: what the first-run assistant checks and proposes. Pure, so it is tested without a
 * device; everything works offline and nothing here talks to a third-party service.
 */
object FirstRun {
    enum class Status { OK, WARNING, BLOCKED }

    data class Check(val title: String, val status: Status, val detail: String)

    /** This device, as far as running games goes. [gpuName] null = no Vulkan device found. */
    fun deviceChecks(gpuName: String?, abis: List<String>, sdk: Int): List<Check> = listOf(
        if (gpuName != null) Check("Vulkan GPU", Status.OK, gpuName)
        else Check("Vulkan GPU", Status.BLOCKED, "No Vulkan device found: games cannot run on this phone."),
        if ("arm64-v8a" in abis) Check("64-bit ARM", Status.OK, "arm64-v8a")
        else Check("64-bit ARM", Status.BLOCKED, "The emulator core is built for arm64-v8a only."),
        when {
            sdk >= 30 -> Check("Android", Status.OK, "API $sdk")
            else -> Check("Android", Status.WARNING,
                "API $sdk: choosing a game folder needs Android 11 or newer; games launched from a frontend still work.")
        },
    )

    fun folderCheck(ready: Boolean, supported: Boolean): Check = when {
        ready -> Check("Game folder", Status.OK, "Set; the library scans it.")
        !supported -> Check("Game folder", Status.WARNING, "Needs Android 11 or newer (All Files Access).")
        else -> Check("Game folder", Status.WARNING, "Not set yet: choose the folder that holds your games.")
    }

    /** The games' language and region (the console settings), proposed from the phone's locale. */
    data class GuestLocale(val languageValue: String?, val languageLabel: String?,
                           val countryValue: String?, val countryLabel: String?) {
        val any: Boolean get() = languageValue != null || countryValue != null
    }

    /**
     * Maps an Android locale (ISO 639 [language], ISO 3166 [country]) onto the console's
     * language and country lists; either is null when the console has no such entry, and
     * then the current setting is kept.
     */
    fun guestLocale(language: String, country: String): GuestLocale {
        val languages = (SettingsSchema.byKey["Console|user_language"] as? Setting.ListChoice)?.options.orEmpty()
        val countries = (SettingsSchema.byKey["Console|user_country"] as? Setting.ListChoice)?.options.orEmpty()
        // Norwegian Bokmål is "nb" on the console and either "nb" or "no" on phones.
        val lang = language.lowercase().let { if (it == "no") "nb" else it }
        val languageOption = languages.firstOrNull { it.label == lang }
        val countryOption = countries.firstOrNull { it.label == country.uppercase() }
        return GuestLocale(languageOption?.value, languageOption?.label, countryOption?.value, countryOption?.label)
    }
}

/** Whether the assistant ran (finished or skipped); it can be reopened from the library menu. */
object FirstRunStore {
    private const val PREFS = "first_run"
    private const val DONE = "done"

    fun done(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(DONE, true) }
    }
}
