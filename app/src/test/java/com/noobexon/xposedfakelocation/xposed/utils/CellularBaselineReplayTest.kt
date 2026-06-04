package com.noobexon.xposedfakelocation.xposed.utils

import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NeighboringCellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_CDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_GSM
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_WCDMA
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import com.noobexon.xposedfakelocation.xposed.utils.CellularBaselineReplay.CellLocationReplayKind
import com.noobexon.xposedfakelocation.xposed.utils.CellularBaselineReplay.NeighboringReplayDecision
import com.noobexon.xposedfakelocation.xposed.utils.CellularBaselineReplay.ParcelReplayValidation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class CellularBaselineReplayTest {
    @Before
    fun setUp() {
        CellularBaselineReplay.currentSdkIntProvider = { SignalBaselineTestFixtures.CURRENT_SDK }
        CellularBaselineReplay.currentBuildFingerprintProvider = { SignalBaselineTestFixtures.CURRENT_BUILD }
    }

    @After
    fun tearDown() {
        CellularBaselineReplay.currentSdkIntProvider = { 0 }
        CellularBaselineReplay.currentBuildFingerprintProvider = { "" }
    }

    @Test
    fun cellLocationReplayKind_acceptsCompleteTypedGsmAndCdmaSnapshots() {
        val gsm = CellLocationSnapshot(
            type = RADIO_TYPE_GSM,
            gsm = GsmCellLocationSnapshot(lac = 321, cid = 6_543, psc = 9)
        )
        val cdma = CellLocationSnapshot(
            type = RADIO_TYPE_CDMA,
            cdma = CdmaCellLocationSnapshot(
                baseStationId = 42,
                baseStationLatitude = 12_345,
                baseStationLongitude = 54_321,
                systemId = 8,
                networkId = 9
            )
        )

        assertEquals(CellLocationReplayKind.GSM, CellularBaselineReplay.cellLocationReplayKind(gsm))
        assertEquals(CellLocationReplayKind.CDMA, CellularBaselineReplay.cellLocationReplayKind(cdma))
    }

    @Test
    fun cellLocationReplayKind_preservesPartialGsmAndRejectsMissingRequiredCdmaFields() {
        val partialGsm = CellLocationSnapshot(
            type = RADIO_TYPE_GSM,
            gsm = GsmCellLocationSnapshot(lac = 321, cid = null, psc = 9)
        )
        val emptyGsm = CellLocationSnapshot(
            type = RADIO_TYPE_GSM,
            gsm = GsmCellLocationSnapshot(lac = null, cid = null, psc = null)
        )
        val missingCdmaNetwork = CellLocationSnapshot(
            type = RADIO_TYPE_CDMA,
            cdma = CdmaCellLocationSnapshot(
                baseStationId = 42,
                baseStationLatitude = 12_345,
                baseStationLongitude = 54_321,
                systemId = 8,
                networkId = null
            )
        )

        assertEquals(CellLocationReplayKind.GSM, CellularBaselineReplay.cellLocationReplayKind(partialGsm))
        assertEquals(CellLocationReplayKind.NONE, CellularBaselineReplay.cellLocationReplayKind(emptyGsm))
        assertEquals(CellLocationReplayKind.NONE, CellularBaselineReplay.cellLocationReplayKind(missingCdmaNetwork))
        assertEquals(CellLocationReplayKind.NONE, CellularBaselineReplay.cellLocationReplayKind(null))
    }

    @Test
    fun cellInfoParcelValidation_coversValidMismatchCorruptAndUnknownRecords() {
        val valid = SignalBaselineTestFixtures.gsmCellInfoSnapshot(parcelBytes = byteArrayOf(1, 2, 3))
        val noParcel = SignalBaselineTestFixtures.gsmCellInfoSnapshot(parcelBytes = null)
        val sdkMismatch = valid.copy(parcelSdkInt = SignalBaselineTestFixtures.CURRENT_SDK + 1)
        val buildMismatch = valid.copy(parcelBuildFingerprint = "other/build/fingerprint")
        val corruptBase64 = valid.copy(parcelBase64 = "not-base64", parcelByteCount = 12)
        val unknownClass = valid.copy(parcelClassName = "android.telephony.CellInfoUnknown")
        val sizeMismatch = valid.copy(parcelByteCount = 4)

        assertEquals(ParcelReplayValidation.VALID, CellularBaselineReplay.cellInfoParcelValidation(valid))
        assertEquals(ParcelReplayValidation.MISSING_METADATA, CellularBaselineReplay.cellInfoParcelValidation(noParcel))
        assertEquals(ParcelReplayValidation.SDK_MISMATCH, CellularBaselineReplay.cellInfoParcelValidation(sdkMismatch))
        assertEquals(ParcelReplayValidation.BUILD_MISMATCH, CellularBaselineReplay.cellInfoParcelValidation(buildMismatch))
        assertEquals(ParcelReplayValidation.BASE64_INVALID, CellularBaselineReplay.cellInfoParcelValidation(corruptBase64))
        assertEquals(ParcelReplayValidation.UNSUPPORTED_CLASS, CellularBaselineReplay.cellInfoParcelValidation(unknownClass))
        assertEquals(ParcelReplayValidation.SIZE_MISMATCH, CellularBaselineReplay.cellInfoParcelValidation(sizeMismatch))
    }

    @Test
    fun neighboringReplayDecision_prefersParcelAndAllowsTypedOnlyWhenEnoughFieldsExist() {
        val parcel = SignalBaselineTestFixtures.neighboringCellInfoSnapshot()
        val typedGsm = NeighboringCellInfoSnapshot(
            radioType = RADIO_TYPE_GSM,
            networkType = 1,
            cid = 0x0044,
            lac = 0x0022,
            psc = null,
            rssi = 16
        )
        val typedWcdma = NeighboringCellInfoSnapshot(
            radioType = RADIO_TYPE_WCDMA,
            networkType = 3,
            cid = null,
            lac = null,
            psc = 12,
            rssi = 14
        )
        val missingRssi = typedGsm.copy(rssi = null)
        val missingIdentity = typedGsm.copy(cid = null, lac = null)

        assertEquals(NeighboringReplayDecision.PARCEL, CellularBaselineReplay.neighboringReplayDecision(parcel))
        assertEquals(NeighboringReplayDecision.TYPED, CellularBaselineReplay.neighboringReplayDecision(typedGsm))
        assertEquals(NeighboringReplayDecision.TYPED, CellularBaselineReplay.neighboringReplayDecision(typedWcdma))
        assertEquals(NeighboringReplayDecision.NONE, CellularBaselineReplay.neighboringReplayDecision(missingRssi))
        assertEquals(NeighboringReplayDecision.NONE, CellularBaselineReplay.neighboringReplayDecision(missingIdentity))
    }

    @Test
    fun neighboringParcelValidation_coversValidAndFailClosedMetadata() {
        val valid = SignalBaselineTestFixtures.neighboringCellInfoSnapshot().copy(
            parcelBase64 = Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7)),
            parcelByteCount = 3
        )
        val corrupt = valid.copy(parcelBase64 = "not-base64", parcelByteCount = 12)
        val buildMismatch = valid.copy(parcelBuildFingerprint = "other/build/fingerprint")

        assertEquals(ParcelReplayValidation.VALID, CellularBaselineReplay.neighboringParcelValidation(valid))
        assertEquals(ParcelReplayValidation.BASE64_INVALID, CellularBaselineReplay.neighboringParcelValidation(corrupt))
        assertEquals(ParcelReplayValidation.BUILD_MISMATCH, CellularBaselineReplay.neighboringParcelValidation(buildMismatch))
    }

    @Test
    fun missingBaselineFailsClosedToEmptyListsWithoutFrameworkConstruction() {
        assertTrue(CellularBaselineReplay.replayAllCellInfo(null).isEmpty())
        assertTrue(CellularBaselineReplay.replayNeighboringCellInfo(null).isEmpty())
    }

}
