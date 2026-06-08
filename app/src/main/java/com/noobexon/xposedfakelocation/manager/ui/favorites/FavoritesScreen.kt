package com.noobexon.xposedfakelocation.manager.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
        onNavigateUp = { navController.navigateUp() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesContent(
    favorites: List<FavoriteLocation>,
    onFavoriteClick: (FavoriteLocation) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { FavoritesTopAppBar(onNavigateUp) }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.favorites_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(favorites) { favorite ->
                    FavoriteItem(
                        favorite = favorite,
                        onClick = { onFavoriteClick(favorite) },
                        onDelete = { onDelete(favorite) },
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
private fun FavoriteItem(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text(favorite.name) },
            supportingContent = {
                Text(
                    text = stringResource(
                        R.string.coordinates_lat_lon,
                        favorite.latitude.toString(),
                        favorite.longitude.toString()
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete)
                    )
                }
            }
        )
    }
}
