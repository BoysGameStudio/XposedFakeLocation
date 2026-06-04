package com.noobexon.xposedfakelocation.manager.baseline

import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LocationBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NeighboringCellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiInfoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class SignalBaselineCaptureTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun capture_succeedsWithValidLocationAndPartialSignals() {
        val source = FakeSignalBaselineSource(
            enabledProviders = listOf("gps", "network", "fused"),
            locations = mapOf(
                "gps" to locationSnapshot(provider = "gps", timeMillis = 1_000L, accuracyMeters = 1.0f),
                "network" to locationSnapshot(provider = "network", timeMillis = 2_000L, accuracyMeters = 10.0f),
                "fused" to locationSnapshot(provider = "fused", timeMillis = 2_000L, accuracyMeters = 5.0f)
            ),
            wifiConnectionInfo = wifiInfoSnapshot(),
            wifiScanResults = listOf(scanResultSnapshot())
        )
        val capture = newCapture(source)

        val result = capture.capture()

        require(result is SignalBaselineCapture.CaptureResult.Success)
        val snapshot = result.snapshot
        assertEquals(SignalBaselineCodec.SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals(fixedClock.millis(), snapshot.capturedAtMillis)
        assertEquals(CURRENT_SDK, snapshot.captureSdkInt)
        assertEquals(CURRENT_BUILD, snapshot.captureBuildFingerprint)
        assertEquals("fused", snapshot.location.provider)
        assertEquals(37.4219983, snapshot.location.latitude, 0.0)
        assertEquals(-122.084, snapshot.location.longitude, 0.0)
        assertEquals(0, snapshot.cellular.cellInfoCount)
        assertEquals(0, snapshot.cellular.neighboringCellInfoCount)
        assertEquals("TestNet", snapshot.wifi.connectionInfo?.ssid)
        assertEquals("12:34:56:78:9a:bc", snapshot.wifi.connectionInfo?.bssid)
        assertEquals(1, snapshot.wifi.scanResultCount)
        assertEquals("TestNet", snapshot.wifi.scanResults.single().ssid)
        assertEquals("12:34:56:78:9a:bc", snapshot.wifi.scanResults.single().bssid)
        assertTrue(
            SignalBaselineCodec.validate(
                snapshot = snapshot,
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            ).isValid
        )
    }

    @Test
    fun capture_returnsNoRealLocationWhenProvidersHaveNoLocation() {
        val source = FakeSignalBaselineSource(
            enabledProviders = listOf("gps", "network"),
            locations = mapOf("gps" to null, "network" to null),
            wifiConnectionInfo = wifiInfoSnapshot(),
            wifiScanResults = listOf(scanResultSnapshot())
        )
        val capture = newCapture(source)

        val result = capture.capture()

        assertSame(SignalBaselineCapture.CaptureResult.NoRealLocation, result)
    }

    @Test
    fun capture_succeedsWithEmptyCellAndWifiSources() {
        val source = FakeSignalBaselineSource(
            enabledProviders = listOf("network"),
            locations = mapOf("network" to locationSnapshot(provider = "network"))
        )
        val capture = newCapture(source)

        val result = capture.capture()

        require(result is SignalBaselineCapture.CaptureResult.Success)
        val snapshot = result.snapshot
        assertEquals(0, snapshot.cellular.cellInfoCount)
        assertEquals(0, snapshot.cellular.neighboringCellInfoCount)
        assertNull(snapshot.cellular.cellLocation)
        assertNull(snapshot.wifi.connectionInfo)
        assertEquals(0, snapshot.wifi.scanResultCount)
        assertTrue(
            SignalBaselineCodec.validate(
                snapshot = snapshot,
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            ).isValid
        )
    }

    @Test
    fun capture_catchesSecurityExceptionAndThrowablePerSignalSource() {
        val source = FakeSignalBaselineSource(
            enabledProviders = listOf("network"),
            locations = mapOf("network" to locationSnapshot(provider = "network")),
            allCellInfoError = SecurityException("cell info denied"),
            cellLocationError = SecurityException("cell location denied"),
            neighboringCellInfoError = RuntimeException("neighboring unavailable"),
            wifiConnectionInfoError = SecurityException("wifi connection denied"),
            wifiScanResultsError = AssertionError("scan cache unavailable")
        )
        val capture = newCapture(source)

        val result = capture.capture()

        require(result is SignalBaselineCapture.CaptureResult.Success)
        val snapshot = result.snapshot
        assertEquals(0, snapshot.cellular.cellInfoCount)
        assertNull(snapshot.cellular.cellLocation)
        assertEquals(0, snapshot.cellular.neighboringCellInfoCount)
        assertNull(snapshot.wifi.connectionInfo)
        assertEquals(0, snapshot.wifi.scanResultCount)
        assertTrue(
            SignalBaselineCodec.validate(
                snapshot = snapshot,
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            ).isValid
        )
    }

    private fun newCapture(source: SignalBaselineSource): SignalBaselineCapture {
        return SignalBaselineCapture(
            source = source,
            clock = fixedClock,
            sdkIntProvider = { CURRENT_SDK },
            buildFingerprintProvider = { CURRENT_BUILD }
        )
    }

    private fun locationSnapshot(
        provider: String,
        timeMillis: Long = 1_780_000_000_001L,
        accuracyMeters: Float? = 3.5f
    ): LocationBaselineSnapshot {
        return LocationBaselineSnapshot(
            provider = provider,
            latitude = 37.4219983,
            longitude = -122.084,
            timeMillis = timeMillis,
            elapsedRealtimeNanos = 123_456_789L,
            hasElapsedRealtimeUncertaintyNanos = true,
            elapsedRealtimeUncertaintyNanos = 7.5,
            hasAltitude = true,
            altitudeMeters = 12.25,
            hasAccuracy = accuracyMeters != null,
            accuracyMeters = accuracyMeters,
            hasSpeed = true,
            speedMetersPerSecond = 1.25f,
            hasBearing = true,
            bearingDegrees = 45.0f,
            hasVerticalAccuracy = true,
            verticalAccuracyMeters = 2.0f,
            hasSpeedAccuracy = true,
            speedAccuracyMetersPerSecond = 0.5f,
            hasBearingAccuracy = true,
            bearingAccuracyDegrees = 1.5f,
            hasMslAltitude = true,
            mslAltitudeMeters = 11.75,
            hasMslAltitudeAccuracy = true,
            mslAltitudeAccuracyMeters = 0.75f,
            isMock = false,
            extras = mapOf("source" to "fixture", "satellites" to 12),
            extrasUnsupportedKeys = listOf("parcelablePayload")
        )
    }

    private fun wifiInfoSnapshot(): WifiInfoSnapshot {
        return WifiInfoSnapshot(
            ssid = "TestNet",
            ssidBytesBase64 = ssidBytesBase64(),
            bssid = "12:34:56:78:9a:bc",
            rssi = -48,
            networkId = 42,
            frequencyMhz = 2_412,
            linkSpeedMbps = 144,
            rxLinkSpeedMbps = 144,
            txLinkSpeedMbps = 72,
            wifiStandard = 6,
            currentSecurityType = 2,
            subscriptionId = null
        )
    }

    private fun scanResultSnapshot(): ScanResultSnapshot {
        return ScanResultSnapshot(
            ssid = "TestNet",
            ssidBytesBase64 = ssidBytesBase64(),
            bssid = "12:34:56:78:9a:bc",
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            level = -51,
            frequencyMhz = 2_412,
            channelWidth = 0,
            centerFreq0Mhz = 2_412,
            centerFreq1Mhz = 0,
            timestampMicros = 1_234_567L,
            wifiStandard = 6,
            is80211mcResponder = false
        )
    }

    private fun ssidBytesBase64(): String = Base64.getEncoder().encodeToString("TestNet".toByteArray(Charsets.UTF_8))

    private class FakeSignalBaselineSource(
        private val enabledProviders: List<String> = emptyList(),
        private val locations: Map<String, LocationBaselineSnapshot?> = emptyMap(),
        private val allCellInfo: List<CellInfoSnapshot> = emptyList(),
        private val cellLocation: CellLocationSnapshot? = null,
        private val neighboringCellInfo: List<NeighboringCellInfoSnapshot> = emptyList(),
        private val wifiConnectionInfo: WifiInfoSnapshot? = null,
        private val wifiScanResults: List<ScanResultSnapshot> = emptyList(),
        private val allCellInfoError: Throwable? = null,
        private val cellLocationError: Throwable? = null,
        private val neighboringCellInfoError: Throwable? = null,
        private val wifiConnectionInfoError: Throwable? = null,
        private val wifiScanResultsError: Throwable? = null
    ) : SignalBaselineSource {
        override fun getEnabledLocationProviders(): List<String> = enabledProviders

        override fun getLastKnownLocation(provider: String): LocationBaselineSnapshot? = locations[provider]

        override fun getAllCellInfoSnapshots(): List<CellInfoSnapshot> {
            allCellInfoError?.let { throw it }
            return allCellInfo
        }

        override fun getCellLocationSnapshot(): CellLocationSnapshot? {
            cellLocationError?.let { throw it }
            return cellLocation
        }

        override fun getNeighboringCellInfoSnapshots(): List<NeighboringCellInfoSnapshot> {
            neighboringCellInfoError?.let { throw it }
            return neighboringCellInfo
        }

        override fun getWifiConnectionInfoSnapshot(): WifiInfoSnapshot? {
            wifiConnectionInfoError?.let { throw it }
            return wifiConnectionInfo
        }

        override fun getWifiScanResultSnapshots(): List<ScanResultSnapshot> {
            wifiScanResultsError?.let { throw it }
            return wifiScanResults
        }
    }

    private companion object {
        const val CURRENT_SDK = 36
        const val CURRENT_BUILD = "google/sdk_gphone64_x86_64/fixture:16/BD1A.250000.001/1234567:userdebug/test-keys"
    }
}
