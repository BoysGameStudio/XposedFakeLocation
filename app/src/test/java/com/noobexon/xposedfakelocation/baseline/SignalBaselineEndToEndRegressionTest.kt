package com.noobexon.xposedfakelocation.baseline

import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_SIGNAL_BASELINE_SNAPSHOT
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import com.noobexon.xposedfakelocation.xposed.hooks.PhoneServiceHookResult
import com.noobexon.xposedfakelocation.xposed.hooks.PhoneServicesHooks
import com.noobexon.xposedfakelocation.xposed.hooks.SystemServiceHookResult
import com.noobexon.xposedfakelocation.xposed.hooks.SystemServicesHooks
import com.noobexon.xposedfakelocation.xposed.utils.CellularBaselineReplay
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ConnectionReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReturnKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignalBaselineEndToEndRegressionTest {
    private val gson = Gson()

    @Before
    fun setUp() {
        resetPreferencesUtil()
        resetLocationUtil()
        LocationUtil.currentSdkIntProvider = { SignalBaselineTestFixtures.CURRENT_SDK }
        LocationUtil.currentBuildFingerprintProvider = { SignalBaselineTestFixtures.CURRENT_BUILD }
        CellularBaselineReplay.currentSdkIntProvider = { SignalBaselineTestFixtures.CURRENT_SDK }
        CellularBaselineReplay.currentBuildFingerprintProvider = { SignalBaselineTestFixtures.CURRENT_BUILD }
    }

    @After
    fun tearDown() {
        resetPreferencesUtil()
        resetLocationUtil()
        CellularBaselineReplay.currentSdkIntProvider = { 0 }
        CellularBaselineReplay.currentBuildFingerprintProvider = { "" }
    }

    @Test
    fun savedRemoteBaselineDrivesTargetReplayDecisionsEndToEnd() {
        val baseline = SignalBaselineTestFixtures.validBaseline(
            cellInfo = listOf(
                SignalBaselineTestFixtures.gsmCellInfoSnapshot(parcelBytes = null),
                SignalBaselineTestFixtures.lteCellInfoSnapshot()
            ),
            neighboringCellInfo = emptyList()
        )
        PreferencesUtil.init(remotePrefs(isPlaying = true, baselineJson = requireNotNull(SignalBaselineCodec.encodeToJson(baseline))))

        val parsed = requireNotNull(
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            )
        )

        LocationUtil.updateLocation()
        assertEquals(37.4219983, LocationUtil.latitude, COORDINATE_DELTA)
        assertEquals(-122.084, LocationUtil.longitude, COORDINATE_DELTA)
        assertTrue(LocationUtil.shouldSpoofPackage(TARGET_PACKAGE))
        assertFalse(LocationUtil.shouldSpoofPackage("com.example.other"))
        assertFalse(LocationUtil.shouldSpoofPackage(MANAGER_APP_PACKAGE_NAME))

        assertEquals(
            CellularBaselineReplay.CellLocationReplayKind.GSM,
            CellularBaselineReplay.cellLocationReplayKind(parsed.cellular.cellLocation)
        )
        val cellInfoReplay = PhoneServicesHooks.allCellInfoHookResult(
            args = listOf(TARGET_PACKAGE),
            cellularProvider = { parsed.cellular }
        )
        require(cellInfoReplay is PhoneServiceHookResult.Spoofed)
        assertTrue(cellInfoReplay.value.isEmpty())

        val wifiConnection = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf(TARGET_PACKAGE),
            wifiProvider = { parsed.wifi }
        )
        val wifiScans = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = { parsed.wifi }
        )
        require(wifiConnection is SystemServiceHookResult.Spoofed)
        require(wifiScans is SystemServiceHookResult.Spoofed)
        assertEquals(ConnectionReplayKind.SAVED_BASELINE, wifiConnection.value.kind)
        assertEquals("TestNet", wifiConnection.value.values.ssid)
        assertEquals("12:34:56:78:9a:bc", wifiConnection.value.values.bssid)
        assertEquals(ScanResultsReplayKind.SAVED_BASELINE, wifiScans.value.kind)
        assertEquals(ScanResultsReturnKind.LIST, wifiScans.value.returnKind)
        assertEquals(1, wifiScans.value.values.size)
    }

    @Test
    fun corruptBaselineFailsClosedForSpoofedTargetsAndPreservesPassthroughForNonTargets() {
        PreferencesUtil.init(remotePrefs(isPlaying = true, baselineJson = "{not-json"))

        LocationUtil.updateLocation()
        assertEquals(0.0, LocationUtil.latitude, COORDINATE_DELTA)
        assertEquals(0.0, LocationUtil.longitude, COORDINATE_DELTA)

        val wifiConnection = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf(TARGET_PACKAGE),
            wifiProvider = { parsedWifiBaselineOrNull() }
        )
        val wifiScans = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = { parsedWifiBaselineOrNull() }
        )
        val cellInfo = PhoneServicesHooks.allCellInfoHookResult(
            args = listOf(TARGET_PACKAGE),
            cellularProvider = {
                PreferencesUtil.getSignalBaseline(
                    currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                    currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
                )?.cellular
            }
        )

        require(wifiConnection is SystemServiceHookResult.Spoofed)
        require(wifiScans is SystemServiceHookResult.Spoofed)
        require(cellInfo is PhoneServiceHookResult.Spoofed)
        assertEquals(ConnectionReplayKind.PLACEHOLDER, wifiConnection.value.kind)
        assertEquals(ScanResultsReplayKind.EMPTY, wifiScans.value.kind)
        assertTrue(cellInfo.value.isEmpty())

        assertSame(
            SystemServiceHookResult.Passthrough,
            SystemServicesHooks.wifiConnectionInfoHookDecision(args = listOf("com.example.other"))
        )
        assertSame(
            PhoneServiceHookResult.Passthrough,
            PhoneServicesHooks.allCellInfoHookResult(args = listOf("com.example.other"))
        )
    }

    private fun remotePrefs(isPlaying: Boolean, baselineJson: String): FakeSharedPreferences {
        return FakeSharedPreferences().also { prefs ->
            prefs.edit()
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .putString(KEY_TARGET_APPS, gson.toJson(listOf(TARGET_PACKAGE, MANAGER_APP_PACKAGE_NAME)))
                .putString(KEY_SIGNAL_BASELINE_SNAPSHOT, baselineJson)
                .apply()
        }
    }

    private fun parsedWifiBaselineOrNull() = PreferencesUtil.getSignalBaseline(
        currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
        currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
    )?.wifi

    private fun resetPreferencesUtil() {
        val preferencesField = PreferencesUtil::class.java.getDeclaredField("preferences")
        preferencesField.isAccessible = true
        preferencesField.set(PreferencesUtil, null)
    }

    private fun resetLocationUtil() {
        LocationUtil.logger = null
        LocationUtil.currentSdkIntProvider = { 0 }
        LocationUtil.currentBuildFingerprintProvider = { "" }
        LocationUtil.currentTimeMillisProvider = { 0L }
        LocationUtil.elapsedRealtimeNanosProvider = { 0L }
        LocationUtil.latitude = 0.0
        LocationUtil.longitude = 0.0
        LocationUtil.accuracy = 0F
        LocationUtil.altitude = 0.0
        LocationUtil.verticalAccuracy = 0F
        LocationUtil.meanSeaLevel = 0.0
        LocationUtil.meanSeaLevelAccuracy = 0F
        LocationUtil.speed = 0F
        LocationUtil.speedAccuracy = 0F
    }

    private class HookReturnTypes {
        @Suppress("unused")
        fun scanList(): List<Any> = emptyList()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        const val COORDINATE_DELTA = 0.0000001
    }
}
