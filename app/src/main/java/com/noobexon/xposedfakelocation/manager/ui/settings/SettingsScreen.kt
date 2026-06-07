package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.launch
import java.util.Locale

private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 12.dp
    val CARD_ELEVATION = 2.dp
}

/**
 * Top-level settings screen. Branded top bar with search + an overflow (reset) menu; a unified,
 * searchable list of [SettingEntry]s grouped by [SettingsCategory]. Collects one-shot
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var restartDialogEnabled by remember { mutableStateOf<Boolean?>(null) } // null = hidden; true = enabled; false = disabled

    // Collapse search on Back before leaving the screen.
    BackHandler(enabled = searchActive) {
        searchActive = false
        query = ""
    }

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

    // --- Collect all preference state ---
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsStateWithLifecycle().value)
    val hideToast by settingsViewModel.hideFakeLocationToast.collectAsStateWithLifecycle()
    val broadcast by settingsViewModel.enableBroadcastControl.collectAsStateWithLifecycle()
    val systemHooks by settingsViewModel.enableSystemHooks.collectAsStateWithLifecycle()
    val useRandomize by settingsViewModel.useRandomize.collectAsStateWithLifecycle()
    val randomizeRadius by settingsViewModel.randomizeRadius.collectAsStateWithLifecycle()
    val useAccuracy by settingsViewModel.useAccuracy.collectAsStateWithLifecycle()
    val accuracy by settingsViewModel.accuracy.collectAsStateWithLifecycle()
    val useVerticalAccuracy by settingsViewModel.useVerticalAccuracy.collectAsStateWithLifecycle()
    val verticalAccuracy by settingsViewModel.verticalAccuracy.collectAsStateWithLifecycle()
    val useAltitude by settingsViewModel.useAltitude.collectAsStateWithLifecycle()
    val altitude by settingsViewModel.altitude.collectAsStateWithLifecycle()
    val useMeanSeaLevel by settingsViewModel.useMeanSeaLevel.collectAsStateWithLifecycle()
    val meanSeaLevel by settingsViewModel.meanSeaLevel.collectAsStateWithLifecycle()
    val useMeanSeaLevelAccuracy by settingsViewModel.useMeanSeaLevelAccuracy.collectAsStateWithLifecycle()
    val meanSeaLevelAccuracy by settingsViewModel.meanSeaLevelAccuracy.collectAsStateWithLifecycle()
    val useSpeed by settingsViewModel.useSpeed.collectAsStateWithLifecycle()
    val speed by settingsViewModel.speed.collectAsStateWithLifecycle()
    val useSpeedAccuracy by settingsViewModel.useSpeedAccuracy.collectAsStateWithLifecycle()
    val speedAccuracy by settingsViewModel.speedAccuracy.collectAsStateWithLifecycle()

    // --- Build the declarative entry list (category order = SettingsCategory order) ---
    val categories: List<Pair<SettingsCategory, List<SettingEntry>>> = listOf(
        SettingsCategory.LOCATION to listOf(
            SettingEntry.Numeric(NumericSetting.RANDOMIZE_RADIUS, useRandomize, settingsViewModel::setUseRandomize, randomizeRadius.toFloat()) { settingsViewModel.setRandomizeRadius(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.HORIZONTAL_ACCURACY, useAccuracy, settingsViewModel::setUseAccuracy, accuracy.toFloat()) { settingsViewModel.setAccuracy(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.VERTICAL_ACCURACY, useVerticalAccuracy, settingsViewModel::setUseVerticalAccuracy, verticalAccuracy, settingsViewModel::setVerticalAccuracy)
        ),
        SettingsCategory.ALTITUDE to listOf(
            SettingEntry.Numeric(NumericSetting.ALTITUDE, useAltitude, settingsViewModel::setUseAltitude, altitude.toFloat()) { settingsViewModel.setAltitude(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL, useMeanSeaLevel, settingsViewModel::setUseMeanSeaLevel, meanSeaLevel.toFloat()) { settingsViewModel.setMeanSeaLevel(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL_ACCURACY, useMeanSeaLevelAccuracy, settingsViewModel::setUseMeanSeaLevelAccuracy, meanSeaLevelAccuracy, settingsViewModel::setMeanSeaLevelAccuracy)
        ),
        SettingsCategory.MOVEMENT to listOf(
            SettingEntry.Numeric(NumericSetting.SPEED, useSpeed, settingsViewModel::setUseSpeed, speed, settingsViewModel::setSpeed),
            SettingEntry.Numeric(NumericSetting.SPEED_ACCURACY, useSpeedAccuracy, settingsViewModel::setUseSpeedAccuracy, speedAccuracy, settingsViewModel::setSpeedAccuracy)
        ),
        SettingsCategory.NOTIFICATIONS to listOf(
            SettingEntry.Switch("hide_toast", R.string.setting_hide_toast_title, R.string.setting_hide_toast_description, hideToast, settingsViewModel::setHideFakeLocationToast)
        ),
        SettingsCategory.SYSTEM_HOOKS to listOf(
            SettingEntry.Switch("system_hooks", R.string.setting_system_hooks_title, R.string.setting_system_hooks_description, systemHooks, settingsViewModel::setEnableSystemHooks)
        ),
        SettingsCategory.EXTERNAL_CONTROL to listOf(
            SettingEntry.Switch("broadcast", R.string.setting_external_broadcast_title, R.string.setting_external_broadcast_description, broadcast, settingsViewModel::setEnableBroadcastControl)
        ),
        SettingsCategory.LANGUAGE to listOf(
            SettingEntry.Language(selectedLanguage) { option ->
                settingsViewModel.setLanguageTag(option.tag)
                LocaleController.persistLanguageTag(context, option.tag)
                (context as? Activity)?.recreate()
            }
        )
    )

    val filtered = categories
        .map { (category, entries) -> category to entries.filter { entryMatches(query, searchTextOf(it, context)) } }
        .filter { it.second.isNotEmpty() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Text(stringResource(R.string.screen_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            query = ""
                        } else {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_settings))
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_reset_all)) },
                                onClick = {
                                    menuExpanded = false
                                    showResetDialog = true
                                }
                            )
                        }
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

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimensions.SPACING_LARGE),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filtered.forEach { (category, entries) ->
                        CategoryLabel(stringResource(category.titleRes))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.SPACING_SMALL),
                            shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                        ) {
                            Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                entries.forEachIndexed { index, entry ->
                                    key(entry.key) {
                                        SettingEntryRow(entry)
                                        if (index != entries.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.SPACING_LARGE))
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.settings_reset_title)) },
                    text = { Text(stringResource(R.string.settings_reset_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showResetDialog = false
                            settingsViewModel.resetToDefaults()
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_reset_done))
                            }
                        }) {
                            Text(stringResource(R.string.action_reset))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
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

/** Non-composable: the text searched for [entry] (title + description, plus language display name). */
private fun searchTextOf(entry: SettingEntry, context: Context): String {
    val base = context.getString(entry.titleRes) + " " + context.getString(entry.descriptionRes)
    return if (entry is SettingEntry.Language) {
        val option = entry.selected
        val display = if (option == LanguageOption.SYSTEM) {
            context.getString(option.labelRes)
        } else {
            option.autonym ?: context.getString(option.labelRes)
        }
        "$base $display"
    } else {
        base
    }
}

