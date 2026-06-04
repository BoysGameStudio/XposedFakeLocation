package com.noobexon.xposedfakelocation.manager.export

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Clock
import java.time.Instant

class LocationBaseInfoExporter(
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) {
    sealed interface ExportResult {
        data class Success(
            val file: File,
            @Deprecated("Compatibility for the old selected-marker export UI; real-location exports do not include towers.")
            val towerCount: Int = 0
        ) : ExportResult

        @Deprecated("Use NoRealLocation for real-device export failures.")
        data object NoSelectedLocation : ExportResult

        data object NoRealLocation : ExportResult

        data object MissingLocationPermission : ExportResult

        data class WriteFailure(val file: File?, val cause: Throwable) : ExportResult
    }

    data class RealLocationSnapshot(
        val provider: String?,
        val latitude: Double,
        val longitude: Double,
        val timeMillis: Long,
        val elapsedRealtimeNanos: Long,
        val hasElapsedRealtimeUncertaintyNanos: Boolean,
        val elapsedRealtimeUncertaintyNanos: Double?,
        val hasAltitude: Boolean,
        val altitudeMeters: Double?,
        val hasAccuracy: Boolean,
        val accuracyMeters: Float?,
        val hasSpeed: Boolean,
        val speedMetersPerSecond: Float?,
        val hasBearing: Boolean,
        val bearingDegrees: Float?,
        val hasVerticalAccuracy: Boolean,
        val verticalAccuracyMeters: Float?,
        val hasSpeedAccuracy: Boolean,
        val speedAccuracyMetersPerSecond: Float?,
        val hasBearingAccuracy: Boolean,
        val bearingAccuracyDegrees: Float?,
        val hasMslAltitude: Boolean,
        val mslAltitudeMeters: Double?,
        val hasMslAltitudeAccuracy: Boolean,
        val mslAltitudeAccuracyMeters: Float?,
        val isMock: Boolean,
        val extras: Map<String, Any?>,
        val extrasUnsupportedKeys: List<String>
    )

    fun export(
        outputRoot: File,
        location: RealLocationSnapshot?
    ): ExportResult {
        if (location == null) {
            return ExportResult.NoRealLocation
        }

        val outputFile = outputFile(outputRoot)
        val payload = ExportPayload(
            exportedAt = Instant.now(clock).toString(),
            location = location
        )

        return runCatching {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(gson.toJson(payload))
            ExportResult.Success(outputFile)
        }.getOrElse { throwable ->
            ExportResult.WriteFailure(outputFile, throwable)
        }
    }

    fun exportToAppSpecificExternalStorage(
        context: Context,
        location: RealLocationSnapshot?
    ): ExportResult {
        val outputRoot = context.getExternalFilesDir(null)
            ?: return ExportResult.WriteFailure(null, IllegalStateException("External files directory is unavailable"))
        return export(outputRoot, location)
    }

    private fun outputFile(outputRoot: File): File {
        return File(File(outputRoot, OUTPUT_DIRECTORY_NAME), OUTPUT_FILE_NAME)
    }

    private data class ExportPayload(
        val schemaVersion: Int = SCHEMA_VERSION,
        val source: String = SOURCE,
        val exportedAt: String,
        val location: RealLocationSnapshot
    )

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val SOURCE = "real_device_location"
        private const val OUTPUT_DIRECTORY_NAME = "exports"
        private const val OUTPUT_FILE_NAME = "current_location_base_info.json"
    }
}
