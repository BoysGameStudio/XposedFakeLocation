@file:Suppress("DEPRECATION")

package com.noobexon.xposedfakelocation.xposed.utils

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Build
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiInfoSnapshot
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Base64

object WifiBaselineReplay {
    internal const val PLACEHOLDER_SSID = "AndroidAP"
    internal const val PLACEHOLDER_BSSID = "02:00:00:00:00:00"
    private const val MAX_WIFI_SSID_BYTES = 32
    private const val DEFAULT_RSSI = -60
    private const val DEFAULT_NETWORK_ID = -1

    fun replayConnectionInfo(wifi: WifiBaselineSnapshot?): WifiInfo {
        return replayConnectionInfo(connectionReplayResult(wifi))
    }

    internal fun replayConnectionInfo(result: ConnectionReplayResult): WifiInfo {
        return runCatching {
            buildWifiInfo(result.values)
        }.getOrElse {
            buildWifiInfo(placeholderConnectionReplayValues())
        }
    }

    fun replayScanResults(wifi: WifiBaselineSnapshot?): List<ScanResult> {
        return replayScanResults(scanResultsReplayResult(wifi))
    }

    internal fun replayScanResults(plan: ScanResultsReplayPlan): Any? {
        return when (plan.returnKind) {
            ScanResultsReturnKind.LIST -> replayScanResults(plan.result)
            ScanResultsReturnKind.PARCELED_LIST_SLICE -> replayParceledListSlice(
                returnType = plan.returnType,
                scanResults = replayScanResults(plan.result)
            )
            ScanResultsReturnKind.UNSUPPORTED -> null
        }
    }

    internal fun replayScanResults(result: ScanResultsReplayResult): List<ScanResult> {
        if (result.kind == ScanResultsReplayKind.EMPTY) return emptyList()
        return result.values.map { values ->
            ScanResult().apply {
                SSID = values.ssid
                BSSID = values.bssid
                capabilities = values.capabilities
                values.level?.let { level = it }
                values.frequencyMhz?.let { frequency = it }
                values.channelWidth?.let { channelWidth = it }
                values.centerFreq0Mhz?.let { centerFreq0 = it }
                values.centerFreq1Mhz?.let { centerFreq1 = it }
                values.timestampMicros?.let { timestamp = it }
            }
        }
    }

    internal fun scanResultsReplayPlan(
        wifi: WifiBaselineSnapshot?,
        returnType: Class<*>?
    ): ScanResultsReplayPlan {
        val result = scanResultsReplayResult(wifi)
        return ScanResultsReplayPlan(
            result = result,
            returnType = returnType,
            returnKind = scanResultsReturnKind(returnType)
        )
    }

    internal fun connectionReplayResult(wifi: WifiBaselineSnapshot?): ConnectionReplayResult {
        val connectionInfo = wifi?.connectionInfo
        val values = connectionReplayValues(connectionInfo)
        return if (values == null) {
            ConnectionReplayResult(ConnectionReplayKind.PLACEHOLDER, placeholderConnectionReplayValues())
        } else {
            ConnectionReplayResult(ConnectionReplayKind.SAVED_BASELINE, values)
        }
    }

    internal fun scanResultsReturnKind(returnType: Class<*>?): ScanResultsReturnKind {
        if (returnType == null) return ScanResultsReturnKind.LIST
        if (List::class.java.isAssignableFrom(returnType)) return ScanResultsReturnKind.LIST
        if (returnType.hierarchyNames().any(::isParceledListSliceName)) {
            return ScanResultsReturnKind.PARCELED_LIST_SLICE
        }
        return ScanResultsReturnKind.UNSUPPORTED
    }

    internal fun scanResultsReturnKind(returnTypeName: String?): ScanResultsReturnKind {
        return when {
            returnTypeName == null -> ScanResultsReturnKind.LIST
            returnTypeName == List::class.java.name -> ScanResultsReturnKind.LIST
            isParceledListSliceName(returnTypeName) -> ScanResultsReturnKind.PARCELED_LIST_SLICE
            else -> ScanResultsReturnKind.UNSUPPORTED
        }
    }

