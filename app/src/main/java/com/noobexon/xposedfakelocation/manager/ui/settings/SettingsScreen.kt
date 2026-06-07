package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import com.noobexon.xposedfakelocation.manager.localization.LocaleController

/** Shared spacing and card styling constants for the settings screen. */
private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 12.dp
    val CARD_ELEVATION = 2.dp
}

/**
 * Static, stable metadata for a numeric (slider) setting. All fields are immutable primitives or
 * [StringRes] ids, so entries are singletons that never allocate per recomposition. The dynamic
 * value and its setter are resolved at the call site from [SettingsViewModel] (see
 * [NumericSettingsSections]); the UI layer works uniformly in [Float] and converts back to the
 * persisted type in the setter lambda.
 *
 * @property titleRes title shown next to the enable switch.
 * @property descriptionRes help text shown when the info icon is tapped.
 * @property labelRes short label prefixed to the formatted value.
 * @property unit unit suffix appended to the value (e.g. `"m"`, `"m/s"`).
 * @property min inclusive lower bound of the slider and stepper.
 * @property max inclusive upper bound of the slider and stepper.
 * @property step increment applied by the +/- buttons and slider tick spacing.
 */
private enum class NumericSetting(
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
    )
}

/**
 * Groups [NumericSetting]s under a localized category header, in display order.
 *
 * @property titleRes localized category header title.
 * @property settings settings rendered under this category, top to bottom.
 */
private enum class SettingCategory(
    @StringRes val titleRes: Int,
    val settings: List<NumericSetting>
) {
    LOCATION(
        R.string.category_location,
        listOf(
            NumericSetting.RANDOMIZE_RADIUS,
            NumericSetting.HORIZONTAL_ACCURACY,
            NumericSetting.VERTICAL_ACCURACY
        )
    ),
    ALTITUDE(
        R.string.category_altitude,
        listOf(
            NumericSetting.ALTITUDE,
            NumericSetting.MEAN_SEA_LEVEL,
            NumericSetting.MEAN_SEA_LEVEL_ACCURACY
        )
    ),
    MOVEMENT(
        R.string.category_movement,
        listOf(
            NumericSetting.SPEED,
            NumericSetting.SPEED_ACCURACY
        )
    )
}

/**
 * Top-level settings screen. Renders language, notification, external-control, system-hook and
 * numeric spoofing sections, all backed by [settingsViewModel]. Collects one-shot
 * [SystemHooksEvent]s (lifecycle-aware) to show a snackbar or the reboot-required dialog.
 *
 * @param navController used to navigate back from the top app bar.
 * @param settingsViewModel state holder for all settings; defaults to the screen-scoped instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsStateWithLifecycle().value)

    val snackbarHostState = remember { SnackbarHostState() }
    // null = hidden; true = hooks were enabled; false = hooks were disabled
    var restartDialogEnabled by remember { mutableStateOf<Boolean?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsViewModel.systemHooksEvents.collect { event ->
                when (event) {
                    is SystemHooksEvent.RestartRequired -> restartDialogEnabled = event.enabled
                    is SystemHooksEvent.ModuleNotActive ->
                        snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                    is SystemHooksEvent.ScopeRequestFailed ->
                        snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_scope_failed, event.message))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimensions.SPACING_MEDIUM)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_language))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    LanguageSettingItem(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { option ->
                            settingsViewModel.setLanguageTag(option.tag)
                            LocaleController.persistLanguageTag(context, option.tag)
                            (context as? Activity)?.recreate()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_notifications))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                        BooleanSettingItem(
                            title = stringResource(R.string.setting_hide_toast_title),
                            description = stringResource(R.string.setting_hide_toast_description),
                            checked = settingsViewModel.hideFakeLocationToast.collectAsStateWithLifecycle().value,
                            onCheckedChange = settingsViewModel::setHideFakeLocationToast
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_external_control))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                        BooleanSettingItem(
                            title = stringResource(R.string.setting_external_broadcast_title),
                            description = stringResource(R.string.setting_external_broadcast_description),
                            checked = settingsViewModel.enableBroadcastControl.collectAsStateWithLifecycle().value,
                            onCheckedChange = settingsViewModel::setEnableBroadcastControl
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_system_hooks))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                        BooleanSettingItem(
                            title = stringResource(R.string.setting_system_hooks_title),
                            description = stringResource(R.string.setting_system_hooks_description),
                            checked = settingsViewModel.enableSystemHooks.collectAsStateWithLifecycle().value,
                            onCheckedChange = settingsViewModel::setEnableSystemHooks
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                NumericSettingsSections(settingsViewModel)

                Spacer(modifier = Modifier.height(Dimensions.SPACING_LARGE))
            }

            restartDialogEnabled?.let { enabled ->
                AlertDialog(
                    onDismissRequest = { restartDialogEnabled = null },
                    title = { Text(stringResource(R.string.dialog_restart_required_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (enabled) R.string.dialog_restart_required_enable_message
                                else R.string.dialog_restart_required_disable_message
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { restartDialogEnabled = null }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Dropdown row for picking the app UI language.
 *
 * @param selectedLanguage currently selected language, shown in the field.
 * @param onLanguageSelected invoked with the chosen option when the user picks an entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettingItem(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Text(
            text = stringResource(R.string.setting_language_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.setting_language_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        Spacer(modifier = Modifier.height(Dimensions.SPACING_SMALL))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = stringResource(selectedLanguage.labelRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.setting_language_title)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LanguageOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Section header: a bold colored [title] followed by a divider that fills the remaining width.
 *
 * @param title localized header text.
 */
