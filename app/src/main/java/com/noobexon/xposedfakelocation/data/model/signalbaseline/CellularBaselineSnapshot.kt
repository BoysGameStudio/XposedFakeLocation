package com.noobexon.xposedfakelocation.data.model.signalbaseline

const val RADIO_TYPE_GSM = "gsm"
const val RADIO_TYPE_LTE = "lte"
const val RADIO_TYPE_WCDMA = "wcdma"
const val RADIO_TYPE_NR = "nr"
const val RADIO_TYPE_TDSCDMA = "tdscdma"
const val RADIO_TYPE_CDMA = "cdma"
const val RADIO_TYPE_UNKNOWN = "unknown"

data class CellularBaselineSnapshot(
    val cellLocation: CellLocationSnapshot?,
    val cellInfo: List<CellInfoSnapshot>,
    val cellInfoCount: Int,
    val neighboringCellInfo: List<NeighboringCellInfoSnapshot>,
    val neighboringCellInfoCount: Int
)

data class CellLocationSnapshot(
    val type: String,
    val gsm: GsmCellLocationSnapshot? = null,
    val cdma: CdmaCellLocationSnapshot? = null
)

data class GsmCellLocationSnapshot(
    val lac: Int?,
    val cid: Int?,
    val psc: Int?
)

data class CdmaCellLocationSnapshot(
    val baseStationId: Int?,
    val baseStationLatitude: Int?,
    val baseStationLongitude: Int?,
    val systemId: Int?,
    val networkId: Int?
)

data class CellInfoSnapshot(
    val radioType: String,
    val registered: Boolean,
    val cellConnectionStatus: Int?,
    val timestampMillis: Long?,
    val identity: CellIdentitySnapshot,
    val signalStrength: CellSignalStrengthSnapshot,
    val parcelBase64: String? = null,
    val parcelClassName: String? = null,
    val parcelByteCount: Int? = null,
    val parcelSdkInt: Int? = null,
    val parcelBuildFingerprint: String? = null
)

data class NeighboringCellInfoSnapshot(
    val radioType: String,
    val networkType: Int?,
    val cid: Int?,
    val lac: Int?,
    val psc: Int?,
    val rssi: Int?,
    val parcelBase64: String? = null,
    val parcelClassName: String? = null,
    val parcelByteCount: Int? = null,
    val parcelSdkInt: Int? = null,
    val parcelBuildFingerprint: String? = null
)

data class CellIdentitySnapshot(
    val radioType: String,
    val gsm: GsmCellIdentitySnapshot? = null,
    val lte: LteCellIdentitySnapshot? = null,
    val wcdma: WcdmaCellIdentitySnapshot? = null,
    val nr: NrCellIdentitySnapshot? = null,
    val tdscdma: TdscdmaCellIdentitySnapshot? = null,
    val cdma: CdmaCellIdentitySnapshot? = null,
    val generic: GenericCellRecordSnapshot? = null
)

data class CellSignalStrengthSnapshot(
    val radioType: String,
    val gsm: GsmCellSignalStrengthSnapshot? = null,
    val lte: LteCellSignalStrengthSnapshot? = null,
    val wcdma: WcdmaCellSignalStrengthSnapshot? = null,
    val nr: NrCellSignalStrengthSnapshot? = null,
    val tdscdma: TdscdmaCellSignalStrengthSnapshot? = null,
    val cdma: CdmaCellSignalStrengthSnapshot? = null,
    val generic: GenericCellSignalStrengthSnapshot? = null
)

data class GenericCellRecordSnapshot(
    val className: String?,
    val fields: Map<String, String?>
)

data class GenericCellSignalStrengthSnapshot(
    val className: String?,
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val fields: Map<String, String?>
)

data class GsmCellIdentitySnapshot(
    val mccString: String?,
    val mncString: String?,
    val lac: Int?,
    val cid: Int?,
    val arfcn: Int?,
    val bsic: Int?,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class GsmCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val bitErrorRate: Int?,
    val timingAdvance: Int?
)

data class LteCellIdentitySnapshot(
    val mccString: String?,
    val mncString: String?,
    val ci: Int?,
    val pci: Int?,
    val tac: Int?,
    val earfcn: Int?,
    val bandwidth: Int?,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class LteCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val cqi: Int?,
    val cqiTableIndex: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val timingAdvance: Int?,
    val rssi: Int?
)

data class WcdmaCellIdentitySnapshot(
    val mccString: String?,
    val mncString: String?,
    val lac: Int?,
    val cid: Int?,
    val psc: Int?,
    val uarfcn: Int?,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class WcdmaCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val ecNo: Int?
)

data class NrCellIdentitySnapshot(
    val mccString: String?,
    val mncString: String?,
    val nci: Long?,
    val pci: Int?,
    val tac: Int?,
    val nrarfcn: Int?,
    val bands: List<Int>,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class NrCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val csiRsrp: Int?,
    val csiRsrq: Int?,
    val csiSinr: Int?,
    val ssRsrp: Int?,
    val ssRsrq: Int?,
    val ssSinr: Int?
)

data class TdscdmaCellIdentitySnapshot(
    val mccString: String?,
    val mncString: String?,
    val lac: Int?,
    val cid: Int?,
    val cpid: Int?,
    val uarfcn: Int?,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class TdscdmaCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val rscp: Int?
)

data class CdmaCellIdentitySnapshot(
    val baseStationId: Int?,
    val baseStationLatitude: Int?,
    val baseStationLongitude: Int?,
    val systemId: Int?,
    val networkId: Int?,
    val operatorAlphaLong: String?,
    val operatorAlphaShort: String?
)

data class CdmaCellSignalStrengthSnapshot(
    val dbm: Int?,
    val asuLevel: Int?,
    val level: Int?,
    val cdmaDbm: Int?,
    val cdmaEcio: Int?,
    val evdoDbm: Int?,
    val evdoEcio: Int?,
    val evdoSnr: Int?
)