    internal fun scanResultsReplayResult(wifi: WifiBaselineSnapshot?): ScanResultsReplayResult {
        if (wifi == null || wifi.scanResultCount != wifi.scanResults.size) {
            return ScanResultsReplayResult(ScanResultsReplayKind.EMPTY, emptyList())
        }

        val values = wifi.scanResults.mapNotNull(::scanResultReplayValues)
        return if (values.isEmpty()) {
            ScanResultsReplayResult(ScanResultsReplayKind.EMPTY, emptyList())
        } else {
            ScanResultsReplayResult(ScanResultsReplayKind.SAVED_BASELINE, values)
        }
    }

    internal fun connectionReplayValues(snapshot: WifiInfoSnapshot?): WifiConnectionReplayValues? {
        val snapshotValue = snapshot ?: return null
        val bssid = snapshotValue.bssid?.takeIf(::isReplayableBssid) ?: return null
        val ssidBytes = ssidBytesOrNull(snapshotValue.ssid, snapshotValue.ssidBytesBase64) ?: return null
        val rssi = snapshotValue.rssi ?: return null

        return WifiConnectionReplayValues(
            ssid = snapshotValue.ssid,
            ssidBytes = ssidBytes,
            bssid = bssid,
            rssi = rssi,
            networkId = snapshotValue.networkId ?: DEFAULT_NETWORK_ID,
            frequencyMhz = snapshotValue.frequencyMhz,
            linkSpeedMbps = snapshotValue.linkSpeedMbps,
            rxLinkSpeedMbps = snapshotValue.rxLinkSpeedMbps,
            txLinkSpeedMbps = snapshotValue.txLinkSpeedMbps,
            wifiStandard = snapshotValue.wifiStandard,
            currentSecurityType = snapshotValue.currentSecurityType,
            subscriptionId = snapshotValue.subscriptionId
        )
    }

    internal fun scanResultReplayValues(snapshot: ScanResultSnapshot): WifiScanResultReplayValues? {
        if (snapshot.ssidBytesBase64 != null && decodeSsidBytes(snapshot.ssidBytesBase64) == null) return null
        val bssid = snapshot.bssid?.takeIf(::isReplayableBssid) ?: return null

        return WifiScanResultReplayValues(
            ssid = snapshot.ssid,
            bssid = bssid,
            capabilities = snapshot.capabilities,
            level = snapshot.level,
            frequencyMhz = snapshot.frequencyMhz,
            channelWidth = snapshot.channelWidth,
            centerFreq0Mhz = snapshot.centerFreq0Mhz,
            centerFreq1Mhz = snapshot.centerFreq1Mhz,
            timestampMicros = snapshot.timestampMicros,
            wifiStandard = snapshot.wifiStandard,
            is80211mcResponder = snapshot.is80211mcResponder
        )
    }

    internal fun placeholderConnectionReplayValues(): WifiConnectionReplayValues {
        return WifiConnectionReplayValues(
            ssid = PLACEHOLDER_SSID,
            ssidBytes = PLACEHOLDER_SSID.toByteArray(Charsets.UTF_8),
            bssid = PLACEHOLDER_BSSID,
            rssi = DEFAULT_RSSI,
            networkId = 0,
            frequencyMhz = null,
            linkSpeedMbps = null,
            rxLinkSpeedMbps = null,
            txLinkSpeedMbps = null,
            wifiStandard = null,
            currentSecurityType = null,
            subscriptionId = null
        )
    }

    private fun buildWifiInfo(values: WifiConnectionReplayValues): WifiInfo {
        val builder = WifiInfo.Builder()
            .setBssid(values.bssid)
            .setSsid(values.ssidBytes)
            .setRssi(values.rssi)
            .setNetworkId(values.networkId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            values.currentSecurityType?.let { runCatching { builder.setCurrentSecurityType(it) } }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            values.subscriptionId?.let { runCatching { builder.setSubscriptionId(it) } }
        }

        return builder.build()
    }

    private fun replayParceledListSlice(returnType: Class<*>?, scanResults: List<ScanResult>): Any? {
        val sliceType = returnType?.takeIf { scanResultsReturnKind(it) == ScanResultsReturnKind.PARCELED_LIST_SLICE }
            ?: return null
        return newParceledListSlice(sliceType, scanResults)
            ?: newParceledListSlice(sliceType, emptyList())
            ?: emptyParceledListSlice(sliceType)
    }

