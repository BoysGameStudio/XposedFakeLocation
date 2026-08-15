package com.noobexon.xposedfakelocation.manager.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen

/**
 * Entry-point composable for the permissions gate.
 *
 * On every [Lifecycle.Event.ON_RESUME] (including the initial one, which Android replays
 * synchronously when the observer is registered) it re-checks whether
 * [Manifest.permission.ACCESS_FINE_LOCATION] is granted and reacts accordingly:
 *
 * - **Not yet checked** — shows a full-screen [CircularProgressIndicator].
 * - **Checked, granted** — navigates to [Screen.Map], clearing this screen from the back stack.
 * - **Checked, denied once** — shows [PermissionRequestScreen] with a rationale and a grant button.
 * - **Permanently denied** — shows [PermanentlyDeniedScreen] with a button to open app Settings.
 *   When the user grants the permission there and returns, [Lifecycle.Event.ON_RESUME] fires
 *   again, the check passes, and navigation happens automatically.
 *
 * @param navController Used to navigate forward to [Screen.Map] once permission is granted.
 * @param permissionsViewModel Injected automatically by [viewModel]; can be overridden in tests.
 */
@Composable
fun PermissionsScreen(
    navController: NavController,
    permissionsViewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity

    val uiState by permissionsViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            permissionsViewModel.updatePermissionsStatus(grants)
            if (permissionsViewModel.uiState.value.hasPermissions) {
                navController.navigate(Screen.Map.route) {
                    popUpTo(Screen.Permissions.route) { inclusive = true }
                }
            } else {
                permissionsViewModel.checkIfPermanentlyDenied(activity)
            }
        }
    )

    // Re-check permissions every time the screen resumes. addObserver replays ON_RESUME
    // synchronously if the lifecycle is already in RESUMED state, so this also covers the
    // initial load. When the user returns from the Settings app after granting the permission
    // manually, the next ON_RESUME triggers a fresh check and the navigation effect below fires.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsViewModel.checkPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Navigate as soon as permissions are confirmed granted. Keyed on both fields so the
    // effect restarts with fresh values whenever either changes — avoiding the stale-capture
    // bug that would occur if this logic were inlined in LaunchedEffect(Unit).
    LaunchedEffect(uiState.permissionsChecked, uiState.hasPermissions) {
        if (uiState.permissionsChecked && uiState.hasPermissions) {
            navController.navigate(Screen.Map.route) {
                popUpTo(Screen.Permissions.route) { inclusive = true }
            }
        }
    }

    if (!uiState.permissionsChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (!uiState.hasPermissions) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (uiState.permanentlyDenied) {
                    PermanentlyDeniedScreen(context)
                } else {
                    PermissionRequestScreen {
                        permissionLauncher.launch(requiredBaselineRuntimePermissions())
                    }
                }
            }
        }
    }
}

/**
 * Shown when the user has permanently denied [Manifest.permission.ACCESS_FINE_LOCATION].
 *
 * Displays an explanation and a button that deep-links to the app's system Settings page so the
 * user can grant the permission manually. [PermissionsScreen] re-checks on the next
 * [Lifecycle.Event.ON_RESUME] and navigates forward automatically if the permission is granted.
 *
 * @param context Used to open the Settings intent and resolve the app's package name.
 */
@Composable
private fun PermanentlyDeniedScreen(context: Context) {
    Text(
        text = stringResource(R.string.permissions_permanently_denied),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }) {
        Text(stringResource(R.string.permissions_open_settings))
    }
}

/**
 * Shown when [Manifest.permission.ACCESS_FINE_LOCATION] has been denied but not permanently.
 *
 * Displays a rationale and a button that triggers the system permission dialog.
 *
 * @param onGrantPermission Called when the user taps the grant button; should launch the
 *   [ActivityResultContracts.RequestPermission] launcher for [Manifest.permission.ACCESS_FINE_LOCATION].
 */
@Composable
private fun PermissionRequestScreen(onGrantPermission: () -> Unit) {
    Text(
        text = stringResource(R.string.permissions_required),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onGrantPermission) {
        Text(stringResource(R.string.permissions_grant))
    }
}
