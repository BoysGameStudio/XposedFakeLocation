package com.noobexon.xposedfakelocation.manager.ui.permissions

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat

class PermissionsViewModel : ViewModel() {

    private val _hasPermissions = mutableStateOf(false)
    val hasPermissions: State<Boolean> get() = _hasPermissions

    private val _permanentlyDenied = mutableStateOf(false)
    val permanentlyDenied: State<Boolean> get() = _permanentlyDenied

    private val _permissionsChecked = mutableStateOf(false)
    val permissionsChecked: State<Boolean> get() = _permissionsChecked

    fun checkPermissions(context: Context) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _hasPermissions.value = fineLocationGranted || coarseLocationGranted
        _permissionsChecked.value = true
    }

    fun updatePermissionsStatus(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun updatePermissionsStatus(grants: Map<String, Boolean>) {
        _hasPermissions.value = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun checkIfPermanentlyDenied(activity: Activity) {
        val shouldShowRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        _permanentlyDenied.value = !shouldShowRationale
    }
}

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
