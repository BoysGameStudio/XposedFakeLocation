package com.noobexon.xposedfakelocation.xposed.hooks

import android.app.Application
import android.location.Location
import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

class LocationApiHooks(private val module: XposedInterface, private val classLoader: ClassLoader) {
    private val tag = "[LocationApiHooks]"

    fun initHooks() {
        hookLocation()
        hookLocationManager()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookLocation() {
        try {
            val locationClass = Class.forName("android.location.Location", false, classLoader)

            module.hook(locationClass.getDeclaredMethod("getLatitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getLatitude()")
                if (shouldSpoofLocationApi()) {
                    module.log(Log.INFO, tag, "\t Returning spoofed latitude.")
                    LocationUtil.latitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getLongitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getLongitude()")
                if (shouldSpoofLocationApi()) {
                    module.log(Log.INFO, tag, "\t Returning spoofed longitude.")
                    LocationUtil.longitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAccuracy")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getAccuracy()")
                if (shouldSpoofLocationApi() && PreferencesUtil.getUseAccuracy() == true) {
                    module.log(Log.INFO, tag, "\t Returning spoofed accuracy.")
                    LocationUtil.accuracy
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getAltitude")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getAltitude()")
                if (shouldSpoofLocationApi() && PreferencesUtil.getUseAltitude() == true) {
                    module.log(Log.INFO, tag, "\t Returning spoofed altitude.")
                    LocationUtil.altitude
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getVerticalAccuracyMeters")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getVerticalAccuracyMeters()")
                if (shouldSpoofLocationApi() && PreferencesUtil.getUseVerticalAccuracy() == true) {
                    module.log(Log.INFO, tag, "\tReturning spoofed vertical accuracy.")
                    LocationUtil.verticalAccuracy
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeed")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getSpeed()")
                if (shouldSpoofLocationApi() && PreferencesUtil.getUseSpeed() == true) {
                    module.log(Log.INFO, tag, "\tReturning spoofed speed.")
                    LocationUtil.speed
                } else {
                    original
                }
            }

            module.hook(locationClass.getDeclaredMethod("getSpeedAccuracyMetersPerSecond")).intercept { chain ->
                val original = chain.proceed()
                LocationUtil.updateLocation()
                module.log(Log.INFO, tag, "Leaving method getSpeedAccuracyMetersPerSecond()")
                if (shouldSpoofLocationApi() && PreferencesUtil.getUseSpeedAccuracy() == true) {
                    module.log(Log.INFO, tag, "\tReturning spoofed speed accuracy.")
                    LocationUtil.speedAccuracy
                } else {
                    original
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                module.hook(locationClass.getDeclaredMethod("getMslAltitudeMeters")).intercept { chain ->
                    val original = chain.proceed()
                    LocationUtil.updateLocation()
                    module.log(Log.INFO, tag, "Leaving method getMslAltitudeMeters()")
                    if (shouldSpoofLocationApi() && PreferencesUtil.getUseMeanSeaLevel() == true) {
                        module.log(Log.INFO, tag, "\tReturning spoofed MSL altitude.")
                        LocationUtil.meanSeaLevel
                    } else {
                        original
                    }
                }

                module.hook(locationClass.getDeclaredMethod("getMslAltitudeAccuracyMeters")).intercept { chain ->
                    val original = chain.proceed()
                    LocationUtil.updateLocation()
                    module.log(Log.INFO, tag, "Leaving method getMslAltitudeAccuracyMeters()")
                    if (shouldSpoofLocationApi() && PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                        module.log(Log.INFO, tag, "\tReturning spoofed MSL altitude accuracy.")
                        LocationUtil.meanSeaLevelAccuracy
                    } else {
                        original
                    }
                }
            } else {
                module.log(Log.INFO, tag, "getMslAltitudeMeters() and getMslAltitudeAccuracyMeters() not available on this API level")
            }

        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking Location class - ${e.message}")
        }
    }

    private fun hookLocationManager() {
        try {
            val locationManagerClass = Class.forName("android.location.LocationManager", false, classLoader)
            val method = locationManagerClass.getDeclaredMethod("getLastKnownLocation", String::class.java)

            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Location
                module.log(Log.INFO, tag, "Leaving method getLastKnownLocation(provider)")
                val provider = chain.getArg(0) as String
                module.log(Log.INFO, tag, "\t Requested data from: $provider")
                if (shouldSpoofLocationApi()) {
                    val fakeLocation = LocationUtil.createFakeLocation(provider = provider)
                    module.log(Log.INFO, tag, "\t Returning spoofed location.")
                    fakeLocation
                } else {
                    original
                }
            }

        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking LocationManager - ${e.message}")
        }
    }

    private fun shouldSpoofLocationApi(): Boolean {
        if (PreferencesUtil.getIsPlaying() != true) return false
        val processName = runCatching { Application.getProcessName() }.getOrNull()
        return processName != MANAGER_APP_PACKAGE_NAME && processName?.startsWith("$MANAGER_APP_PACKAGE_NAME:") != true
    }
}
