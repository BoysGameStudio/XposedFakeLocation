package com.noobexon.xposedfakelocation.testutil

import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellularBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LocationBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NeighboringCellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_GSM
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_LTE
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiInfoSnapshot
import java.util.Base64

object SignalBaselineTestFixtures {
    const val CURRENT_SDK = 36
    const val CURRENT_BUILD = "google/sdk_gphone64_x86_64/fixture:16/BD1A.250000.001/1234567:userdebug/test-keys"

    fun validBaseline(
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

    fun gsmCellInfoSnapshot(parcelBytes: ByteArray? = byteArrayOf(1, 2, 3, 4)): CellInfoSnapshot {
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

    fun lteCellInfoSnapshot(): CellInfoSnapshot {
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

    fun neighboringCellInfoSnapshot(): NeighboringCellInfoSnapshot {
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

    fun scanResultSnapshot(): ScanResultSnapshot {
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
}
