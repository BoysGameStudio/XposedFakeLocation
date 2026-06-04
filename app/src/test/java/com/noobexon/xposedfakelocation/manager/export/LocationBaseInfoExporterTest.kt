package com.noobexon.xposedfakelocation.manager.export

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LocationBaseInfoExporterTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-04T12:34:56Z"), ZoneOffset.UTC)

    @Test
    fun export_writesRealLocationSnapshotAsJson() {
        val exporter = LocationBaseInfoExporter(clock = fixedClock)
        val snapshot = LocationBaseInfoExporter.RealLocationSnapshot(
            provider = "gps",
            latitude = 31.2304,
            longitude = 121.4737,
            timeMillis = 1_780_000_000_000L,
            elapsedRealtimeNanos = 987_654_321L,
            hasElapsedRealtimeUncertaintyNanos = true,
            elapsedRealtimeUncertaintyNanos = 12.5,
            hasAltitude = true,
            altitudeMeters = 8.75,
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
            mslAltitudeMeters = 7.25,
            hasMslAltitudeAccuracy = true,
            mslAltitudeAccuracyMeters = 0.75f,
            isMock = false,
            extras = mapOf(
                "satellites" to 14,
                "source" to "fused",
                "validated" to true
            ),
            extrasUnsupportedKeys = listOf("parcelablePayload")
        )

        val outputRoot = Files.createTempDirectory("location-base-info-export-success").toFile()
        try {
            val result = exporter.export(outputRoot, snapshot)

            require(result is LocationBaseInfoExporter.ExportResult.Success)

            val expectedFile = outputRoot.resolve("exports/current_location_base_info.json")
            assertEquals(expectedFile.absolutePath, result.file.absolutePath)
            assertTrue(expectedFile.exists())

            val json = JsonParser.parseString(expectedFile.readText()).asJsonObject
            assertEquals(1, json.get("schemaVersion").asInt)
            assertEquals("real_device_location", json.get("source").asString)
            assertEquals("2026-06-04T12:34:56Z", json.get("exportedAt").asString)
            assertFalse(json.has("selectedLatitude"))
            assertFalse(json.has("selectedLongitude"))
            assertFalse(json.has("towerCount"))
            assertFalse(json.has("towers"))

            val location = json.getAsJsonObject("location")
            assertEquals("gps", location.get("provider").asString)
            assertEquals(31.2304, location.get("latitude").asDouble, 0.0)
            assertEquals(121.4737, location.get("longitude").asDouble, 0.0)
            assertEquals(1_780_000_000_000L, location.get("timeMillis").asLong)
            assertEquals(987_654_321L, location.get("elapsedRealtimeNanos").asLong)
            assertTrue(location.get("hasElapsedRealtimeUncertaintyNanos").asBoolean)
            assertEquals(12.5, location.get("elapsedRealtimeUncertaintyNanos").asDouble, 0.0)
            assertTrue(location.get("hasAltitude").asBoolean)
            assertEquals(8.75, location.get("altitudeMeters").asDouble, 0.0)
            assertTrue(location.get("hasAccuracy").asBoolean)
            assertEquals(3.5f, location.get("accuracyMeters").asFloat, 0.0f)
            assertTrue(location.get("hasSpeed").asBoolean)
            assertEquals(1.25f, location.get("speedMetersPerSecond").asFloat, 0.0f)
            assertTrue(location.get("hasBearing").asBoolean)
            assertEquals(45.0f, location.get("bearingDegrees").asFloat, 0.0f)
            assertTrue(location.get("hasVerticalAccuracy").asBoolean)
            assertEquals(2.0f, location.get("verticalAccuracyMeters").asFloat, 0.0f)
            assertTrue(location.get("hasSpeedAccuracy").asBoolean)
            assertEquals(0.5f, location.get("speedAccuracyMetersPerSecond").asFloat, 0.0f)
            assertTrue(location.get("hasBearingAccuracy").asBoolean)
            assertEquals(1.5f, location.get("bearingAccuracyDegrees").asFloat, 0.0f)
            assertTrue(location.get("hasMslAltitude").asBoolean)
            assertEquals(7.25, location.get("mslAltitudeMeters").asDouble, 0.0)
            assertTrue(location.get("hasMslAltitudeAccuracy").asBoolean)
            assertEquals(0.75f, location.get("mslAltitudeAccuracyMeters").asFloat, 0.0f)
            assertFalse(location.get("isMock").asBoolean)

            val extras = location.getAsJsonObject("extras")
            assertEquals(14, extras.get("satellites").asInt)
            assertEquals("fused", extras.get("source").asString)
            assertTrue(extras.get("validated").asBoolean)
            assertEquals("parcelablePayload", location.getAsJsonArray("extrasUnsupportedKeys")[0].asString)
        } finally {
            outputRoot.deleteRecursively()
        }
    }

    @Test
    fun export_returnsNoRealLocationWhenSnapshotIsMissing() {
        val exporter = LocationBaseInfoExporter(clock = fixedClock)

        val outputRoot = Files.createTempDirectory("location-base-info-export-missing-location").toFile()
        try {
            val result = exporter.export(outputRoot, null)

            assertEquals(LocationBaseInfoExporter.ExportResult.NoRealLocation, result)
            assertFalse(outputRoot.resolve("exports/current_location_base_info.json").exists())
        } finally {
            outputRoot.deleteRecursively()
        }
    }
}
