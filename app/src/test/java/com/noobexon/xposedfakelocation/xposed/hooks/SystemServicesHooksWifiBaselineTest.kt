package com.noobexon.xposedfakelocation.xposed.hooks

import android.telephony.CellInfo
import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellularBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ConnectionReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReturnKind
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemServicesHooksWifiBaselineTest {
    private val gson = Gson()

    @Before
    fun setUp() {
        resetPreferencesUtil()
    }

    @After
    fun tearDown() {
        resetPreferencesUtil()
    }

    @Test
    fun wifiHookDecisionsReplaySavedBaselineForSpoofedTarget() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))
        val wifi = wifiBaselineWithTwoScans()

        val connection = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf(TARGET_PACKAGE),
            wifiProvider = { wifi }
        )
        val scans = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = { wifi }
        )

        require(connection is SystemServiceHookResult.Spoofed)
        require(scans is SystemServiceHookResult.Spoofed)
        assertEquals(ConnectionReplayKind.SAVED_BASELINE, connection.value.kind)
        assertEquals("12:34:56:78:9a:bc", connection.value.values.bssid)
        assertEquals("TestNet", connection.value.values.ssid)
        assertEquals(-55, connection.value.values.rssi)
        assertEquals(2_412, connection.value.values.frequencyMhz)
        assertEquals(ScanResultsReplayKind.SAVED_BASELINE, scans.value.kind)
        assertEquals(ScanResultsReturnKind.LIST, scans.value.returnKind)
        assertEquals(2, scans.value.values.size)
        assertEquals("12:34:56:78:9a:bc", scans.value.values[0].bssid)
        assertEquals("aa:bb:cc:dd:ee:ff", scans.value.values[1].bssid)
    }

    @Test
    fun wifiHookDecisionsFailClosedForMissingAndCorruptBaselineWithoutPassthrough() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))
        var missingConnectionProviderCalls = 0
        var missingScanProviderCalls = 0

        val missingConnection = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf(TARGET_PACKAGE),
            wifiProvider = {
                missingConnectionProviderCalls++
                null
            }
        )
        val missingScans = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = {
                missingScanProviderCalls++
                null
            }
        )

        require(missingConnection is SystemServiceHookResult.Spoofed)
        require(missingScans is SystemServiceHookResult.Spoofed)
        assertEquals(ConnectionReplayKind.PLACEHOLDER, missingConnection.value.kind)
        assertEquals(ScanResultsReplayKind.EMPTY, missingScans.value.kind)
        assertEquals(1, missingConnectionProviderCalls)
        assertEquals(1, missingScanProviderCalls)

        val validWifi = SignalBaselineTestFixtures.validBaseline().wifi
        val corruptWifi = validWifi.copy(
            connectionInfo = requireNotNull(validWifi.connectionInfo).copy(
                ssidBytesBase64 = "{not-base64",
                bssid = "not-a-bssid"
            ),
            scanResults = listOf(validWifi.scanResults.single().copy(ssidBytesBase64 = "{not-base64")),
            scanResultCount = 1
        )
        val corruptConnection = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf(TARGET_PACKAGE),
            wifiProvider = { corruptWifi }
        )
        val corruptScans = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = { corruptWifi }
        )

        require(corruptConnection is SystemServiceHookResult.Spoofed)
        require(corruptScans is SystemServiceHookResult.Spoofed)
        assertEquals(ConnectionReplayKind.PLACEHOLDER, corruptConnection.value.kind)
        assertEquals(ScanResultsReplayKind.EMPTY, corruptScans.value.kind)
    }

    @Test
    fun wifiScanHookDecisionSelectsListParceledListSliceAndUnsupportedReturnShapes() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))

        val listReplay = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanList"),
            wifiProvider = { null }
        )
        val parceledReplay = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanParceledListSlice"),
            wifiProvider = { null }
        )
        val unsupportedReplay = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("scanUnsupported"),
            wifiProvider = { null }
        )

        require(listReplay is SystemServiceHookResult.Spoofed)
        require(parceledReplay is SystemServiceHookResult.Spoofed)
        require(unsupportedReplay is SystemServiceHookResult.Spoofed)
        assertEquals(ScanResultsReturnKind.LIST, listReplay.value.returnKind)
        assertEquals(ScanResultsReturnKind.PARCELED_LIST_SLICE, parceledReplay.value.returnKind)
        assertEquals(ScanResultsReturnKind.UNSUPPORTED, unsupportedReplay.value.returnKind)
        assertEquals(ScanResultsReplayKind.EMPTY, listReplay.value.kind)
        assertEquals(ScanResultsReplayKind.EMPTY, parceledReplay.value.kind)
        assertEquals(ScanResultsReplayKind.EMPTY, unsupportedReplay.value.kind)
    }

    @Test
    fun wifiHookDecisionsArePassthroughForNonTargetsDisabledStateAndManagerPackage() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE, MANAGER_APP_PACKAGE_NAME))
        var providerCalled = false

        val nonTarget = SystemServicesHooks.wifiConnectionInfoHookDecision(
            args = listOf("com.example.other"),
            wifiProvider = {
                providerCalled = true
                wifiBaselineWithTwoScans()
            }
        )
        val managerPackage = SystemServicesHooks.wifiScanResultsHookDecision(
            args = listOf(MANAGER_APP_PACKAGE_NAME),
            wifiProvider = {
                providerCalled = true
                wifiBaselineWithTwoScans()
            }
        )

        assertSame(SystemServiceHookResult.Passthrough, nonTarget)
        assertSame(SystemServiceHookResult.Passthrough, managerPackage)
        assertFalse(providerCalled)

        initPreferences(isPlaying = false, targets = listOf(TARGET_PACKAGE))
        assertSame(
            SystemServiceHookResult.Passthrough,
            SystemServicesHooks.wifiConnectionInfoHookDecision(
                args = listOf(TARGET_PACKAGE),
                wifiProvider = {
                    providerCalled = true
                    wifiBaselineWithTwoScans()
                }
            )
        )
        assertFalse(providerCalled)
    }

    @Test
    fun miuiBlurryCellHookDecisionsUseReplaySeamAndSafeFallbacks() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))
        val cellular = SignalBaselineTestFixtures.validBaseline().cellular
        val cellLocationMethod = HookReturnTypes::class.java.getDeclaredMethod("cellLocation")
        val cellInfosMethod = HookReturnTypes::class.java.getDeclaredMethod("cellInfos")
        val locationSentinel = Any()
        val infosSentinel = listOf("replayed")
        var replayedLocationCellular: CellularBaselineSnapshot? = null
        var replayedInfosCellular: CellularBaselineSnapshot? = null

        val locationReplay = SystemServicesHooks.blurryCellLocationHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = cellLocationMethod,
            cellularProvider = { cellular },
            replayProvider = { replayCellular, method ->
                replayedLocationCellular = replayCellular
                assertSame(cellLocationMethod, method)
                locationSentinel
            }
        )
        val infosReplay = SystemServicesHooks.blurryCellInfosHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = cellInfosMethod,
            cellularProvider = { cellular },
            replayProvider = { replayCellular, method ->
                replayedInfosCellular = replayCellular
                assertSame(cellInfosMethod, method)
                infosSentinel
            }
        )

        require(locationReplay is SystemServiceHookResult.Spoofed)
        require(infosReplay is SystemServiceHookResult.Spoofed)
        assertSame(locationSentinel, locationReplay.value)
        assertSame(infosSentinel, infosReplay.value)
        assertSame(cellular, replayedLocationCellular)
        assertSame(cellular, replayedInfosCellular)

        val missingLocation = SystemServicesHooks.blurryCellLocationHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = cellLocationMethod,
            cellularProvider = { null }
        )
        val missingInfos = SystemServicesHooks.blurryCellInfosHookDecision(
            args = listOf(TARGET_PACKAGE),
            method = cellInfosMethod,
            cellularProvider = { null }
        )
        require(missingLocation is SystemServiceHookResult.Spoofed)
        require(missingInfos is SystemServiceHookResult.Spoofed)
        assertNull(missingLocation.value)
        assertTrue((missingInfos.value as List<*>).isEmpty())

        val nonTarget = SystemServicesHooks.blurryCellInfosHookDecision(
            args = listOf("com.example.other"),
            method = cellInfosMethod,
            cellularProvider = { cellular },
            replayProvider = { _, _ -> error("non-target should passthrough") }
        )
        assertSame(SystemServiceHookResult.Passthrough, nonTarget)
    }

    private fun initPreferences(isPlaying: Boolean, targets: List<String>) {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.edit()
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putString(KEY_TARGET_APPS, gson.toJson(targets))
            .apply()
        PreferencesUtil.init(remotePrefs)
    }

    private fun resetPreferencesUtil() {
        val preferencesField = PreferencesUtil::class.java.getDeclaredField("preferences")
        preferencesField.isAccessible = true
        preferencesField.set(PreferencesUtil, null)
    }

    private fun wifiBaselineWithTwoScans(): WifiBaselineSnapshot {
        val baselineWifi = SignalBaselineTestFixtures.validBaseline().wifi
        val firstScan = baselineWifi.scanResults.single()
        val secondScan = scanResultSnapshot(
            ssid = "TestNet-5G",
            bssid = "aa:bb:cc:dd:ee:ff",
            level = -62,
            frequencyMhz = 5_180
        )
        val scans = listOf(firstScan, secondScan)
        return baselineWifi.copy(
            connectionInfo = requireNotNull(baselineWifi.connectionInfo).copy(rssi = -55),
            scanResults = scans,
            scanResultCount = scans.size
        )
    }

    private fun scanResultSnapshot(
        ssid: String,
        bssid: String,
        level: Int,
        frequencyMhz: Int
    ): ScanResultSnapshot {
        return ScanResultSnapshot(
            ssid = ssid,
            ssidBytesBase64 = Base64.getEncoder().encodeToString(ssid.toByteArray(Charsets.UTF_8)),
            bssid = bssid,
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            level = level,
            frequencyMhz = frequencyMhz,
            channelWidth = 0,
            centerFreq0Mhz = frequencyMhz,
            centerFreq1Mhz = 0,
            timestampMicros = 2_345_678L,
            wifiStandard = 6,
            is80211mcResponder = false
        )
    }

    private class HookReturnTypes {
        @Suppress("unused")
        fun cellLocation(): Any? = null

        @Suppress("unused")
        fun cellInfos(): List<CellInfo> = emptyList()

        @Suppress("unused")
        fun scanList(): List<Any> = emptyList()

        @Suppress("unused")
        fun scanParceledListSlice(): android.content.pm.ParceledListSlice<Any> =
            android.content.pm.ParceledListSlice.emptyList()

        @Suppress("unused")
        fun scanUnsupported(): String = "unsupported"
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
    }
}
