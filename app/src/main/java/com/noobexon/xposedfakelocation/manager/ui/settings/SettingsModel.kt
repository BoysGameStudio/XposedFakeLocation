package com.noobexon.xposedfakelocation.manager.ui.settings

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import kotlin.math.round

/**
 * Visual grouping and display order for the settings screen (top to bottom). Spoofing categories
 * come first, followed by behavioural and app-level preferences.
 *
 * @property titleRes String resource for the uppercase section header rendered above each card.
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
 * Compile-time metadata for a numeric (field + slider) setting row. The UI works uniformly in
 * [Float]; callers convert back to the ViewModel's persisted type in the `onValueChange` lambda.
 *
 * @property titleRes String resource for the setting name shown next to the enable switch.
 * @property descriptionRes String resource for the help text shown when the info icon is tapped.
 * @property labelRes String resource for the short label used as the value field's floating label.
 * @property unit Unit suffix appended inside the value field (e.g. `"m"`, `"m/s"`).
 * @property min Inclusive lower bound of the slider and value field.
 * @property max Inclusive upper bound of the slider and value field.
 * @property step Granularity hint; used only to derive [decimals] — values `>= 1f` give zero
 *   decimal places, smaller values give one. It no longer controls discrete slider steps.
 * @property default Value written (and persisted) when the user clears the field and blurs without
 *   entering a number. Sourced directly from `data/Constants.kt` so it stays in sync with the
 *   value produced by [SettingsViewModel.resetToDefaults].
 */
enum class NumericSetting(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val labelRes: Int,
    val unit: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float
) {
    RANDOMIZE_RADIUS(
        R.string.setting_randomize_title,
        R.string.setting_randomize_description,
        R.string.setting_randomize_radius_label,
        unit = "m", min = 0f, max = 2000f, step = 0.1f, default = DEFAULT_RANDOMIZE_RADIUS.toFloat()
    ),
    HORIZONTAL_ACCURACY(
        R.string.setting_horizontal_accuracy_title,
        R.string.setting_horizontal_accuracy_description,
        R.string.setting_horizontal_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f, default = DEFAULT_ACCURACY.toFloat()
    ),
    VERTICAL_ACCURACY(
        R.string.setting_vertical_accuracy_title,
        R.string.setting_vertical_accuracy_description,
        R.string.setting_vertical_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f, default = DEFAULT_VERTICAL_ACCURACY
    ),
    ALTITUDE(
        R.string.setting_altitude_title,
        R.string.setting_altitude_description,
        R.string.setting_altitude_label,
        unit = "m", min = 0f, max = 2000f, step = 0.5f, default = DEFAULT_ALTITUDE.toFloat()
    ),
    MEAN_SEA_LEVEL(
        R.string.setting_msl_title,
        R.string.setting_msl_description,
        R.string.setting_msl_label,
        unit = "m", min = -400f, max = 2000f, step = 0.5f, default = DEFAULT_MEAN_SEA_LEVEL.toFloat()
    ),
    MEAN_SEA_LEVEL_ACCURACY(
        R.string.setting_msl_accuracy_title,
        R.string.setting_msl_accuracy_description,
        R.string.setting_msl_accuracy_label,
        unit = "m", min = 0f, max = 100f, step = 1f, default = DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    ),
    SPEED(
        R.string.setting_speed_title,
        R.string.setting_speed_description,
        R.string.setting_speed_label,
        unit = "m/s", min = 0f, max = 30f, step = 0.1f, default = DEFAULT_SPEED
    ),
    SPEED_ACCURACY(
        R.string.setting_speed_accuracy_title,
        R.string.setting_speed_accuracy_description,
        R.string.setting_speed_accuracy_label,
        unit = "m/s", min = 0f, max = 100f, step = 1f, default = DEFAULT_SPEED_ACCURACY
    );

    /**
     * Number of fractional digits used when formatting or rounding a value for this setting.
     * Derived from [step]: integer-granularity settings (`step >= 1f`) use `0`; sub-unit settings
     * use `1`. Consistent between the field label and [roundValue] so the displayed and persisted
     * values always agree.
     */
    val decimals: Int get() = if (step >= 1f) 0 else 1

    /**
     * Rounds [value] to [decimals] fractional digits, eliminating the floating-point drift that
     * would otherwise cause the displayed field text and the persisted [Double]/[Float] to diverge.
     *
     * @param value raw value to round (e.g. from a slider drag or typed input).
     * @return value rounded to the precision implied by [step].
     */
    fun roundValue(value: Float): Float =
        if (decimals == 0) round(value) else round(value * 10f) / 10f
}

/**
 * Declarative model for a single row in the settings list. Every variant exposes a stable [key]
 * (used by Compose's `key {}` block to anchor remembered state across recompositions, e.g. during
 * search filtering) and the string resources required for search matching.
 *
 * @property key Stable, unique identifier for this row within the full settings list.
 * @property titleRes String resource for the setting's primary label.
 * @property descriptionRes String resource for the setting's help text, shown via the info icon.
 */
sealed interface SettingEntry {
    val key: String
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int

    /**
     * A boolean on/off row backed by a [Switch].
     *
     * @property key Stable row identifier.
     * @property titleRes Primary label resource.
     * @property descriptionRes Help text resource.
     * @property checked Current toggle state.
     * @property onCheckedChange Callback invoked with the new [Boolean] when the switch is toggled.
     */
    data class Switch(
        override val key: String,
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingEntry

    /**
     * A numeric row with an enable [Switch], an editable value field, and a continuous [Slider].
     * All display and interaction logic is driven by [setting]'s static metadata so the row is
     * fully data-driven.
     *
     * @property setting Static metadata (titles, unit, range, default, precision) for this row.
     * @property enabled Whether the setting's value is currently active (controls switch + field/slider visibility).
     * @property onEnabledChange Callback invoked with the new [Boolean] when the enable switch is toggled.
     * @property value Currently committed value in [NumericSetting.unit].
     * @property onValueChange Callback invoked with the new [Float] once the user finishes editing
     *   (field blur / IME Done / slider release), never on every keystroke or drag frame.
     */
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

    /**
     * The language picker row. Tapping anywhere on the row opens a [LanguageSelectionDialog];
     * selecting an option there applies it immediately and recreates the host Activity.
     *
     * @property selected The currently active [LanguageOption], shown collapsed in the row.
     * @property onSelected Callback invoked with the chosen [LanguageOption] when the user confirms
     *   in the dialog. The caller is responsible for recreating the Activity so the locale applies.
     */
    data class Language(
        val selected: LanguageOption,
        val onSelected: (LanguageOption) -> Unit
    ) : SettingEntry {
        override val key: String get() = "language"
        override val titleRes: Int get() = R.string.setting_language_title
        override val descriptionRes: Int get() = R.string.setting_language_description
    }
}
