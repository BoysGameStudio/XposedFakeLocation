package com.noobexon.xposedfakelocation.data.model.signalbaseline

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SignalBaselineCodecTest {
    private val rawGson = Gson()

    @Test
    fun validBaseline_roundTripsWithVersionedSections() {
        val baseline = validBaseline()
        val jsonText = requireNotNull(SignalBaselineCodec.encodeToJson(baseline))

        val json = JsonParser.parseString(jsonText).asJsonObject
        assertEquals(1, json.get("schemaVersion").asInt)
        assertEquals(CURRENT_SDK, json.get("captureSdkInt").asInt)
        assertEquals(CURRENT_BUILD, json.get("captureBuildFingerprint").asString)
        assertTrue(json.has("location"))
        assertTrue(json.has("cellular"))
        assertTrue(json.has("wifi"))
        assertFalse(json.has("towerCount"))
        assertFalse(json.has("towers"))
        assertFalse(json.has("selectedLatitude"))
        assertFalse(json.has("selectedLongitude"))

        val location = json.getAsJsonObject("location")
        assertEquals(37.4219983, location.get("latitude").asDouble, 0.0)
        assertEquals(-122.084, location.get("longitude").asDouble, 0.0)

        val cellular = json.getAsJsonObject("cellular")
        assertEquals(2, cellular.get("cellInfoCount").asInt)
        val cellInfo = cellular.getAsJsonArray("cellInfo")
        val gsm = cellInfo[0].asJsonObject
        assertEquals(RADIO_TYPE_GSM, gsm.get("radioType").asString)
        assertEquals("android.telephony.CellInfoGsm", gsm.get("parcelClassName").asString)
        assertTrue(gsm.has("parcelBase64"))
        val lteIdentity = cellInfo[1]
            .asJsonObject
            .getAsJsonObject("identity")
            .getAsJsonObject("lte")
        assertEquals(345_678, lteIdentity.get("ci").asInt)

        val wifi = json.getAsJsonObject("wifi")
        assertEquals(1, wifi.get("scanResultCount").asInt)
        val wifiInfo = wifi.getAsJsonObject("connectionInfo")
        assertEquals("TestNet", wifiInfo.get("ssid").asString)
        assertEquals("12:34:56:78:9a:bc", wifiInfo.get("bssid").asString)
        val scanResult = wifi.getAsJsonArray("scanResults")[0].asJsonObject
        assertEquals("[WPA2-PSK-CCMP][ESS]", scanResult.get("capabilities").asString)

        val parsed = SignalBaselineCodec.parseOrNull(
            json = jsonText,
            currentSdkInt = CURRENT_SDK,
            currentBuildFingerprint = CURRENT_BUILD
        )
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(37.4219983, parsed.location.latitude, 0.0)
        assertEquals(-122.084, parsed.location.longitude, 0.0)
        assertEquals(2, parsed.cellular.cellInfoCount)
        assertEquals("TestNet", parsed.wifi.connectionInfo?.ssid)
        assertEquals("12:34:56:78:9a:bc", parsed.wifi.scanResults.single().bssid)
    }

    @Test
    fun parse_rejectsCorruptJsonAndUnsupportedSchemaWithoutThrowing() {
        val corrupt = SignalBaselineCodec.parse(
            json = "{not-json",
            currentSdkInt = CURRENT_SDK,
            currentBuildFingerprint = CURRENT_BUILD
        )

        assertFalse(corrupt.isValid)
        assertNull(corrupt.snapshot)

        val unsupportedSchema = rawGson.toJson(
            validBaseline().copy(schemaVersion = SignalBaselineCodec.SCHEMA_VERSION + 1)
        )

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = unsupportedSchema,
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
    }

    @Test
    fun parse_rejectsSdkAndBuildMismatch() {
        val jsonText = requireNotNull(SignalBaselineCodec.encodeToJson(validBaseline()))

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = jsonText,
                currentSdkInt = CURRENT_SDK + 1,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = jsonText,
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = "other/build/fingerprint"
            )
        )
    }

    @Test
    fun parse_rejectsUnknownParcelClass() {
        val invalidCell = gsmCellInfoSnapshot().copy(
            parcelClassName = "android.telephony.CellInfoUnknown"
        )
        val invalidBaseline = validBaseline(cellInfo = listOf(invalidCell))

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(invalidBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
    }


    @Test
    fun parse_rejectsUnknownWifiParcelClasses() {
        val parcelBytes = byteArrayOf(8, 9, 10)
        val baseline = validBaseline()
        val invalidConnection = requireNotNull(baseline.wifi.connectionInfo).copy(
            parcelBase64 = base64(parcelBytes),
            parcelClassName = "android.net.wifi.UnknownWifiInfo",
            parcelByteCount = parcelBytes.size,
            parcelSdkInt = CURRENT_SDK,
            parcelBuildFingerprint = CURRENT_BUILD
        )
        val invalidConnectionBaseline = baseline.copy(
            wifi = baseline.wifi.copy(connectionInfo = invalidConnection)
        )

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(invalidConnectionBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )

        val invalidScan = baseline.wifi.scanResults.single().copy(
            parcelBase64 = base64(parcelBytes),
            parcelClassName = "android.net.wifi.UnknownScanResult",
            parcelByteCount = parcelBytes.size,
            parcelSdkInt = CURRENT_SDK,
            parcelBuildFingerprint = CURRENT_BUILD
        )
        val invalidScanBaseline = baseline.copy(
            wifi = baseline.wifi.copy(scanResults = listOf(invalidScan), scanResultCount = 1)
        )

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(invalidScanBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
    }

    @Test
    fun parse_rejectsOversizedParcelBlob() {
        val invalidCell = gsmCellInfoSnapshot().copy(
            parcelByteCount = SignalBaselineCodec.MAX_PARCEL_BLOB_BYTES + 1
        )
        val invalidBaseline = validBaseline(cellInfo = listOf(invalidCell))

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(invalidBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
    }

    @Test
    fun parse_rejectsOversizedCellAndWifiLists() {
        val oversizedCells = List(SignalBaselineCodec.MAX_CELL_INFO + 1) {
            gsmCellInfoSnapshot(parcelBytes = null)
        }
        val oversizedCellBaseline = validBaseline(cellInfo = oversizedCells)

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(oversizedCellBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )

        val oversizedScans = List(SignalBaselineCodec.MAX_WIFI_SCANS + 1) {
            scanResultSnapshot()
        }
        val oversizedWifiBaseline = validBaseline(scanResults = oversizedScans)

        assertNull(
            SignalBaselineCodec.parseOrNull(
                json = rawGson.toJson(oversizedWifiBaseline),
                currentSdkInt = CURRENT_SDK,
                currentBuildFingerprint = CURRENT_BUILD
            )
        )
    }

    @Test
    fun parse_rejectsMissingRequiredSectionWithoutThrowing() {
        val json = JsonParser.parseString(
            requireNotNull(SignalBaselineCodec.encodeToJson(validBaseline()))
        ).asJsonObject
        json.remove("location")

        val result = SignalBaselineCodec.parse(
            json = json.toString(),
            currentSdkInt = CURRENT_SDK,
            currentBuildFingerprint = CURRENT_BUILD
        )

        assertFalse(result.isValid)
        assertNull(result.snapshot)
    }

    private fun validBaseline(
        cellInfo: List<CellInfoSnapshot> = listOf(gsmCellInfoSnapshot(), lteCellInfoSnapshot()),
        neighboringCellInfo: List<NeighboringCellInfoSnapshot> = listOf(neighboringCellInfoSnapshot()),
        scanResults: List<ScanResultSnapshot> = listOf(scanResultSnapshot())
    ): SignalBaselineSnapshot {
        return SignalBaselineSnapshot(
            schemaVersion = SignalBaselineCodec.SCHEMA_VERSION,
            capturedAtMillis = 1_780_000_000_000L,
            captureSdkInt = CURRENT_SDK,
            captureBuildFingerprint = CURRENT_BUILD,
            location = LocationBaselineSnapshot(
                provider = "gps",
                latitude = 37.4219983,
                longitude = -122.084,
                timeMillis = 1_780_000_000_001L,
                elapsedRealtimeNanos = 123_456_789L,
                hasElapsedRealtimeUncertaintyNanos = true,
                elapsedRealtimeUncertaintyNanos = 7.5,
                hasAltitude = true,
                altitudeMeters = 12.25,
                hasAccuracy = true,
                accuracyMeters = 3.5f,
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
                extras = mapOf("source" to "fixture"),
                extrasUnsupportedKeys = listOf("parcelablePayload")
            ),
            cellular = CellularBaselineSnapshot(
                cellLocation = CellLocationSnapshot(
                    type = RADIO_TYPE_GSM,
                    gsm = GsmCellLocationSnapshot(lac = 404, cid = 12_345, psc = 7)
                ),
                cellInfo = cellInfo,
                cellInfoCount = cellInfo.size,
                neighboringCellInfo = neighboringCellInfo,
                neighboringCellInfoCount = neighboringCellInfo.size
            ),
            wifi = WifiBaselineSnapshot(
                connectionInfo = WifiInfoSnapshot(
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
                ),
                scanResults = scanResults,
                scanResultCount = scanResults.size
            )
        )
    }

    private fun gsmCellInfoSnapshot(parcelBytes: ByteArray? = byteArrayOf(1, 2, 3, 4)): CellInfoSnapshot {
        return CellInfoSnapshot(
            radioType = RADIO_TYPE_GSM,
            registered = true,
            cellConnectionStatus = 1,
            timestampMillis = 123_456L,
            identity = CellIdentitySnapshot(
                radioType = RADIO_TYPE_GSM,
                gsm = GsmCellIdentitySnapshot(
                    mccString = "310",
                    mncString = "260",
                    lac = 404,
                    cid = 12_345,
                    arfcn = 512,
                    bsic = 7,
                    operatorAlphaLong = "Test Carrier",
                    operatorAlphaShort = "TC"
                )
            ),
            signalStrength = CellSignalStrengthSnapshot(
                radioType = RADIO_TYPE_GSM,
                gsm = GsmCellSignalStrengthSnapshot(
                    dbm = -85,
                    asuLevel = 15,
                    level = 3,
                    bitErrorRate = 0,
                    timingAdvance = 2
                )
            ),
            parcelBase64 = parcelBytes?.let { base64(it) },
            parcelClassName = parcelBytes?.let { "android.telephony.CellInfoGsm" },
            parcelByteCount = parcelBytes?.size,
            parcelSdkInt = parcelBytes?.let { CURRENT_SDK },
            parcelBuildFingerprint = parcelBytes?.let { CURRENT_BUILD }
        )
    }

    private fun lteCellInfoSnapshot(): CellInfoSnapshot {
        return CellInfoSnapshot(
            radioType = RADIO_TYPE_LTE,
            registered = false,
            cellConnectionStatus = 0,
            timestampMillis = 234_567L,
            identity = CellIdentitySnapshot(
                radioType = RADIO_TYPE_LTE,
                lte = LteCellIdentitySnapshot(
                    mccString = "310",
                    mncString = "260",
                    ci = 345_678,
                    pci = 10,
                    tac = 22,
                    earfcn = 1_500,
                    bandwidth = 20_000,
                    operatorAlphaLong = "Test Carrier LTE",
                    operatorAlphaShort = "TCL"
                )
            ),
            signalStrength = CellSignalStrengthSnapshot(
                radioType = RADIO_TYPE_LTE,
                lte = LteCellSignalStrengthSnapshot(
                    dbm = -95,
                    asuLevel = 45,
                    level = 4,
                    cqi = 9,
                    cqiTableIndex = 1,
                    rsrp = -100,
                    rsrq = -10,
                    rssnr = 30,
                    timingAdvance = 3,
                    rssi = -70
                )
            )
        )
    }

    private fun neighboringCellInfoSnapshot(): NeighboringCellInfoSnapshot {
        val parcelBytes = byteArrayOf(5, 6, 7)
        return NeighboringCellInfoSnapshot(
            radioType = RADIO_TYPE_GSM,
            networkType = 1,
            cid = 67_890,
            lac = 405,
            psc = 8,
            rssi = 18,
            parcelBase64 = base64(parcelBytes),
            parcelClassName = "android.telephony.NeighboringCellInfo",
            parcelByteCount = parcelBytes.size,
            parcelSdkInt = CURRENT_SDK,
            parcelBuildFingerprint = CURRENT_BUILD
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

    private fun ssidBytesBase64(): String = base64("TestNet".toByteArray(Charsets.UTF_8))

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val CURRENT_SDK = 36
        const val CURRENT_BUILD = "google/sdk_gphone64_x86_64/fixture:16/BD1A.250000.001/1234567:userdebug/test-keys"
    }
}
