package com.noobexon.xposedfakelocation.xposed

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.noobexon.xposedfakelocation.data.REMOTE_PREFS_GROUP
import com.noobexon.xposedfakelocation.xposed.hooks.LocationApiHooks
import com.noobexon.xposedfakelocation.xposed.hooks.LocationManagerApiHooks
import com.noobexon.xposedfakelocation.xposed.hooks.PhoneServicesHooks
import com.noobexon.xposedfakelocation.xposed.hooks.SystemServicesHooks
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleEntry : XposedModule() {
    companion object {
        const val TAG = "[ModuleEntry]"
    }

    private var locationApiHooks: LocationApiHooks? = null
    private var locationManagerApiHooks: LocationManagerApiHooks? = null
    private var systemServicesHooks: SystemServicesHooks? = null
    private var phoneServicesHooks: PhoneServicesHooks? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName}")
        initLoggers()
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        log(Log.INFO, TAG, "onPackageLoaded: ${param.packageName}")
        initRemotePreferences()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "onPackageReady: ${param.packageName}")

        if (!param.isFirstPackage) return // Run per-package setup only once.

        if (param.packageName == "com.android.phone") {
            // Telephony process: only the cell/Wi-Fi telephony spoofing belongs here. We deliberately
            // skip LocationApiHooks so we don't fake com.android.phone's own location requests.
            initPhoneServiceHooks(param.classLoader)
        } else {
            initHooks(param.classLoader)
            if (PreferencesUtil.getHideFakeLocationToast() != true) {
                showActiveToast(param)
            }
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting: ${param.classLoader}")
        initRemotePreferences()
        initSystemHooks(param.classLoader)
    }

    private fun initLoggers() {
        LocationUtil.logger = { priority, tag, message -> log(priority, tag, message) }
        PreferencesUtil.logger = { priority, tag, message -> log(priority, tag, message) }
    }

    private fun initRemotePreferences() {
        PreferencesUtil.init(getRemotePreferences(REMOTE_PREFS_GROUP))
    }

    private fun initHooks(classLoader: ClassLoader) {
        locationApiHooks = LocationApiHooks(this, classLoader).also { it.initHooks() }
        locationManagerApiHooks = LocationManagerApiHooks(this, classLoader).also { it.initHooks() }
    }

    private fun initPhoneServiceHooks(classLoader: ClassLoader) {
        phoneServicesHooks = PhoneServicesHooks(this, classLoader).also { it.initHooks() }
    }

    private fun initSystemHooks(classLoader: ClassLoader) {
        systemServicesHooks = SystemServicesHooks(this, classLoader).also { it.initHooks() }
    }

    private fun showActiveToast(param: PackageReadyParam) {
        val clazz = Class.forName("android.app.Instrumentation", false, param.classLoader)
        val method = clazz.getDeclaredMethod("callApplicationOnCreate", Application::class.java)
        hook(method).intercept { chain ->
            val result = chain.proceed()
            try {
                val context = (chain.getArg(0) as Application).applicationContext
                log(Log.INFO, TAG, "Target App's context has been acquired (${param.packageName}).")
                Toast.makeText(context, "Fake Location Is Active!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Toast/context failed - ${e.message}")
            }
            result
        }
    }
}