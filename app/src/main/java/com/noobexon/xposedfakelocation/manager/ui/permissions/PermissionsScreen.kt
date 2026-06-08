package com.noobexon.xposedfakelocation.manager.ui.permissions

import android.Manifest
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen

@Composable
fun PermissionsScreen(navController: NavController, permissionsViewModel: PermissionsViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity

    if (activity == null) {
        Text(stringResource(R.string.permissions_activity_error))
        return
    }

    val hasPermissions by permissionsViewModel.hasPermissions
    val permanentlyDenied by permissionsViewModel.permanentlyDenied
    val permissionsChecked by permissionsViewModel.permissionsChecked

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            permissionsViewModel.updatePermissionsStatus(granted)
            if (granted) {
                navController.navigate(Screen.Map.route) {
                    popUpTo(Screen.Permissions.route) { inclusive = true }
                }
            } else {
                permissionsViewModel.checkIfPermanentlyDenied(activity)
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionsViewModel.checkPermissions(context)
        if (hasPermissions) {
            navController.navigate(Screen.Map.route) {
                popUpTo(Screen.Permissions.route) { inclusive = true }
            }
        }
    }

    if (!permissionsChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (!hasPermissions) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (permanentlyDenied) {
                    PermanentlyDeniedScreen(context)
                } else {
                    PermissionRequestScreen {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            }
        }
    }
}

@Composable
fun PermanentlyDeniedScreen(context: Context) {
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

@Composable
fun PermissionRequestScreen(onGrantPermission: () -> Unit) {
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
