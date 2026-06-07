package com.noobexon.xposedfakelocation.manager.ui.settings

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import kotlin.math.round

/**
 * Settings categories in display order (top to bottom): spoofing groups first, then behavior,
 * advanced, and app preferences.
 *
 * @property titleRes localized category header label.
 */
enum class SettingsCategory(@StringRes val titleRes: Int) {
    LOCATION(R.string.category_location),
    ALTITUDE(R.string.category_altitude),
    MOVEMENT(R.string.category_movement),
    NOTIFICATIONS(R.string.category_notifications),
    SYSTEM_HOOKS(R.string.category_system_hooks),
    EXTERNAL_CONTROL(R.string.category_external_control),
    LANGUAGE(R.string.category_language)
}

/**
 * Static, stable metadata for a numeric (field + slider) setting. The UI works uniformly in
 * [Float] and converts back to the persisted type in the value callback.
 *
 * @property titleRes title shown next to the enable switch.
 * @property descriptionRes help text shown when the info icon is tapped.
 * @property labelRes short label used as the value field's label.
 * @property unit unit suffix shown in the field (e.g. "m", "m/s").
 * @property min inclusive lower bound.
 * @property max inclusive upper bound.
 * @property step granularity used only to decide display precision (>=1 -> 0 decimals, else 1).
 */
enum class NumericSetting(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val labelRes: Int,
    val unit: String,
    val min: Float,
    val max: Float,
    val step: Float
) {
    RANDOMIZE_RADIUS(
        R.string.setting_randomize_title,
        R.string.setting_randomize_description,
        R.string.setting_randomize_radius_label,
        unit = "m", min = 0f, max = 2000f, step = 0.1f
    ),
    HORIZONTAL_ACCURACY(
        R.string.setting_horizontal_accuracy_title,
        R.string.setting_horizontal_accuracy_description,
        R.string.setting_horizontal_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f
    ),
    VERTICAL_ACCURACY(
        R.string.setting_vertical_accuracy_title,
        R.string.setting_vertical_accuracy_description,
        R.string.setting_vertical_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f
    ),
    ALTITUDE(
        R.string.setting_altitude_title,
        R.string.setting_altitude_description,
        R.string.setting_altitude_label,
        unit = "m", min = 0f, max = 2000f, step = 0.5f
    ),
    MEAN_SEA_LEVEL(
        R.string.setting_msl_title,
        R.string.setting_msl_description,
        R.string.setting_msl_label,
        unit = "m", min = -400f, max = 2000f, step = 0.5f
    ),
    MEAN_SEA_LEVEL_ACCURACY(
        R.string.setting_msl_accuracy_title,
        R.string.setting_msl_accuracy_description,
        R.string.setting_msl_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f
    ),
    SPEED(
        R.string.setting_speed_title,
        R.string.setting_speed_description,
        R.string.setting_speed_label,
        unit = "m/s", min = 0f, max = 30f, step = 0.1f
    ),
    SPEED_ACCURACY(
        R.string.setting_speed_accuracy_title,
        R.string.setting_speed_accuracy_description,
        R.string.setting_speed_accuracy_label,
        unit = "m/s", min = 0f, max = 100f, step = 1f
    );

    /** Number of fractional digits to show/keep: whole numbers for integer steps, otherwise one. */
    val decimals: Int get() = if (step >= 1f) 0 else 1

    /** Rounds [value] to [decimals] so the displayed text and the persisted value never diverge. */
    fun roundValue(value: Float): Float =
        if (decimals == 0) round(value) else round(value * 10f) / 10f
}

/**
 * A single renderable settings row. Each entry exposes a stable [key] and the string resources
 * used both for display and for search matching.
 */
sealed interface SettingEntry {
    val key: String
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int

    /** An on/off toggle (hide-toast, external broadcast, system hooks). */
    data class Switch(
        override val key: String,
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingEntry

    /** A numeric value with an enable switch, editable field, and continuous slider. */
    data class Numeric(
        val setting: NumericSetting,
        val enabled: Boolean,
        val onEnabledChange: (Boolean) -> Unit,
        val value: Float,
        val onValueChange: (Float) -> Unit
    ) : SettingEntry {
        override val key: String get() = setting.name
        override val titleRes: Int get() = setting.titleRes
        override val descriptionRes: Int get() = setting.descriptionRes
    }

    /** The language picker row (opens a single-choice dialog). */
    data class Language(
        val selected: LanguageOption,
        val onSelected: (LanguageOption) -> Unit
    ) : SettingEntry {
        override val key: String get() = "language"
        override val titleRes: Int get() = R.string.setting_language_title
        override val descriptionRes: Int get() = R.string.setting_language_description
    }
}
