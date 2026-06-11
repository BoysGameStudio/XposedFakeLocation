package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.Location
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

class LocationManagerApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationManagerApiHooks]"

    fun initHooks() {
        hookLocationManager()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookLocationManager() {
        try {
            val locationManagerClass = Class.forName("android.location.LocationManager", false, classLoader)
            val method = locationManagerClass.getDeclaredMethod("getLastKnownLocation", String::class.java)

            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Location
                module.log(Log.INFO, tag, "Leaving method getLastKnownLocation(provider)")
                module.log(Log.INFO, tag, "\t Original location: $original")
                val provider = chain.getArg(0) as String
                module.log(Log.INFO, tag, "\t Requested data from: $provider")
                if (PreferencesUtil.getIsPlaying() == true) {
                    val fakeLocation = LocationUtil.createFakeLocation(provider = provider)
                    module.log(Log.INFO, tag, "\t Modified location: $fakeLocation")
                    fakeLocation
                } else {
                    original
                }
            }

        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking LocationManager - ${e.message}")
        }
    }
}
