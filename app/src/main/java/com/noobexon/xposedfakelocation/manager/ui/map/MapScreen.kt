package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Context
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.manager.ui.drawer.DrawerContent
import com.noobexon.xposedfakelocation.manager.ui.map.components.AddToFavoritesDialog
import com.noobexon.xposedfakelocation.manager.ui.map.components.GoToPointDialog
import com.noobexon.xposedfakelocation.manager.ui.map.components.MapViewContainer
import com.noobexon.xposedfakelocation.manager.ui.map.components.SaveLocationProfileTitleDialog
import com.noobexon.xposedfakelocation.manager.ui.map.components.SavedLocationProfilesDialog
import com.noobexon.xposedfakelocation.manager.ui.map.components.SignalBaselineDetailsDialog
import com.noobexon.xposedfakelocation.manager.ui.map.components.SimulationDataDialog
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val showGoToPointDialog = uiState.goToPointDialogState == DialogState.Visible
    val showAddToFavoritesDialog = uiState.addToFavoritesDialogState == DialogState.Visible
    val showSignalBaselineDetailsDialog = uiState.signalBaselineDetailsDialogState == DialogState.Visible
    val showSavedLocationProfilesDialog = uiState.savedLocationProfilesDialogState == DialogState.Visible
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSimulationDataDialog by remember { mutableStateOf(false) }
    var showSaveLocationProfileTitleDialog by remember { mutableStateOf(false) }
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)
    val activeLocationProfileLabel = uiState.activeLocationProfileLabel
    val simulationStatusText = when {
        isPlaying && uiState.signalBaseline != null && activeLocationProfileLabel != null -> {
            stringResource(R.string.map_simulation_status_real_environment_active_named, activeLocationProfileLabel)
        }
        isPlaying && uiState.signalBaseline != null -> stringResource(R.string.map_simulation_status_real_environment_active)
        isPlaying -> stringResource(R.string.map_simulation_status_coordinate_active)
        uiState.signalBaseline != null && activeLocationProfileLabel != null -> {
            stringResource(R.string.map_simulation_status_real_environment_ready_named, activeLocationProfileLabel)
        }
        uiState.signalBaseline != null -> stringResource(R.string.map_simulation_status_real_environment_ready)
        else -> stringResource(R.string.map_simulation_status_inactive)
    }
    val showSimulationStatus = isPlaying || uiState.signalBaseline != null
    val exportProfilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val message = mapViewModel.exportSavedLocationProfiles(it)
                Toast.makeText(context, context.getToastText(message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importProfilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val message = mapViewModel.importSavedLocationProfiles(it)
                Toast.makeText(context, context.getToastText(message), Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerContent = {
            DrawerContent(
                onCloseDrawer = { scope.launch { drawerState.close() } },
                navController = navController
            )
        },
        scrimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        drawerState = drawerState,
        gesturesEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = { mapViewModel.triggerCenterMapEvent() }) {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = stringResource(R.string.cd_center))
                        }
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_options))
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationSearching,
                                        contentDescription = stringResource(R.string.map_go_to_point)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_go_to_point)) },
                                onClick = {
                                    showOptionsMenu = false
                                    mapViewModel.showGoToPointDialog()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.map_add_to_favorites)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_add_to_favorites)) },
                                onClick = {
                                    showOptionsMenu = false
                                    mapViewModel.showAddToFavoritesDialog()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(R.string.map_save_signal_baseline)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_save_signal_baseline)) },
                                onClick = {
                                    showOptionsMenu = false
                                    showSaveLocationProfileTitleDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.map_clear_signal_baseline)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_clear_signal_baseline)) },
                                onClick = {
                                    showOptionsMenu = false
                                    scope.launch {
                                        val message = mapViewModel.clearRealEnvironmentBaseline()
                                        Toast.makeText(context, context.getToastText(message), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.EditLocationAlt,
                                        contentDescription = stringResource(R.string.map_select_signal_baseline)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_select_signal_baseline)) },
                                onClick = {
                                    showOptionsMenu = false
                                    mapViewModel.showSavedLocationProfilesDialog()
                                },
                                enabled = uiState.savedLocationProfiles.isNotEmpty()
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(R.string.map_export_location_profiles)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_export_location_profiles)) },
                                onClick = {
                                    showOptionsMenu = false
                                    try {
                                        exportProfilesLauncher.launch("xposedfakelocation-location-profiles.json")
                                    } catch (exception: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.toast_location_profiles_export_failed, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = uiState.savedLocationProfiles.isNotEmpty()
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = stringResource(R.string.map_import_location_profiles)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_import_location_profiles)) },
                                onClick = {
                                    showOptionsMenu = false
                                    try {
                                        importProfilesLauncher.launch(arrayOf("application/json", "text/*"))
                                    } catch (exception: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.toast_location_profiles_import_failed, Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = stringResource(R.string.screen_favorites)
                                    )
                                },
                                text = { Text(stringResource(R.string.screen_favorites)) },
                                onClick = {
                                    showOptionsMenu = false
                                    navController.navigate(Screen.Favorites.route)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.map_clear_location)
                                    )
                                },
                                text = { Text(stringResource(R.string.map_clear_location)) },
                                onClick = {
                                    showOptionsMenu = false
                                    mapViewModel.updateClickedLocation(null)
                                },
                                enabled = isFabClickable
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (isFabClickable) {
                            val wasPlaying = uiState.isPlaying
                            mapViewModel.togglePlaying()
                            Toast.makeText(
                                context,
                                if (!wasPlaying) fakeLocationSet else fakeLocationUnset,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    containerColor = if (isFabClickable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    contentColor = if (isFabClickable) {
                        contentColorFor(MaterialTheme.colorScheme.primary)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = if (isFabClickable) 6.dp else 0.dp,
                        pressedElevation = if (isFabClickable) 12.dp else 0.dp
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.cd_stop)
                        } else {
                            stringResource(R.string.cd_play)
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MapViewContainer(mapViewModel)
                if (showSimulationStatus) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clickable {
                                showSimulationDataDialog = true
                            },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = simulationStatusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (showGoToPointDialog) {
            GoToPointDialog(
                onDismissRequest = { mapViewModel.hideGoToPointDialog() },
                onGoToPoint = { latitude, longitude ->
                    mapViewModel.goToPoint(latitude, longitude)
                    mapViewModel.hideGoToPointDialog()
                },
                mapViewModel = mapViewModel
            )
        }

        if (showAddToFavoritesDialog) {
            val lastClickedLocation = uiState.lastClickedLocation

            LaunchedEffect(lastClickedLocation) {
                mapViewModel.prefillCoordinatesFromMarker(
                    lastClickedLocation?.latitude,
                    lastClickedLocation?.longitude
                )
            }

            AddToFavoritesDialog(
                mapViewModel = mapViewModel,
                onDismissRequest = { mapViewModel.hideAddToFavoritesDialog() },
                onAddFavorite = { name, latitude, longitude ->
                    val favorite = FavoriteLocation(name, latitude, longitude)
                    mapViewModel.addFavoriteLocation(favorite)
                    mapViewModel.hideAddToFavoritesDialog()
                }
            )
        }

        if (showSignalBaselineDetailsDialog) {
            SignalBaselineDetailsDialog(
                baseline = uiState.signalBaseline,
                onDismissRequest = { mapViewModel.hideSignalBaselineDetailsDialog() }
            )
        }

        if (showSimulationDataDialog) {
            SimulationDataDialog(
                baseline = uiState.signalBaseline,
                coordinate = uiState.lastClickedLocation,
                onDismissRequest = { showSimulationDataDialog = false }
            )
        }

        if (showSaveLocationProfileTitleDialog) {
            SaveLocationProfileTitleDialog(
                onDismissRequest = { showSaveLocationProfileTitleDialog = false },
                onSaveTitle = { title ->
                    showSaveLocationProfileTitleDialog = false
                    scope.launch {
                        val message = mapViewModel.saveRealEnvironmentBaseline(title)
                        Toast.makeText(context, context.getToastText(message), Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (showSavedLocationProfilesDialog) {
            SavedLocationProfilesDialog(
                profiles = uiState.savedLocationProfiles,
                selectedProfile = uiState.selectedSavedLocationProfile,
                onSelectProfile = mapViewModel::selectSavedLocationProfile,
                onBackToList = mapViewModel::showSavedLocationProfilesList,
                onUseProfile = { profile ->
                    scope.launch {
                        val message = mapViewModel.useSavedLocationProfile(profile)
                        Toast.makeText(context, context.getToastText(message), Toast.LENGTH_SHORT).show()
                        mapViewModel.hideSavedLocationProfilesDialog()
                    }
                },
                onRenameProfile = { profile, title ->
                    scope.launch {
                        val message = mapViewModel.renameSavedLocationProfile(profile, title)
                        Toast.makeText(context, context.getToastText(message), Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteProfile = { profile ->
                    scope.launch {
                        val message = mapViewModel.deleteSavedLocationProfile(profile)
                        Toast.makeText(context, context.getToastText(message), Toast.LENGTH_SHORT).show()
                        mapViewModel.showSavedLocationProfilesList()
                    }
                },
                onDismissRequest = { mapViewModel.hideSavedLocationProfilesDialog() }
            )
        }
    }
}

private fun Context.getToastText(message: SignalBaselineToastMessage): String {
    return if (message.formatArgs.isEmpty()) {
        getString(message.messageRes)
    } else {
        getString(message.messageRes, *message.formatArgs.toTypedArray())
    }
}
