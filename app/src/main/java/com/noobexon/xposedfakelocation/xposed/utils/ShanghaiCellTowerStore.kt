package com.noobexon.xposedfakelocation.xposed.utils

import com.noobexon.xposedfakelocation.data.model.ShanghaiCellTower
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ShanghaiCellTowerStore private constructor(
    private val towers: List<ShanghaiCellTower>
) {
    fun findNearest(
        latitude: Double,
        longitude: Double,
        limit: Int = DEFAULT_LIMIT,
        maxDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS
    ): List<ShanghaiCellTower> {
        if (!isInShanghai(latitude, longitude)) return emptyList()
        return towers.asSequence()
            .map { tower -> tower to distanceMeters(latitude, longitude, tower.latitude, tower.longitude) }
            .filter { (_, distance) -> distance <= maxDistanceMeters }
            .sortedBy { (_, distance) -> distance }
            .take(limit.coerceAtLeast(0))
            .map { (tower, _) -> tower }
            .toList()
    }

    companion object {
        private const val ASSET_ENTRY = "assets/shanghai_cells.csv"
        private const val EXTERNAL_CSV_PATH = "/sdcard/XposedFakeLocation/shanghai_cells.csv"
        private const val MCC_CHINA = 460
        private const val DEFAULT_LIMIT = 8
        private const val DEFAULT_MAX_DISTANCE_METERS = 3_000.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        private const val SHANGHAI_MIN_LAT = 30.67
        private const val SHANGHAI_MAX_LAT = 31.90
        private const val SHANGHAI_MIN_LON = 120.85
        private const val SHANGHAI_MAX_LON = 122.25

        fun empty(): ShanghaiCellTowerStore = ShanghaiCellTowerStore(emptyList())

        fun fromExternalOrModuleApk(moduleApkPath: String?): ShanghaiCellTowerStore {
            val externalStore = fromFile(EXTERNAL_CSV_PATH)
            if (!externalStore.isEmpty()) return externalStore
            return fromModuleApk(moduleApkPath)
        }

        fun fromFile(path: String): ShanghaiCellTowerStore {
            return runCatching {
                val file = File(path)
                if (!file.isFile || !file.canRead()) return empty()
                file.inputStream().use(::fromCsv)
            }.getOrElse { empty() }
        }

        fun fromModuleApk(moduleApkPath: String?): ShanghaiCellTowerStore {
            if (moduleApkPath.isNullOrBlank()) return empty()
            return runCatching {
                ZipFile(moduleApkPath).use { zipFile ->
                    val entry = zipFile.getEntry(ASSET_ENTRY)
                    if (entry == null) {
                        empty()
                    } else {
                        zipFile.getInputStream(entry).use(::fromCsv)
                    }
                }
            }.getOrElse { empty() }
        }

        fun fromCsv(inputStream: InputStream): ShanghaiCellTowerStore {
            val lines = inputStream.bufferedReader().useLines { it.toList() }
            if (lines.isEmpty()) return empty()

            val firstColumns = splitCsvLine(lines.first())
            val hasHeader = firstColumns.firstOrNull()?.equals("radio", ignoreCase = true) == true
            val header = if (hasHeader) firstColumns.map { it.trim().lowercase(Locale.US) } else DEFAULT_HEADER
            val dataLines = if (hasHeader) lines.drop(1) else lines

            val towers = dataLines.mapNotNull { line ->
                parseTower(splitCsvLine(line), header)
            }

            return ShanghaiCellTowerStore(towers)
        }

        private fun ShanghaiCellTowerStore.isEmpty(): Boolean {
            return towers.isEmpty()
        }

        private fun parseTower(columns: List<String>, header: List<String>): ShanghaiCellTower? {
            fun value(name: String): String? {
                val index = header.indexOf(name)
                return columns.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            }

            val radio = value("radio")?.uppercase(Locale.US) ?: return null
            val mcc = value("mcc")?.toIntOrNull() ?: return null
            val mnc = (value("net") ?: value("mnc"))?.toIntOrNull() ?: return null
            val lac = (value("area") ?: value("lac") ?: value("tac"))?.toIntOrNull() ?: return null
            val cid = (value("cell") ?: value("cid"))?.toIntOrNull() ?: return null
            val longitude = (value("lon") ?: value("longitude"))?.toDoubleOrNull() ?: return null
            val latitude = (value("lat") ?: value("latitude"))?.toDoubleOrNull() ?: return null

            if (mcc != MCC_CHINA || !isInShanghai(latitude, longitude)) return null

            return ShanghaiCellTower(
                radio = radio,
                mcc = mcc,
                mnc = mnc,
                lac = lac,
                cid = cid,
                pscOrPci = value("unit")?.toIntOrNull() ?: 0,
                longitude = longitude,
                latitude = latitude,
                rangeMeters = value("range")?.toIntOrNull(),
                averageSignal = (value("averagesignal") ?: value("averageSignal"))?.toIntOrNull()
            )
        }

        private fun splitCsvLine(line: String): List<String> {
            return line.split(',')
        }

        private fun isInShanghai(latitude: Double, longitude: Double): Boolean {
            return latitude in SHANGHAI_MIN_LAT..SHANGHAI_MAX_LAT &&
                longitude in SHANGHAI_MIN_LON..SHANGHAI_MAX_LON
        }

        private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val a = sin(dLat / 2).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2.0)
            return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        private val DEFAULT_HEADER = listOf(
            "radio",
            "mcc",
            "net",
            "area",
            "cell",
            "unit",
            "lon",
            "lat",
            "range",
            "samples",
            "changeable",
            "created",
            "updated",
            "averagesignal"
        )
    }
}
