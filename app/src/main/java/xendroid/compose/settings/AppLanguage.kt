package xendroid.compose.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.util.Locale

/**
 * 15l (Eden, Bannerlator): the app's language chosen inside the app. [tag] is what Android calls
 * it (null follows the phone); [autonym] is the language's own name, shown as is in every
 * language. The tags are the ones `res/xml/locales_config.xml` offers to the system.
 */
enum class AppLanguage(val tag: String?, val autonym: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)");

    companion object {
        /**
         * The choice for a list of language tags as Android keeps them ("pt-BR,en"): the first
         * language the app has (any Portuguese is the Brazilian one; any English is English).
         * Nothing, or none the app has, follows the phone.
         */
        fun fromTags(tags: String?): AppLanguage =
            tags.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.firstNotNullOfOrNull { tag ->
                when (Locale.forLanguageTag(tag).language) {
                    "en" -> ENGLISH
                    "pt" -> PORTUGUESE_BR
                    else -> null
                }
            } ?: SYSTEM
    }
}

/**
 * Where the choice lives. On Android 13 and later it is the system's per-app language
 * ([LocaleManager]), the same one the system settings change, and the system applies it to every
 * process of the app. Before 13 the app keeps it and each activity applies it to itself in
 * attachBaseContext ([overrideFor]); texts made outside an activity (notifications) then follow
 * the phone.
 */
object AppLanguageStore {
    private const val PREFS = "app_language"
    private const val TAG = "tag"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun saved(context: Context): AppLanguage = AppLanguage.fromTags(prefs(context).getString(TAG, null))

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= 33) AppLanguage.fromTags(localeManager(context).applicationLocales.toLanguageTags())
        else saved(context)

    /** Switches to [language]. Before Android 13 the activity is recreated to show it. */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            localeManager(activity).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs(activity).edit(commit = true) { if (language.tag == null) remove(TAG) else putString(TAG, language.tag) }
            activity.recreate()
        }
    }

    /**
     * Before Android 13: the configuration an activity applies over its own for the chosen
     * language (null when it follows the phone), and the process's default locale set to match,
     * so numbers and dates read like the texts. From 13 on the system does both.
     */
    fun overrideFor(base: Context): Configuration? {
        if (Build.VERSION.SDK_INT >= 33) return null
        val tag = saved(base).tag
        val locale = tag?.let { Locale.forLanguageTag(it) }
        Locale.setDefault(locale ?: base.resources.configuration.locales[0])
        return locale?.let { Configuration().apply { setLocales(LocaleList(it)) } }
    }

    /**
     * A choice kept before the phone moved to Android 13 becomes the system's, once, so an
     * Android update does not lose it.
     */
    fun moveToSystem(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val tag = saved(context).tag ?: return
        val manager = localeManager(context)
        if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(tag)
        prefs(context).edit { remove(TAG) }
    }

    @RequiresApi(33)
    private fun localeManager(context: Context): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
