package com.noobexon.xposedfakelocation.data.model.signalbaseline

data class WifiBaselineSnapshot(
    val connectionInfo: WifiInfoSnapshot?,
    val scanResults: List<ScanResultSnapshot>,
    val scanResultCount: Int
)

data class WifiInfoSnapshot(
    val ssid: String?,
    val ssidBytesBase64: String?,
    val bssid: String?,
    val rssi: Int?,
    val networkId: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val wifiStandard: Int?,
    val currentSecurityType: Int?,
    val subscriptionId: Int?
)

data class ScanResultSnapshot(
    val ssid: String?,
    val ssidBytesBase64: String?,
    val bssid: String?,
    val capabilities: String?,
    val level: Int?,
    val frequencyMhz: Int?,
    val channelWidth: Int?,
    val centerFreq0Mhz: Int?,
    val centerFreq1Mhz: Int?,
    val timestampMicros: Long?,
    val wifiStandard: Int?,
    val is80211mcResponder: Boolean?
)
