package com.noobexon.xposedfakelocation.xposed.utils

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.NeighboringCellInfo
import android.telephony.TelephonyManager
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellularBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NeighboringCellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_CDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_GSM
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_LTE
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_NR
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_TDSCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_WCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import java.lang.reflect.Field
import java.util.Base64

object CellularBaselineReplay {
    @Volatile
    internal var currentSdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    @Volatile
    internal var currentBuildFingerprintProvider: () -> String = { Build.FINGERPRINT.orEmpty() }

    @Volatile
    internal var elapsedRealtimeNanosProvider: () -> Long = { SystemClock.elapsedRealtimeNanos() }

    fun replayCellLocation(cellular: CellularBaselineSnapshot?): CellLocation? {
        return replayCellLocation(cellular?.cellLocation)
    }

    @Suppress("DEPRECATION")
    fun replayCellLocation(snapshot: CellLocationSnapshot?): CellLocation? {
        return try {
            when (cellLocationReplayKind(snapshot)) {
                CellLocationReplayKind.GSM -> {
                    val gsm = snapshot?.gsm ?: return null
                    replayGsmCellLocation(
                        lac = gsm.lac ?: UNKNOWN_CELL_LOCATION_VALUE,
                        cid = gsm.cid ?: UNKNOWN_CELL_LOCATION_VALUE,
                        psc = gsm.psc
                    )
                }
                CellLocationReplayKind.CDMA -> {
                    val cdma = snapshot?.cdma ?: return null
                    CdmaCellLocation().apply {
                        setCellLocationData(
                            cdma.baseStationId ?: return null,
                            cdma.baseStationLatitude ?: return null,
                            cdma.baseStationLongitude ?: return null,
                            cdma.systemId ?: return null,
                            cdma.networkId ?: return null
                        )
                    }
                }
                CellLocationReplayKind.NONE -> null
            }
        } catch (throwable: Throwable) {
            null
        }
    }

    fun replayCellLocationBundle(cellular: CellularBaselineSnapshot?): Bundle? {
        return replayCellLocationBundle(cellular?.cellLocation)
    }

    fun replayCellIdentity(cellular: CellularBaselineSnapshot?): CellIdentity? {
        val records = replayAllCellInfo(cellular)
        return (records.firstOrNull { it.isRegistered } ?: records.firstOrNull())?.cellIdentity
    }

    @Suppress("DEPRECATION")
    fun replayCellLocationBundle(snapshot: CellLocationSnapshot?): Bundle? {
        val location = replayCellLocation(snapshot) ?: return null
        return try {
            Bundle().also { bundle ->
                when (location) {
                    is GsmCellLocation -> location.fillInNotifierBundle(bundle)
                    is CdmaCellLocation -> location.fillInNotifierBundle(bundle)
                    else -> return null
                }
            }
        } catch (throwable: Throwable) {
            null
        }
    }

    fun replayAllCellInfo(cellular: CellularBaselineSnapshot?): List<CellInfo> {
        val records = cellular?.cellInfo.orEmpty()
        if (records.isEmpty()) return emptyList()
        val currentSdkInt = currentSdkIntProvider()
        val currentBuildFingerprint = currentBuildFingerprintProvider()
        return records.mapNotNull { snapshot ->
            replayCellInfo(snapshot, currentSdkInt, currentBuildFingerprint)
        }
    }

    fun replayNeighboringCellInfo(cellular: CellularBaselineSnapshot?): List<NeighboringCellInfo> {
        val records = cellular?.neighboringCellInfo.orEmpty()
        if (records.isEmpty()) return emptyList()
        val currentSdkInt = currentSdkIntProvider()
        val currentBuildFingerprint = currentBuildFingerprintProvider()
        return records.mapNotNull { snapshot ->
            replayNeighboringCellInfo(snapshot, currentSdkInt, currentBuildFingerprint)
        }
    }

