package com.noobexon.xposedfakelocation.manager.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation


@Composable
fun FavoritesScreen(
    navController: NavController,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    favoritesViewModel: FavoritesViewModel = viewModel()
) {
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()

    FavoritesContent(
        favorites = favorites,
        onFavoriteClick = { favorite ->
            onFavoriteSelected(favorite)
            navController.navigateUp()
        },
        onDelete = { favorite -> favoritesViewModel.removeFavorite(favorite) },
        onEdit = { old, new -> favoritesViewModel.updateFavorite(old, new) },
        onNavigateUp = { navController.navigateUp() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesContent(
    favorites: List<FavoriteLocation>,
    onFavoriteClick: (FavoriteLocation) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    onEdit: (old: FavoriteLocation, new: FavoriteLocation) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var deletePending by remember { mutableStateOf<FavoriteLocation?>(null) }
    var editPending by remember { mutableStateOf<FavoriteLocation?>(null) }

    deletePending?.let { favorite ->
        DeleteConfirmationDialog(
            favoriteName = favorite.name,
            onConfirm = {
                onDelete(favorite)
                deletePending = null
            },
            onDismiss = { deletePending = null },
        )
    }

    editPending?.let { favorite ->
        EditFavoriteDialog(
            favorite = favorite,
            onSave = { updated ->
                onEdit(favorite, updated)
                editPending = null
            },
            onDismiss = { editPending = null },
        )
    }

    Scaffold(
        topBar = { FavoritesTopAppBar(onNavigateUp) }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            FavoritesEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = favorites,
                    key = { "${it.name}_${it.latitude}_${it.longitude}" }
                ) { favorite ->
                    FavoriteItem(
                        favorite = favorite,
                        onClick = { onFavoriteClick(favorite) },
                        onEditClick = { editPending = favorite },
                        onDeleteClick = { deletePending = favorite },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTopAppBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.screen_favorites)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favorites_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.favorites_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteItem(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (favorite.description.isNotBlank()) {
                    Text(
                        text = favorite.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(
                        R.string.coordinates_lat_lon,
                        "%.5f".format(favorite.latitude),
                        "%.5f".format(favorite.longitude)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_named_item, favorite.name),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_named_item, favorite.name),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EditFavoriteDialog(
    favorite: FavoriteLocation,
    onSave: (FavoriteLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val latRange = -90.0..90.0
    val lonRange = -180.0..180.0

    var name by remember { mutableStateOf(favorite.name) }
    var description by remember { mutableStateOf(favorite.description) }
    var latitude by remember { mutableStateOf(favorite.latitude.toString()) }
    var longitude by remember { mutableStateOf(favorite.longitude.toString()) }

    var nameError by remember { mutableStateOf(false) }
    var latitudeError by remember { mutableStateOf(false) }
    var longitudeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorites_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.validation_name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        latitudeError = it.toDoubleOrNull()?.let { v -> v !in latRange } != false
                    },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    isError = latitudeError,
                    supportingText = if (latitudeError) {
                        { Text(stringResource(R.string.validation_latitude_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        longitudeError = it.toDoubleOrNull()?.let { v -> v !in lonRange } != false
                    },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    isError = longitudeError,
                    supportingText = if (longitudeError) {
                        { Text(stringResource(R.string.validation_longitude_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latVal = latitude.toDoubleOrNull()
                    val lonVal = longitude.toDoubleOrNull()
                    nameError = name.isBlank()
                    latitudeError = latVal == null || latVal !in latRange
                    longitudeError = lonVal == null || lonVal !in lonRange
                    if (!nameError && !latitudeError && !longitudeError) {
                        onSave(
                            favorite.copy(
                                name = name.trim(),
                                description = description.trim(),
                                latitude = latVal!!,
                                longitude = lonVal!!,
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    favoriteName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.favorites_delete_title)) },
        text = { Text(stringResource(R.string.favorites_delete_message, favoriteName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.favorites_delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
