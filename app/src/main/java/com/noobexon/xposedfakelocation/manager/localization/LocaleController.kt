package com.noobexon.xposedfakelocation.manager.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.KEY_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import java.util.Locale

object LocaleController {
    fun attachBaseContext(context: Context): Context {
        return localizedContext(context, readLanguageTag(context))
    }

    /**
     * The device's actual system language written in its own script (autonym), e.g. "English".
     * Read from the system resources so it reflects the real device locale even when the app has
     * overridden its own locale. Used to show what the "System default" option resolves to.
     */
    fun systemLanguageAutonym(): String {
        val locale = Resources.getSystem().configuration.locales[0]
        return locale.getDisplayLanguage(locale)
            .ifBlank { locale.getDisplayName(locale) }
            .replaceFirstChar { it.titlecase(locale) }
    }

    fun persistLanguageTag(context: Context, languageTag: String) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
    }

    fun readLanguageTag(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
    }

    private fun localizedContext(context: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return context

        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
