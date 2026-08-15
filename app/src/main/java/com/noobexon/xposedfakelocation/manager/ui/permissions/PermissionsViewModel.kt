package com.noobexon.xposedfakelocation.manager.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the permissions screen.
 *
 * @property hasPermissions `true` once [Manifest.permission.ACCESS_FINE_LOCATION] is granted.
 * @property permanentlyDenied `true` when the user has permanently denied the permission
 *   (i.e. [Activity.shouldShowRequestPermissionRationale] returns `false` after a denial).
 * @property permissionsChecked `true` after the initial permission check has run; used to
 *   distinguish "not yet checked" from "checked and denied" to avoid a flash of the request UI.
 */
@Immutable
data class PermissionsUiState(
    val hasPermissions: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val permissionsChecked: Boolean = false
)

class PermissionsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    /** Checks the current [Manifest.permission.ACCESS_FINE_LOCATION] grant status. */
    fun checkPermissions(context: Context) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.update {
            it.copy(
                hasPermissions = granted || coarseLocationGranted,
                permissionsChecked = true
            )
        }
    }

    /** Updates the permission grant state after the system permission dialog returns. */
    fun updatePermissionsStatus(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
    }

    /**
     * Updates the permission grant state from a multi-permission request result.
     * Permissions are considered granted when either fine or coarse location is granted.
     */
    fun updatePermissionsStatus(grants: Map<String, Boolean>) {
        _uiState.update {
            it.copy(
                hasPermissions = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            )
        }
    }

    /**
     * Sets [PermissionsUiState.permanentlyDenied] based on whether the system would show a
     * rationale. Call this when the permission dialog returns a denial.
     */
    fun checkIfPermanentlyDenied(activity: Activity) {
        val shouldShowRationale = activity.shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        _uiState.update { it.copy(permanentlyDenied = !shouldShowRationale) }
    }
}

/**
 * The baseline features require fine/coarse location plus nearby Wi-Fi devices (Android 13+,
 * for Wi-Fi RTT-based positioning).
 */
internal fun requiredBaselineRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return permissions.toTypedArray()
}
