package com.noobexon.xposedfakelocation.manager.ui.permissions

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PermissionsViewModelTest {
    @Test
    fun baselineRuntimePermissionsBeforeAndroid13UseLocationOnly() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            requiredBaselineRuntimePermissions(Build.VERSION_CODES.S_V2)
        )
    }

    @Test
    fun baselineRuntimePermissionsOnAndroid13PlusIncludeNearbyWifi() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            requiredBaselineRuntimePermissions(Build.VERSION_CODES.TIRAMISU)
        )
    }
}
