package com.noobexon.xposedfakelocation.data.model

data class ShanghaiCellTower(
    val radio: String,
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cid: Int,
    val pscOrPci: Int,
    val longitude: Double,
    val latitude: Double,
    val rangeMeters: Int?,
    val averageSignal: Int?
)
