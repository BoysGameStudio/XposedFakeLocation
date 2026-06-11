package com.noobexon.xposedfakelocation.xposed.hooks

import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

// TODO: in all hooks, we should check every 3 seconds if we are still in scope. isPlaying is not enough.

class LocationApiHooks(private val module: XposedInterface, private val classLoader: ClassLoader) {
    private val tag = "[LocationApiHooks]"

    fun init() {
        hookLocation()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookLocation() {
        runCatching {
            val locationClass = Class.forName("android.location.Location", false, classLoader)

            with(locationClass) {
                hookMethod("getLatitude", enabled = { true }) { LocationUtil.latitude }
                hookMethod("getLongitude", enabled = { true }) { LocationUtil.longitude }
                hookMethod("getAccuracy", enabled = { PreferencesUtil.getUseAccuracy() == true }) { LocationUtil.accuracy }
                hookMethod("getAltitude", enabled = { PreferencesUtil.getUseAltitude() == true }) { LocationUtil.altitude }
                hookMethod("getVerticalAccuracyMeters", enabled = { PreferencesUtil.getUseVerticalAccuracy() == true }) { LocationUtil.verticalAccuracy }
                hookMethod("getSpeed", enabled = { PreferencesUtil.getUseSpeed() == true }) { LocationUtil.speed }
                hookMethod("getSpeedAccuracyMetersPerSecond", enabled = { PreferencesUtil.getUseSpeedAccuracy() == true }) { LocationUtil.speedAccuracy }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    hookMethod("getMslAltitudeMeters", enabled = { PreferencesUtil.getUseMeanSeaLevel() == true }) { LocationUtil.meanSeaLevel }
                    hookMethod("getMslAltitudeAccuracyMeters", enabled = { PreferencesUtil.getUseMeanSeaLevelAccuracy() == true }) { LocationUtil.meanSeaLevelAccuracy }
                } else {
                    module.log(Log.INFO, tag, "getMslAltitudeMeters() and getMslAltitudeAccuracyMeters() not available on this API level")
                }
            }
        }.onFailure { module.log(Log.ERROR, tag, "Error hooking Location class - ${it.message}") }
    }

    private fun Class<*>.hookMethod(
        methodName: String,
        enabled: () -> Boolean,
        spoofed: () -> Any?,
    ) {
        module.hook(getDeclaredMethod(methodName)).intercept { chain ->
            val original = chain.proceed()
            LocationUtil.updateLocation()
            module.log(Log.INFO, tag, "Leaving $methodName\n\tOriginal: $original")
            if (PreferencesUtil.getIsPlaying() == true && enabled()) {
                val value = spoofed()
                module.log(Log.INFO, tag, "\tModified to: $value")
                value
            } else {
                original
            }
        }
    }
}
