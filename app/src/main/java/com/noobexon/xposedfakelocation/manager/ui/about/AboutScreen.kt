package com.noobexon.xposedfakelocation.manager.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.R

/**
 * Entry-point composable for the About screen.
 *
 * Collects [AboutViewModel.contributorsState] and [AboutViewModel.developerFallback] and passes
 * them down to the stateless [AboutContent]. The ViewModel is scoped to this composable so it is
 * the only correct injection point.
 *
 * @param navController Used by [AboutTopAppBar] to navigate back to the previous destination.
 * @param viewModel The ViewModel that owns the contributor-fetch lifecycle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = viewModel()
) {
    val contributorsState by viewModel.contributorsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AboutTopAppBar(navController) }
    ) { innerPadding ->
        AboutContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contributorsState = contributorsState,
            developerFallback = viewModel.developerFallback,
            onRetry = { viewModel.loadContributors(forceRefresh = true) },
        )
    }
}

/**
 * Top app bar for the About screen with a back-navigation icon.
 *
 * Uses the theme's primary colour for its background and contrasting colours for content,
 * consistent with the rest of the app's top bars.
 *
 * @param navController Used to call [NavController.navigateUp] when the back icon is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopAppBar(navController: NavController) {
    TopAppBar(
        title = { Text(stringResource(R.string.screen_about)) },
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

/**
 * Stateless layout for the About screen.
 *
 * Renders the [AppHeroSection] (app icon, name, version badge, description) followed by two
 * [AboutSectionCard]s — one for the developer and one for the contributor list. The developer
 * shown in the first card is resolved from [contributorsState] when a [ContributorsUiState.Success]
 * is available, falling back to [developerFallback] during loading or on error.
 *
 * @param modifier Applied to the root [Column]; should include `fillMaxSize` and inset padding.
 * @param contributorsState The current fetch state forwarded from [AboutViewModel.contributorsState].
 * @param developerFallback A pre-built [Contributor] displayed while the API response is pending.
 * @param onRetry Called when the user taps "Retry" after a failed fetch.
 */
@Composable
private fun AboutContent(
    modifier: Modifier = Modifier,
    contributorsState: ContributorsUiState,
    developerFallback: Contributor,
    onRetry: () -> Unit,
) {
    val developer = (contributorsState as? ContributorsUiState.Success)?.developer
        ?: developerFallback

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppHeroSection()

        AboutSectionCard(
            label = stringResource(R.string.about_developer_label),
            icon = Icons.Outlined.Person,
        ) {
            ContributorRow(developer)
        }

        AboutSectionCard(
            label = stringResource(R.string.about_contributors_label),
            icon = Icons.Outlined.Group,
        ) {
            ContributorsList(
                state = contributorsState,
                onRetry = onRetry,
            )
        }
    }
}

/**
 * Centred hero section displayed at the top of the About screen.
 *
 * Shows the launcher icon (loaded via [PackageManager] to correctly render adaptive icons on
 * API 26+), the app name, a pill-shaped version badge, and the app description.
 */
@Composable
private fun AppHeroSection() {
    val context = LocalContext.current
    val appIcon = remember { context.packageManager.getApplicationIcon(context.packageName) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = appIcon,
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * A reusable [ElevatedCard] with a labelled header row and an arbitrary [content] slot.
 *
 * The header row shows a tinted [icon] and a [label] in the theme's primary colour, separated
 * from [content] by a [HorizontalDivider]. This component is used for both the "Created by" and
 * "Contributors" sections so the two cards share a consistent visual treatment.
 *
 * @param label Text displayed in the card header (e.g. "Created by", "Contributors").
 * @param icon Icon displayed beside the label in the card header.
 * @param content The body content rendered below the divider, scoped to [ColumnScope].
 */
@Composable
private fun AboutSectionCard(
    label: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider()
        content()
    }
}

/**
 * A full-width, clickable row displaying a single [Contributor]'s avatar, name, and a trailing
 * chevron indicating that the row opens an external link.
 *
 * Tapping the row launches the contributor's GitHub profile URL in the system browser.
 *
 * @param contributor The contributor whose data is rendered in this row.
 */
@Composable
private fun ContributorRow(contributor: Contributor) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(contributor.githubUrl))
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = contributor.avatarUrl,
            contentDescription = contributor.name,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = contributor.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Renders the body of the contributors card based on the current [ContributorsUiState].
 *
 * - [ContributorsUiState.Loading] → [ContributorsLoading] (spinner + label).
 * - [ContributorsUiState.Error] → [ContributorsError] (message + retry button).
 * - [ContributorsUiState.Success] with an empty list → an empty-state message.
 * - [ContributorsUiState.Success] with contributors → a [ContributorRow] per entry, separated by
 *   inset [HorizontalDivider]s.
 *
 * @param state The current fetch state from [AboutViewModel.contributorsState].
 * @param onRetry Forwarded to [ContributorsError] as the retry callback.
 */
@Composable
private fun ContributorsList(
    state: ContributorsUiState,
    onRetry: () -> Unit,
) {
    when (val current = state) {
        is ContributorsUiState.Loading -> ContributorsLoading()
        is ContributorsUiState.Error -> ContributorsError(onRetry = onRetry)
        is ContributorsUiState.Success -> {
            if (current.contributors.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.about_contributors_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                current.contributors.forEachIndexed { index, contributor ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    ContributorRow(contributor)
                }
            }
        }
    }
}

/**
 * Inline loading indicator shown inside the contributors card while the API request is in flight.
 *
 * Displays a small [CircularProgressIndicator] alongside a descriptive text label.
 */
@Composable
private fun ContributorsLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.about_contributors_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state shown inside the contributors card when the API request fails.
 *
 * Displays a short error message and an [OutlinedButton] that triggers [onRetry], which calls
 * [AboutViewModel.loadContributors] with `forceRefresh = true`.
 *
 * @param onRetry Called when the user taps the retry button.
 */
@Composable
private fun ContributorsError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_contributors_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.about_contributors_retry))
        }
    }
}