@Composable
private fun CategoryHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimensions.SPACING_SMALL)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier
                .weight(2f)
                .padding(start = Dimensions.SPACING_MEDIUM),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}

/**
 * A single on/off setting row: a [title] with an info button that reveals [description], plus a
 * trailing switch.
 *
 * @param title localized setting name.
 * @param description help text toggled by the info icon.
 * @param checked current switch state.
 * @param onCheckedChange invoked with the new state when the switch is toggled.
 */
@Composable
private fun BooleanSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }
    val moreInfoDescription = stringResource(R.string.setting_more_info, title)
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = { showTooltip = !showTooltip },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = moreInfoDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showTooltip) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (checked) disableDescription else enableDescription
                }
            )
        }
    }
}

/**
 * Renders every numeric spoofing setting, grouped by [SettingCategory]. Collects the relevant
 * flows once here (lifecycle-aware) and dispatches the live value + setter to each item. Each
 * [NumericSettingItem] receives only plain values + callbacks, so unchanged items skip when a
 * single value changes. [Double]-backed settings are converted at the [Float] boundary.
 */
@Composable
private fun NumericSettingsSections(viewModel: SettingsViewModel) {
    val useRandomize by viewModel.useRandomize.collectAsStateWithLifecycle()
    val randomizeRadius by viewModel.randomizeRadius.collectAsStateWithLifecycle()
    val useAccuracy by viewModel.useAccuracy.collectAsStateWithLifecycle()
    val accuracy by viewModel.accuracy.collectAsStateWithLifecycle()
    val useVerticalAccuracy by viewModel.useVerticalAccuracy.collectAsStateWithLifecycle()
    val verticalAccuracy by viewModel.verticalAccuracy.collectAsStateWithLifecycle()
    val useAltitude by viewModel.useAltitude.collectAsStateWithLifecycle()
    val altitude by viewModel.altitude.collectAsStateWithLifecycle()
    val useMeanSeaLevel by viewModel.useMeanSeaLevel.collectAsStateWithLifecycle()
    val meanSeaLevel by viewModel.meanSeaLevel.collectAsStateWithLifecycle()
    val useMeanSeaLevelAccuracy by viewModel.useMeanSeaLevelAccuracy.collectAsStateWithLifecycle()
    val meanSeaLevelAccuracy by viewModel.meanSeaLevelAccuracy.collectAsStateWithLifecycle()
    val useSpeed by viewModel.useSpeed.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val useSpeedAccuracy by viewModel.useSpeedAccuracy.collectAsStateWithLifecycle()
    val speedAccuracy by viewModel.speedAccuracy.collectAsStateWithLifecycle()

    SettingCategory.entries.forEach { category ->
        CategoryHeader(stringResource(category.titleRes))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.SPACING_SMALL),
            shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
        ) {
            Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                category.settings.forEachIndexed { index, setting ->
                    val useValue: Boolean
                    val value: Float
                    val onUseValueChange: (Boolean) -> Unit
                    val onValueChange: (Float) -> Unit
                    when (setting) {
                        NumericSetting.RANDOMIZE_RADIUS -> {
                            useValue = useRandomize
                            value = randomizeRadius.toFloat()
                            onUseValueChange = viewModel::setUseRandomize
                            onValueChange = { viewModel.setRandomizeRadius(it.toDouble()) }
                        }
                        NumericSetting.HORIZONTAL_ACCURACY -> {
                            useValue = useAccuracy
                            value = accuracy.toFloat()
                            onUseValueChange = viewModel::setUseAccuracy
                            onValueChange = { viewModel.setAccuracy(it.toDouble()) }
                        }
                        NumericSetting.VERTICAL_ACCURACY -> {
                            useValue = useVerticalAccuracy
                            value = verticalAccuracy
                            onUseValueChange = viewModel::setUseVerticalAccuracy
                            onValueChange = viewModel::setVerticalAccuracy
                        }
                        NumericSetting.ALTITUDE -> {
                            useValue = useAltitude
                            value = altitude.toFloat()
                            onUseValueChange = viewModel::setUseAltitude
                            onValueChange = { viewModel.setAltitude(it.toDouble()) }
                        }
                        NumericSetting.MEAN_SEA_LEVEL -> {
                            useValue = useMeanSeaLevel
                            value = meanSeaLevel.toFloat()
                            onUseValueChange = viewModel::setUseMeanSeaLevel
                            onValueChange = { viewModel.setMeanSeaLevel(it.toDouble()) }
                        }
                        NumericSetting.MEAN_SEA_LEVEL_ACCURACY -> {
                            useValue = useMeanSeaLevelAccuracy
                            value = meanSeaLevelAccuracy
                            onUseValueChange = viewModel::setUseMeanSeaLevelAccuracy
                            onValueChange = viewModel::setMeanSeaLevelAccuracy
                        }
                        NumericSetting.SPEED -> {
                            useValue = useSpeed
                            value = speed
                            onUseValueChange = viewModel::setUseSpeed
                            onValueChange = viewModel::setSpeed
                        }
                        NumericSetting.SPEED_ACCURACY -> {
                            useValue = useSpeedAccuracy
                            value = speedAccuracy
                            onUseValueChange = viewModel::setUseSpeedAccuracy
                            onValueChange = viewModel::setSpeedAccuracy
                        }
                    }

                    NumericSettingItem(
                        setting = setting,
                        useValue = useValue,
                        onUseValueChange = onUseValueChange,
                        value = value,
                        onValueChange = onValueChange
                    )

                    if (index != category.settings.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
    }
}

/**
 * A single numeric setting row driven by [setting]'s static metadata. Shows an enable switch and,
 * when enabled, a formatted value with +/- steppers and a slider bounded by [NumericSetting.min]
 * /[NumericSetting.max].
 *
 * The slider tracks an internal [Float] that mirrors [value] (kept in sync via [LaunchedEffect]),
 * committing the final value through [onValueChange] on stepper taps and slider release.
 *
 * @param setting static metadata (titles, unit, range, step) for this row.
 * @param useValue whether this setting is currently enabled.
 * @param onUseValueChange invoked when the enable switch is toggled.
 * @param value current committed value, in [setting]'s unit.
 * @param onValueChange invoked with the new value when the user adjusts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumericSettingItem(
    setting: NumericSetting,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val title = stringResource(setting.titleRes)
    val description = stringResource(setting.descriptionRes)
    val label = stringResource(setting.labelRes)
    val unit = setting.unit
    val minValue = setting.min
    val maxValue = setting.max
    val step = setting.step

    var showTooltip by remember { mutableStateOf(false) }
    val moreInfoDescription = stringResource(R.string.setting_more_info, title)
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)
    val adjustDescription = stringResource(R.string.setting_adjust_value, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = { showTooltip = !showTooltip },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = moreInfoDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showTooltip) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                    )
                }
            }

            Switch(
                checked = useValue,
                onCheckedChange = onUseValueChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (useValue) disableDescription else enableDescription
                }
            )
        }

        if (useValue) {
            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

            var sliderValue by remember { mutableFloatStateOf(value) }

            LaunchedEffect(value) {
                if (sliderValue != value) sliderValue = value
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_SMALL),
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayText = stringResource(
                    R.string.setting_value_display,
                    label,
                    "%.2f".format(sliderValue),
                    unit
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue - step).coerceAtLeast(minValue)
                        sliderValue = newValue
                        onValueChange(newValue)
                    },
                    enabled = sliderValue > minValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "−",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue + step).coerceAtMost(maxValue)
                        sliderValue = newValue
                        onValueChange(newValue)
                    },
                    enabled = sliderValue < maxValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "+",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.SPACING_SMALL)
            ) {
                Text(
                    text = "${minValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = minValue..maxValue,
                steps = ((maxValue - minValue) / step).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = adjustDescription }
            )
        }
    }
}
