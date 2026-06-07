package com.noobexon.xposedfakelocation.manager.localization

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import java.util.Locale

private const val ENGLISH_LANGUAGE_TAG = "en"
private const val CHINESE_LANGUAGE_TAG = "zh-CN"

enum class LanguageOption(
    val tag: String,
    @StringRes val labelRes: Int
) {
    SYSTEM(DEFAULT_LANGUAGE_TAG, R.string.language_system),
    ENGLISH(ENGLISH_LANGUAGE_TAG, R.string.language_english),
    CHINESE(CHINESE_LANGUAGE_TAG, R.string.language_chinese);

    /**
     * The language's name written in its own script (autonym), e.g. "English" or "中文", so a user
     * can recognize it regardless of the current UI language. `null` for [SYSTEM], whose effective
     * language depends on the device locale.
     */
    val autonym: String?
        get() = when (this) {
            SYSTEM -> null
            else -> Locale.forLanguageTag(tag).let { locale ->
                locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            }
        }

    companion object {
        fun fromTag(tag: String): LanguageOption {
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}