/** Case-insensitive substring match; an empty query matches everything. */
private fun entryMatches(query: String, haystack: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return haystack.contains(q, ignoreCase = true)
}

/** Dispatches a [SettingEntry] to its row composable. */
@Composable
private fun SettingEntryRow(entry: SettingEntry) {
    when (entry) {
        is SettingEntry.Switch -> BooleanSettingItem(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            checked = entry.checked,
            onCheckedChange = entry.onCheckedChange
        )
        is SettingEntry.Numeric -> NumericSettingItem(
            setting = entry.setting,
            useValue = entry.enabled,
            onUseValueChange = entry.onEnabledChange,
            value = entry.value,
            onValueChange = entry.onValueChange
        )
        is SettingEntry.Language -> LanguageSettingItem(
            selectedLanguage = entry.selected,
            onLanguageSelected = entry.onSelected
        )
    }
}

/** Compact uppercase category label in the primary color. */
@Composable
private fun CategoryLabel(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimensions.SPACING_SMALL,
            top = Dimensions.SPACING_SMALL,
            bottom = Dimensions.SPACING_EXTRA_SMALL
        )
    )
}

/**
 * Language picker row. Shows the setting title with an info tooltip on the left and the currently
 * selected language plus an edit button on the right; tapping the row or the edit button opens a
 * single-choice [LanguageSelectionDialog]. Selecting a language there applies it immediately and
 * dismisses the dialog.
 *
 * @param selectedLanguage currently active language.
 * @param onLanguageSelected invoked with the chosen option when the user picks one.
 */
