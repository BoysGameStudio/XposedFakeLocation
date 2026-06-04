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
        assertEquals(6, scans.values[0].wifiStandard)
        assertEquals(false, scans.values[0].is80211mcResponder)
        assertEquals("aa:bb:cc:dd:ee:ff", scans.values[1].bssid)
        assertEquals("TestNet-5G", scans.values[1].ssid)
        assertEquals(-62, scans.values[1].level)
        assertEquals(5_180, scans.values[1].frequencyMhz)
        assertEquals(6, scans.values[1].wifiStandard)
        assertEquals(false, scans.values[1].is80211mcResponder)
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
    fun wifiParcelMetadataIsCarriedIntoReplayValuesForLosslessRuntimeReplay() {
        val parcelBytes = byteArrayOf(9, 8, 7, 6)
        val parcelBase64 = Base64.getEncoder().encodeToString(parcelBytes)
        val baselineWifi = SignalBaselineTestFixtures.validBaseline().wifi
        val connection = requireNotNull(baselineWifi.connectionInfo).copy(
            parcelBase64 = parcelBase64,
            parcelClassName = "android.net.wifi.WifiInfo",
            parcelByteCount = parcelBytes.size,
            parcelSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
            parcelBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
        )
        val scan = baselineWifi.scanResults.single().copy(
            parcelBase64 = parcelBase64,
            parcelClassName = "android.net.wifi.ScanResult",
            parcelByteCount = parcelBytes.size,
            parcelSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
            parcelBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
        )
        val wifi = baselineWifi.copy(
            connectionInfo = connection,
            scanResults = listOf(scan),
            scanResultCount = 1
        )

        val connectionReplay = WifiBaselineReplay.connectionReplayResult(wifi)
        val scanReplay = WifiBaselineReplay.scanResultsReplayResult(wifi)

        assertEquals(parcelBase64, connectionReplay.values.parcelBase64)
        assertEquals("android.net.wifi.WifiInfo", connectionReplay.values.parcelClassName)
        assertEquals(parcelBytes.size, connectionReplay.values.parcelByteCount)
        assertEquals(SignalBaselineTestFixtures.CURRENT_SDK, connectionReplay.values.parcelSdkInt)
        assertEquals(SignalBaselineTestFixtures.CURRENT_BUILD, connectionReplay.values.parcelBuildFingerprint)
        assertEquals(parcelBase64, scanReplay.values.single().parcelBase64)
        assertEquals("android.net.wifi.ScanResult", scanReplay.values.single().parcelClassName)
        assertEquals(parcelBytes.size, scanReplay.values.single().parcelByteCount)
        assertEquals(SignalBaselineTestFixtures.CURRENT_SDK, scanReplay.values.single().parcelSdkInt)
        assertEquals(SignalBaselineTestFixtures.CURRENT_BUILD, scanReplay.values.single().parcelBuildFingerprint)
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
