package com.noobexon.xposedfakelocation.xposed.utils

import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ConnectionReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.WifiBaselineReplay.ScanResultsReturnKind
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiBaselineReplayTest {
    @Test
    fun savedWifiBaselineMapsConnectionAndTwoScanResults() {
        val wifi = wifiBaselineWithTwoScans()

        val connection = WifiBaselineReplay.connectionReplayResult(wifi)
        val scans = WifiBaselineReplay.scanResultsReplayResult(wifi)

        assertEquals(ConnectionReplayKind.SAVED_BASELINE, connection.kind)
        assertEquals("12:34:56:78:9a:bc", connection.values.bssid)
        assertEquals("TestNet", connection.values.ssid)
        assertEquals("TestNet", String(connection.values.ssidBytes, Charsets.UTF_8))
        assertEquals(-55, connection.values.rssi)
        assertEquals(2_412, connection.values.frequencyMhz)
        assertEquals(42, connection.values.networkId)

        assertEquals(ScanResultsReplayKind.SAVED_BASELINE, scans.kind)
        assertEquals(2, scans.values.size)
        assertEquals("12:34:56:78:9a:bc", scans.values[0].bssid)
        assertEquals("TestNet", scans.values[0].ssid)
        assertEquals(-51, scans.values[0].level)
        assertEquals(2_412, scans.values[0].frequencyMhz)
        assertEquals("aa:bb:cc:dd:ee:ff", scans.values[1].bssid)
        assertEquals("TestNet-5G", scans.values[1].ssid)
        assertEquals(-62, scans.values[1].level)
        assertEquals(5_180, scans.values[1].frequencyMhz)
    }

    @Test
    fun missingAndCorruptWifiBaselineFailClosedToPlaceholderAndEmptyScans() {
        val missingConnection = WifiBaselineReplay.connectionReplayResult(null)
        val missingScans = WifiBaselineReplay.scanResultsReplayResult(null)

        assertEquals(ConnectionReplayKind.PLACEHOLDER, missingConnection.kind)
        assertEquals(WifiBaselineReplay.PLACEHOLDER_BSSID, missingConnection.values.bssid)
        assertEquals(WifiBaselineReplay.PLACEHOLDER_SSID, missingConnection.values.ssid)
        assertEquals(ScanResultsReplayKind.EMPTY, missingScans.kind)
        assertTrue(missingScans.values.isEmpty())

        val validWifi = SignalBaselineTestFixtures.validBaseline().wifi
        val corruptConnection = validWifi.copy(
            connectionInfo = requireNotNull(validWifi.connectionInfo).copy(
                ssidBytesBase64 = "{not-base64",
                bssid = "not-a-bssid"
            )
        )
        val corruptScan = validWifi.scanResults.single().copy(ssidBytesBase64 = "{not-base64")
        val corruptScans = WifiBaselineSnapshot(
            connectionInfo = validWifi.connectionInfo,
            scanResults = listOf(corruptScan),
            scanResultCount = 1
        )

        assertEquals(ConnectionReplayKind.PLACEHOLDER, WifiBaselineReplay.connectionReplayResult(corruptConnection).kind)
        assertEquals(ScanResultsReplayKind.EMPTY, WifiBaselineReplay.scanResultsReplayResult(corruptScans).kind)
        assertEquals(
            ScanResultsReplayKind.EMPTY,
            WifiBaselineReplay.scanResultsReplayResult(corruptScans.copy(scanResultCount = 2)).kind
        )
    }

    @Test
    fun scanReplayPlansSelectMethodCompatibleReturnShapes() {
        val listMethod = HookReturnTypes::class.java.getDeclaredMethod("scanList")
        val parceledMethod = HookReturnTypes::class.java.getDeclaredMethod("scanParceledListSlice")
        val unsupportedMethod = HookReturnTypes::class.java.getDeclaredMethod("scanUnsupported")

        val listPlan = WifiBaselineReplay.scanResultsReplayPlan(null, listMethod.returnType)
        val parceledPlan = WifiBaselineReplay.scanResultsReplayPlan(null, parceledMethod.returnType)
        val unsupportedPlan = WifiBaselineReplay.scanResultsReplayPlan(null, unsupportedMethod.returnType)

        assertEquals(ScanResultsReturnKind.LIST, listPlan.returnKind)
        assertEquals(ScanResultsReturnKind.PARCELED_LIST_SLICE, parceledPlan.returnKind)
        assertEquals(ScanResultsReturnKind.UNSUPPORTED, unsupportedPlan.returnKind)
        assertTrue(WifiBaselineReplay.replayScanResults(listPlan) is List<*>)

        val parceledValue = WifiBaselineReplay.replayScanResults(parceledPlan)
        assertEquals("android.content.pm.ParceledListSlice", parceledValue?.javaClass?.name)
        val parceledList = parceledValue?.javaClass?.getMethod("getList")?.invoke(parceledValue) as List<*>
        assertTrue(parceledList.isEmpty())
        assertNull(WifiBaselineReplay.replayScanResults(unsupportedPlan))
    }

    @Test
    fun hiddenParceledListSliceNamesAreRecognizedWithoutCompileSdkStubs() {
        assertEquals(
            ScanResultsReturnKind.PARCELED_LIST_SLICE,
            WifiBaselineReplay.scanResultsReturnKind("android.content.pm.ParceledListSlice")
        )
        assertEquals(
            ScanResultsReturnKind.PARCELED_LIST_SLICE,
            WifiBaselineReplay.scanResultsReturnKind("com.android.modules.utils.ParceledListSlice")
        )
        assertEquals(ScanResultsReturnKind.UNSUPPORTED, WifiBaselineReplay.scanResultsReturnKind("java.lang.String"))
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
        fun scanList(): List<Any> = emptyList()

        @Suppress("unused")
        fun scanParceledListSlice(): android.content.pm.ParceledListSlice<Any> =
            android.content.pm.ParceledListSlice.emptyList()

        @Suppress("unused")
        fun scanUnsupported(): String = "unsupported"
    }
}