    internal fun cellLocationReplayKind(snapshot: CellLocationSnapshot?): CellLocationReplayKind {
        return when (snapshot?.type) {
            RADIO_TYPE_GSM -> {
                val gsm = snapshot.gsm ?: return CellLocationReplayKind.NONE
                if (gsm.lac == null && gsm.cid == null && gsm.psc == null) {
                    CellLocationReplayKind.NONE
                } else {
                    CellLocationReplayKind.GSM
                }
            }
            RADIO_TYPE_CDMA -> {
                val cdma = snapshot.cdma ?: return CellLocationReplayKind.NONE
                if (cdma.baseStationId == null || cdma.baseStationLatitude == null ||
                    cdma.baseStationLongitude == null || cdma.systemId == null || cdma.networkId == null
                ) {
                    CellLocationReplayKind.NONE
                } else {
                    CellLocationReplayKind.CDMA
                }
            }
            else -> CellLocationReplayKind.NONE
        }
    }

    internal fun replayCellInfo(
        snapshot: CellInfoSnapshot,
        currentSdkInt: Int = currentSdkIntProvider(),
        currentBuildFingerprint: String = currentBuildFingerprintProvider(),
        elapsedRealtimeNanos: Long? = null
    ): CellInfo? {
        if (!snapshot.hasParcelMetadata()) return null
        return replayParcelable(
            parcelBase64 = snapshot.parcelBase64,
            parcelClassName = snapshot.parcelClassName,
            parcelByteCount = snapshot.parcelByteCount,
            parcelSdkInt = snapshot.parcelSdkInt,
            parcelBuildFingerprint = snapshot.parcelBuildFingerprint,
            currentSdkInt = currentSdkInt,
            currentBuildFingerprint = currentBuildFingerprint,
            allowedClassNames = SignalBaselineCodec.allowedCellInfoParcelClassNames,
            expectedType = CellInfo::class.java
        )?.also { cellInfo ->
            applyReplayTimestampFields(
                target = cellInfo,
                elapsedRealtimeNanos = elapsedRealtimeNanos ?: elapsedRealtimeNanosProvider()
            )
        }
    }

    internal fun applyReplayTimestampFields(target: Any, elapsedRealtimeNanos: Long): Boolean {
        if (elapsedRealtimeNanos < 0L) return false
        val elapsedRealtimeMillis = elapsedRealtimeNanos / 1_000_000L
        var applied = false
        for (fieldName in CELL_INFO_TIMESTAMP_NANOS_FIELDS) {
            applied = target.setLongFieldIfPresent(fieldName, elapsedRealtimeNanos) || applied
        }
        for (fieldName in CELL_INFO_TIMESTAMP_MILLIS_FIELDS) {
            applied = target.setLongFieldIfPresent(fieldName, elapsedRealtimeMillis) || applied
        }
        return applied
    }

    internal fun replayNeighboringCellInfo(
        snapshot: NeighboringCellInfoSnapshot,
        currentSdkInt: Int = currentSdkIntProvider(),
        currentBuildFingerprint: String = currentBuildFingerprintProvider()
    ): NeighboringCellInfo? {
        if (snapshot.hasParcelMetadata()) {
            return replayParcelable(
                parcelBase64 = snapshot.parcelBase64,
                parcelClassName = snapshot.parcelClassName,
                parcelByteCount = snapshot.parcelByteCount,
                parcelSdkInt = snapshot.parcelSdkInt,
                parcelBuildFingerprint = snapshot.parcelBuildFingerprint,
                currentSdkInt = currentSdkInt,
                currentBuildFingerprint = currentBuildFingerprint,
                allowedClassNames = SignalBaselineCodec.allowedNeighboringCellInfoParcelClassNames,
                expectedType = NeighboringCellInfo::class.java
            )
        }

        return replayNeighboringCellInfoFromFields(snapshot)
    }