    private fun newParceledListSlice(returnType: Class<*>, scanResults: List<ScanResult>): Any? {
        return runCatching {
            val constructor = returnType.findListConstructor() ?: return@runCatching null
            constructor.newInstance(scanResults)?.takeIf(returnType::isInstance)
        }.getOrNull()
    }

    private fun emptyParceledListSlice(returnType: Class<*>): Any? {
        return runCatching {
            val method = returnType.findNoArgStaticMethod("emptyList") ?: return@runCatching null
            method.invoke(null)?.takeIf(returnType::isInstance)
        }.getOrNull()
    }

    private fun Class<*>.findListConstructor(): Constructor<*>? {
        return runCatching { getConstructor(List::class.java) }.getOrNull()
            ?: runCatching {
                getDeclaredConstructor(List::class.java).apply { isAccessible = true }
            }.getOrNull()
    }

    private fun Class<*>.findNoArgStaticMethod(name: String): Method? {
        return runCatching { getMethod(name) }.getOrNull()
            ?: runCatching {
                getDeclaredMethod(name).apply { isAccessible = true }
            }.getOrNull()
    }

    private fun ssidBytesOrNull(ssid: String?, ssidBytesBase64: String?): ByteArray? {
        val decoded = ssidBytesBase64?.let(::decodeSsidBytes)
        if (ssidBytesBase64 != null) return decoded
        val bytes = ssid?.toByteArray(Charsets.UTF_8) ?: return null
        return bytes.takeIf { it.size <= MAX_WIFI_SSID_BYTES }
    }

    private fun decodeSsidBytes(base64: String): ByteArray? {
        val maxBase64Chars = ((MAX_WIFI_SSID_BYTES + 2) / 3) * 4
        if (base64.length > maxBase64Chars) return null
        return runCatching { Base64.getDecoder().decode(base64) }
            .getOrNull()
            ?.takeIf { it.size <= MAX_WIFI_SSID_BYTES }
    }

    private fun isReplayableBssid(value: String): Boolean {
        return value.length == 17 && value.split(':').size == 6 && value.split(':').all { part ->
            part.length == 2 && part.all { char -> char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' }
        }
    }

    private fun Class<*>.hierarchyNames(): Sequence<String> {
        return generateSequence(this) { it.superclass }.map { it.name }
    }

    private fun isParceledListSliceName(name: String): Boolean {
        return name == ANDROID_PARCELED_LIST_SLICE_CLASS_NAME ||
            name == MODULES_PARCELED_LIST_SLICE_CLASS_NAME
    }

    internal data class ConnectionReplayResult(
        val kind: ConnectionReplayKind,
        val values: WifiConnectionReplayValues
    )

    internal data class ScanResultsReplayResult(
        val kind: ScanResultsReplayKind,
        val values: List<WifiScanResultReplayValues>
    )

    internal data class ScanResultsReplayPlan(
        val result: ScanResultsReplayResult,
        val returnType: Class<*>?,
        val returnKind: ScanResultsReturnKind
    ) {
        val kind: ScanResultsReplayKind get() = result.kind
        val values: List<WifiScanResultReplayValues> get() = result.values
    }

    internal data class WifiConnectionReplayValues(
        val ssid: String?,
        val ssidBytes: ByteArray,
        val bssid: String,
        val rssi: Int,
        val networkId: Int,
        val frequencyMhz: Int?,
        val linkSpeedMbps: Int?,
        val rxLinkSpeedMbps: Int?,
        val txLinkSpeedMbps: Int?,
        val wifiStandard: Int?,
        val currentSecurityType: Int?,
        val subscriptionId: Int?
    )

    internal data class WifiScanResultReplayValues(
        val ssid: String?,
        val bssid: String,
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

    internal enum class ConnectionReplayKind { SAVED_BASELINE, PLACEHOLDER }
    internal enum class ScanResultsReplayKind { SAVED_BASELINE, EMPTY }
    internal enum class ScanResultsReturnKind { LIST, PARCELED_LIST_SLICE, UNSUPPORTED }

    private const val ANDROID_PARCELED_LIST_SLICE_CLASS_NAME = "android.content.pm.ParceledListSlice"
    private const val MODULES_PARCELED_LIST_SLICE_CLASS_NAME = "com.android.modules.utils.ParceledListSlice"
}