@Composable
private fun LanguageSettingItem(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    val title = stringResource(R.string.setting_language_title)
    val moreInfoDescription = stringResource(R.string.setting_more_info, title)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(Dimensions.SPACING_MEDIUM)
        ) {
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

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = languageDisplayName(selectedLanguage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimensions.SPACING_SMALL)
            )

            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_change_language),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showTooltip) {
            Text(
                text = stringResource(R.string.setting_language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Dimensions.SPACING_MEDIUM,
                    end = Dimensions.SPACING_MEDIUM,
                    bottom = Dimensions.SPACING_MEDIUM
                )
            )
        }
    }

    if (showDialog) {
        LanguageSelectionDialog(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = {
                onLanguageSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Single-choice language dialog. Lists every [LanguageOption] as a radio row showing the language
 * in its own script (autonym) with the current-UI-language name as a subtitle when it differs;
 * [LanguageOption.SYSTEM] shows what the device locale currently resolves to.
 *
 * @param selectedLanguage currently active language, pre-selected in the list.
 * @param onLanguageSelected invoked with the option the user taps.
 * @param onDismiss invoked when the dialog is dismissed without a selection.
 */
@Composable
private fun LanguageSelectionDialog(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_language_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    val selected = option == selectedLanguage
                    val primary: String
                    val secondary: String?
                    if (option == LanguageOption.SYSTEM) {
                        primary = stringResource(option.labelRes)
                        secondary = LocaleController.systemLanguageAutonym()
                    } else {
                        val localized = stringResource(option.labelRes)
                        primary = option.autonym ?: localized
                        secondary = localized.takeUnless { it.equals(primary, ignoreCase = true) }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_MEDIUM),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onLanguageSelected(option) }
                            )
                            .padding(vertical = Dimensions.SPACING_SMALL)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (secondary != null) {
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** The display name shown for [option] in the collapsed language row (autonym, or system label). */
@Composable
private fun languageDisplayName(option: LanguageOption): String =
    if (option == LanguageOption.SYSTEM) {
        stringResource(option.labelRes)
    } else {
        option.autonym ?: stringResource(option.labelRes)
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
 * A single numeric setting row driven by [setting]'s static metadata. Shows an enable switch and,
 * when enabled, an editable value field (with unit) plus a continuous slider bounded by
 * [NumericSetting.min]/[NumericSetting.max], with min/max end labels.
 *
 * The field and slider share an internal [Float] mirroring [value] (synced via [LaunchedEffect]).
 * Typing parses and clamps into range and commits; invalid/empty text reverts on focus loss. The
 * slider commits on value-change-finished.
 *
 * @param setting static metadata (titles, unit, range) for this row.
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
            var text by remember { mutableStateOf(formatNumericValue(value, setting)) }

            LaunchedEffect(value) {
                if (sliderValue != value) {
                    sliderValue = value
                    text = formatNumericValue(value, setting)
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    val parsed = input.toFloatOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(minValue, maxValue)
                        sliderValue = clamped
                        onValueChange(clamped)
                    }
                },
                label = { Text(label) },
                suffix = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            text = formatNumericValue(sliderValue, setting)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(Dimensions.SPACING_SMALL))

            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    text = formatNumericValue(newValue, setting)
                },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = minValue..maxValue,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = adjustDescription }
            )

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
        }
    }
}

/** Locale-stable value formatting (uses '.' so the field stays parseable by [String.toFloatOrNull]). */
private fun formatNumericValue(value: Float, setting: NumericSetting): String {
    val decimals = if (setting.step >= 1f) 0 else 1
    return String.format(Locale.US, "%.${decimals}f", value)
}