    internal fun cellInfoParcelValidation(
        snapshot: CellInfoSnapshot,
        currentSdkInt: Int = currentSdkIntProvider(),
        currentBuildFingerprint: String = currentBuildFingerprintProvider()
    ): ParcelReplayValidation {
        return parcelValidation(
            parcelBase64 = snapshot.parcelBase64,
            parcelClassName = snapshot.parcelClassName,
            parcelByteCount = snapshot.parcelByteCount,
            parcelSdkInt = snapshot.parcelSdkInt,
            parcelBuildFingerprint = snapshot.parcelBuildFingerprint,
            currentSdkInt = currentSdkInt,
            currentBuildFingerprint = currentBuildFingerprint,
            allowedClassNames = SignalBaselineCodec.allowedCellInfoParcelClassNames
        )
    }

    internal fun neighboringParcelValidation(
        snapshot: NeighboringCellInfoSnapshot,
        currentSdkInt: Int = currentSdkIntProvider(),
        currentBuildFingerprint: String = currentBuildFingerprintProvider()
    ): ParcelReplayValidation {
        return parcelValidation(
            parcelBase64 = snapshot.parcelBase64,
            parcelClassName = snapshot.parcelClassName,
            parcelByteCount = snapshot.parcelByteCount,
            parcelSdkInt = snapshot.parcelSdkInt,
            parcelBuildFingerprint = snapshot.parcelBuildFingerprint,
            currentSdkInt = currentSdkInt,
            currentBuildFingerprint = currentBuildFingerprint,
            allowedClassNames = SignalBaselineCodec.allowedNeighboringCellInfoParcelClassNames
        )
    }

    internal fun neighboringReplayDecision(snapshot: NeighboringCellInfoSnapshot): NeighboringReplayDecision {
        if (snapshot.hasParcelMetadata()) return NeighboringReplayDecision.PARCEL
        if (snapshot.rssi == null) return NeighboringReplayDecision.NONE
        return when (snapshot.radioType) {
            RADIO_TYPE_GSM -> if (snapshot.cid != null) NeighboringReplayDecision.TYPED else NeighboringReplayDecision.NONE
            RADIO_TYPE_WCDMA,
            RADIO_TYPE_TDSCDMA -> if (snapshot.psc != null) NeighboringReplayDecision.TYPED else NeighboringReplayDecision.NONE
            else -> if (snapshot.cid != null) NeighboringReplayDecision.TYPED else NeighboringReplayDecision.NONE
        }
    }

    @Suppress("DEPRECATION")
    private fun replayGsmCellLocation(lac: Int, cid: Int, psc: Int?): GsmCellLocation {
        return runCatching {
            GsmCellLocation(
                Bundle().apply {
                    putInt(GSM_BUNDLE_LAC, lac)
                    putInt(GSM_BUNDLE_CID, cid)
                    psc?.let { putInt(GSM_BUNDLE_PSC, it) }
                }
            )
        }.getOrElse {
            GsmCellLocation().apply { setLacAndCid(lac, cid) }
        }
    }

