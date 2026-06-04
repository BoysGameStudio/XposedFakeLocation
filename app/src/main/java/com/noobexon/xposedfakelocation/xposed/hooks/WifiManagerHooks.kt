@file:Suppress("DEPRECATION")

package com.noobexon.xposedfakelocation.xposed.hooks

import android.util.Log
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

class WifiManagerHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader,
    private val packageName: String
) {
    private val tag = "[WifiManagerHooks]"

    fun initHooks() {
        hookWifiManager()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookWifiManager() {
        try {
            val wifiManagerClass = Class.forName("android.net.wifi.WifiManager", false, classLoader)

            module.hook(wifiManagerClass.getDeclaredMethod("getConnectionInfo")).intercept { chain ->
                when (val replay = wifiConnectionInfoHookDecision(packageName)) {
                    SystemServiceHookResult.Passthrough -> chain.proceed()
                    is SystemServiceHookResult.Spoofed -> {
                        module.log(Log.INFO, tag, "Replayed app Wi-Fi connection info while spoofing.")
                        WifiBaselineReplay.replayConnectionInfo(replay.value)
                    }
                }
            }

            module.hook(wifiManagerClass.getDeclaredMethod("getScanResults")).intercept { chain ->
                when (val replay = wifiScanResultsHookDecision(
                    packageName = packageName,
                    method = chain.executable as? Method
                )) {
                    SystemServiceHookResult.Passthrough -> chain.proceed()
                    is SystemServiceHookResult.Spoofed -> {
                        module.log(Log.INFO, tag, "Replayed app Wi-Fi scan results while spoofing (${replay.value.values.size} records).")
                        WifiBaselineReplay.replayScanResults(replay.value)
                    }
                }
            }
        } catch (e: Exception) {
            module.log(Log.ERROR, tag, "Error hooking WifiManager - ${e.message}")
        }
    }

    internal companion object {
        internal fun wifiConnectionInfoHookDecision(
            packageName: String?,
            shouldSpoofPackage: (String?) -> Boolean = ::shouldSpoofTargetPackage,
            wifiProvider: () -> WifiBaselineSnapshot? = ::activeWifiBaseline
        ): SystemServiceHookResult<WifiBaselineReplay.ConnectionReplayResult> {
            if (!shouldSpoofPackage(packageName)) return SystemServiceHookResult.Passthrough
            return SystemServiceHookResult.Spoofed(
                WifiBaselineReplay.connectionReplayResult(wifiProvider())
            )
        }

        internal fun wifiScanResultsHookDecision(
            packageName: String?,
            method: Method? = null,
            shouldSpoofPackage: (String?) -> Boolean = ::shouldSpoofTargetPackage,
            wifiProvider: () -> WifiBaselineSnapshot? = ::activeWifiBaseline
        ): SystemServiceHookResult<WifiBaselineReplay.ScanResultsReplayPlan> {
            if (!shouldSpoofPackage(packageName)) return SystemServiceHookResult.Passthrough
            return SystemServiceHookResult.Spoofed(
                WifiBaselineReplay.scanResultsReplayPlan(
                    wifi = wifiProvider(),
                    returnType = method?.returnType
                )
            )
        }

        private fun shouldSpoofTargetPackage(packageName: String?): Boolean {
            if (PreferencesUtil.getIsPlaying() != true) return false
            return LocationUtil.shouldSpoofPackage(packageName)
        }

        private fun activeWifiBaseline(): WifiBaselineSnapshot? {
            return PreferencesUtil.getSignalBaseline()?.wifi
        }
    }
}
