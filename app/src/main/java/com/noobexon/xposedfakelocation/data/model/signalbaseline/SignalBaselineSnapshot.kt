package com.noobexon.xposedfakelocation.data.model.signalbaseline

data class SignalBaselineSnapshot(
    val schemaVersion: Int,
    val capturedAtMillis: Long,
    val captureSdkInt: Int,
    val captureBuildFingerprint: String,
    val location: LocationBaselineSnapshot,
    val cellular: CellularBaselineSnapshot,
    val wifi: WifiBaselineSnapshot
)