    @Suppress("DEPRECATION")
    private fun replayNeighboringCellInfoFromFields(snapshot: NeighboringCellInfoSnapshot): NeighboringCellInfo? {
        val rssi = snapshot.rssi ?: return null
        val networkType = snapshot.networkType ?: snapshot.radioType.defaultNeighborNetworkType()
        return try {
            when (snapshot.radioType) {
                RADIO_TYPE_GSM -> replayGsmNeighbor(rssi, networkType, snapshot.lac, snapshot.cid)
                RADIO_TYPE_WCDMA,
                RADIO_TYPE_TDSCDMA -> replayPscNeighbor(rssi, networkType, snapshot.psc)
                else -> snapshot.cid?.let { NeighboringCellInfo(rssi, it) }
            }
        } catch (throwable: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun replayGsmNeighbor(rssi: Int, networkType: Int, lac: Int?, cid: Int?): NeighboringCellInfo? {
        val lacHex = lac?.toFourDigitHexOrNull()
        val cidHex = cid?.toFourDigitHexOrNull()
        return if (lacHex != null && cidHex != null) {
            NeighboringCellInfo(rssi, lacHex + cidHex, networkType)
        } else {
            cid?.let { NeighboringCellInfo(rssi, it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun replayPscNeighbor(rssi: Int, networkType: Int, psc: Int?): NeighboringCellInfo? {
        val pscHex = psc?.toFourDigitHexOrNull() ?: return null
        return NeighboringCellInfo(rssi, pscHex, networkType)
    }

    private fun parcelValidation(
        parcelBase64: String?,
        parcelClassName: String?,
        parcelByteCount: Int?,
        parcelSdkInt: Int?,
        parcelBuildFingerprint: String?,
        currentSdkInt: Int,
        currentBuildFingerprint: String,
        allowedClassNames: Set<String>
    ): ParcelReplayValidation {
        val metadata = listOf(parcelBase64, parcelClassName, parcelByteCount, parcelSdkInt, parcelBuildFingerprint)
        if (metadata.all { it == null }) return ParcelReplayValidation.MISSING_METADATA
        if (metadata.any { it == null }) return ParcelReplayValidation.INCOMPLETE_METADATA

        val className = parcelClassName ?: return ParcelReplayValidation.INCOMPLETE_METADATA
        val byteCount = parcelByteCount ?: return ParcelReplayValidation.INCOMPLETE_METADATA
        val sdkInt = parcelSdkInt ?: return ParcelReplayValidation.INCOMPLETE_METADATA
        val buildFingerprint = parcelBuildFingerprint ?: return ParcelReplayValidation.INCOMPLETE_METADATA
        val base64 = parcelBase64 ?: return ParcelReplayValidation.INCOMPLETE_METADATA

        if (className !in allowedClassNames) return ParcelReplayValidation.UNSUPPORTED_CLASS
        if (sdkInt != currentSdkInt) return ParcelReplayValidation.SDK_MISMATCH
        if (buildFingerprint != currentBuildFingerprint) return ParcelReplayValidation.BUILD_MISMATCH
        if (byteCount <= 0 || byteCount > SignalBaselineCodec.MAX_PARCEL_BLOB_BYTES) {
            return ParcelReplayValidation.SIZE_INVALID
        }
        val bytes = decodeBase64(base64) ?: return ParcelReplayValidation.BASE64_INVALID
        if (bytes.size != byteCount) return ParcelReplayValidation.SIZE_MISMATCH
        return ParcelReplayValidation.VALID
    }

    private fun <T : Any> replayParcelable(
        parcelBase64: String?,
        parcelClassName: String?,
        parcelByteCount: Int?,
        parcelSdkInt: Int?,
        parcelBuildFingerprint: String?,
        currentSdkInt: Int,
        currentBuildFingerprint: String,
        allowedClassNames: Set<String>,
        expectedType: Class<T>
    ): T? {
        val metadata = parcelMetadataOrNull(
            parcelBase64 = parcelBase64,
            parcelClassName = parcelClassName,
            parcelByteCount = parcelByteCount,
            parcelSdkInt = parcelSdkInt,
            parcelBuildFingerprint = parcelBuildFingerprint,
            currentSdkInt = currentSdkInt,
            currentBuildFingerprint = currentBuildFingerprint,
            allowedClassNames = allowedClassNames,
            expectedType = expectedType
        ) ?: return null

        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(metadata.bytes, 0, metadata.bytes.size)
            parcel.setDataPosition(0)
            val creator = metadata.parcelClass.getField("CREATOR").get(null) as? Parcelable.Creator<*>
                ?: return null
            val value = creator.createFromParcel(parcel)
            if (!metadata.parcelClass.isInstance(value) || !expectedType.isInstance(value)) {
                null
            } else {
                expectedType.cast(value)
            }
        } catch (throwable: Throwable) {
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun <T : Any> parcelMetadataOrNull(
        parcelBase64: String?,
        parcelClassName: String?,
        parcelByteCount: Int?,
        parcelSdkInt: Int?,
        parcelBuildFingerprint: String?,
        currentSdkInt: Int,
        currentBuildFingerprint: String,
        allowedClassNames: Set<String>,
        expectedType: Class<T>
    ): ParcelReplayMetadata? {
        if (parcelValidation(
                parcelBase64 = parcelBase64,
                parcelClassName = parcelClassName,
                parcelByteCount = parcelByteCount,
                parcelSdkInt = parcelSdkInt,
                parcelBuildFingerprint = parcelBuildFingerprint,
                currentSdkInt = currentSdkInt,
                currentBuildFingerprint = currentBuildFingerprint,
                allowedClassNames = allowedClassNames
            ) != ParcelReplayValidation.VALID
        ) {
            return null
        }

        val className = parcelClassName ?: return null
        val base64 = parcelBase64 ?: return null
        val bytes = decodeBase64(base64) ?: return null

        val parcelClass = runCatching { Class.forName(className) }.getOrNull() ?: return null
        if (!expectedType.isAssignableFrom(parcelClass)) return null

        return ParcelReplayMetadata(parcelClass = parcelClass, bytes = bytes)
    }

    private fun decodeBase64(base64: String): ByteArray? {
        val maxBase64Chars = ((SignalBaselineCodec.MAX_PARCEL_BLOB_BYTES + 2) / 3) * 4
        if (base64.length > maxBase64Chars) return null
        return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
    }

    private fun Any.setLongFieldIfPresent(fieldName: String, value: Long): Boolean {
        return runCatching {
            val field = javaClass.findFieldInHierarchy(fieldName) ?: return@runCatching false
            field.isAccessible = true
            field.setLong(this, value)
            true
        }.getOrDefault(false)
    }

    private fun Class<*>.findFieldInHierarchy(fieldName: String): Field? {
        return generateSequence(this) { it.superclass }
            .firstNotNullOfOrNull { type ->
                runCatching { type.getDeclaredField(fieldName) }.getOrNull()
            }
    }

    private fun CellInfoSnapshot.hasParcelMetadata(): Boolean {
        return parcelBase64 != null || parcelClassName != null || parcelByteCount != null ||
            parcelSdkInt != null || parcelBuildFingerprint != null
    }

    private fun NeighboringCellInfoSnapshot.hasParcelMetadata(): Boolean {
        return parcelBase64 != null || parcelClassName != null || parcelByteCount != null ||
            parcelSdkInt != null || parcelBuildFingerprint != null
    }

    private fun Int.toFourDigitHexOrNull(): String? {
        if (this !in 0..0xffff) return null
        return toString(16).padStart(4, '0')
    }

    private fun String.defaultNeighborNetworkType(): Int {
        return when (this) {
            RADIO_TYPE_GSM -> TelephonyManager.NETWORK_TYPE_GPRS
            RADIO_TYPE_WCDMA -> TelephonyManager.NETWORK_TYPE_UMTS
            RADIO_TYPE_LTE -> TelephonyManager.NETWORK_TYPE_LTE
            RADIO_TYPE_TDSCDMA -> TelephonyManager.NETWORK_TYPE_TD_SCDMA
            RADIO_TYPE_NR -> TelephonyManager.NETWORK_TYPE_NR
            RADIO_TYPE_CDMA -> TelephonyManager.NETWORK_TYPE_CDMA
            else -> TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
    }

    internal enum class CellLocationReplayKind { GSM, CDMA, NONE }

    internal enum class ParcelReplayValidation {
        VALID,
        MISSING_METADATA,
        INCOMPLETE_METADATA,
        UNSUPPORTED_CLASS,
        SDK_MISMATCH,
        BUILD_MISMATCH,
        SIZE_INVALID,
        BASE64_INVALID,
        SIZE_MISMATCH
    }

    internal enum class NeighboringReplayDecision { PARCEL, TYPED, NONE }

    private data class ParcelReplayMetadata(
        val parcelClass: Class<*>,
        val bytes: ByteArray
    )

    private const val GSM_BUNDLE_LAC = "lac"
    private const val GSM_BUNDLE_CID = "cid"
    private const val GSM_BUNDLE_PSC = "psc"
    private const val UNKNOWN_CELL_LOCATION_VALUE = -1
    private val CELL_INFO_TIMESTAMP_NANOS_FIELDS = listOf("mTimeStamp", "mTimestamp")
    private val CELL_INFO_TIMESTAMP_MILLIS_FIELDS = listOf("mTimestampMillis", "mTimeStampMillis")
}
