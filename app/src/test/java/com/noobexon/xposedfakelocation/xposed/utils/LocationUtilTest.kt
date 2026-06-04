package com.noobexon.xposedfakelocation.xposed.utils

import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_LAST_CLICKED_LOCATION
import com.noobexon.xposedfakelocation.data.KEY_SIGNAL_BASELINE_SNAPSHOT
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.KEY_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationUtilTest {
    private val gson = Gson()

    @Before
    fun setUp() {
        resetPreferencesUtil()
        resetLocationUtil()
        LocationUtil.currentSdkIntProvider = { SignalBaselineTestFixtures.CURRENT_SDK }
        LocationUtil.currentBuildFingerprintProvider = { SignalBaselineTestFixtures.CURRENT_BUILD }
    }

    @After
    fun tearDown() {
        resetPreferencesUtil()
        resetLocationUtil()
    }

    @Test
    fun updateLocation_prefersValidBaselineLocationOverLastClickedLocation() {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.putBaseline(SignalBaselineTestFixtures.validBaseline())
        remotePrefs.putLastClickedLocation(LastClickedLocation(latitude = 48.8584, longitude = 2.2945))
        PreferencesUtil.init(remotePrefs)

        LocationUtil.updateLocation()

        assertEquals(37.4219983, LocationUtil.latitude, COORDINATE_DELTA)
        assertEquals(-122.084, LocationUtil.longitude, COORDINATE_DELTA)
        assertEquals(3.5f, LocationUtil.accuracy, FLOAT_DELTA)
        assertEquals(12.25, LocationUtil.altitude, COORDINATE_DELTA)
        assertEquals(2.0f, LocationUtil.verticalAccuracy, FLOAT_DELTA)
        assertEquals(11.75, LocationUtil.meanSeaLevel, COORDINATE_DELTA)
        assertEquals(0.75f, LocationUtil.meanSeaLevelAccuracy, FLOAT_DELTA)
        assertEquals(1.25f, LocationUtil.speed, FLOAT_DELTA)
        assertEquals(0.5f, LocationUtil.speedAccuracy, FLOAT_DELTA)
    }

    @Test
    fun updateLocation_fallsBackToLastClickedLocationWhenBaselineIsMissingOrInvalid() {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.edit()
            .putString(KEY_SIGNAL_BASELINE_SNAPSHOT, "{not-json")
            .putBoolean(KEY_USE_ACCURACY, true)
            .putLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(8.25))
            .apply()
        remotePrefs.putLastClickedLocation(LastClickedLocation(latitude = 48.8584, longitude = 2.2945))
        PreferencesUtil.init(remotePrefs)

        LocationUtil.updateLocation()

        assertEquals(48.8584, LocationUtil.latitude, COORDINATE_DELTA)
        assertEquals(2.2945, LocationUtil.longitude, COORDINATE_DELTA)
        assertEquals(8.25f, LocationUtil.accuracy, FLOAT_DELTA)
    }

    @Test
    fun baselineLocationReplayValues_refreshesTimestampsAndPreservesCapturedFields() {
        val baselineLocation = SignalBaselineTestFixtures.validBaseline().location

        val replayValues = LocationUtil.baselineLocationReplayValues(
            baselineLocation = baselineLocation,
            requestedProvider = "network",
            nowMillis = 1_900_000_000_000L,
            elapsedRealtimeNanos = 987_654_321L
        )

        assertNotNull(replayValues)
        requireNotNull(replayValues)
        assertEquals("gps", replayValues.provider)
        assertEquals(37.4219983, replayValues.latitude, COORDINATE_DELTA)
        assertEquals(-122.084, replayValues.longitude, COORDINATE_DELTA)
        assertEquals(1_900_000_000_000L, replayValues.timeMillis)
        assertEquals(987_654_321L, replayValues.elapsedRealtimeNanos)
        assertNotEquals(baselineLocation.timeMillis, replayValues.timeMillis)
        assertNotEquals(baselineLocation.elapsedRealtimeNanos, replayValues.elapsedRealtimeNanos)
        assertEquals(7.5, replayValues.elapsedRealtimeUncertaintyNanos!!, COORDINATE_DELTA)
        assertEquals(12.25, replayValues.altitudeMeters!!, COORDINATE_DELTA)
        assertEquals(3.5f, replayValues.accuracyMeters!!, FLOAT_DELTA)
        assertEquals(1.25f, replayValues.speedMetersPerSecond!!, FLOAT_DELTA)
        assertEquals(45.0f, replayValues.bearingDegrees!!, FLOAT_DELTA)
        assertEquals(2.0f, replayValues.verticalAccuracyMeters!!, FLOAT_DELTA)
        assertEquals(0.5f, replayValues.speedAccuracyMetersPerSecond!!, FLOAT_DELTA)
        assertEquals(1.5f, replayValues.bearingAccuracyDegrees!!, FLOAT_DELTA)
        assertEquals(11.75, replayValues.mslAltitudeMeters!!, COORDINATE_DELTA)
        assertEquals(0.75f, replayValues.mslAltitudeAccuracyMeters!!, FLOAT_DELTA)
        assertEquals(mapOf("source" to "fixture"), replayValues.extras)
    }

    @Test
    fun disabledAndNotTargetedPackageGatesRemainPassthrough() {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.edit()
            .putBoolean(KEY_IS_PLAYING, false)
            .putString(KEY_TARGET_APPS, gson.toJson(listOf(TARGET_PACKAGE, MANAGER_APP_PACKAGE_NAME)))
            .apply()
        PreferencesUtil.init(remotePrefs)

        assertEquals(false, PreferencesUtil.getIsPlaying())
        assertTrue(LocationUtil.shouldSpoofPackage(TARGET_PACKAGE))
        assertFalse(LocationUtil.shouldSpoofPackage("com.example.untargeted"))
        assertFalse(LocationUtil.shouldSpoofPackage(MANAGER_APP_PACKAGE_NAME))
    }

    @Test
    fun updateLocationWithoutBaselineOrLastClickedLocationIsSafeAndDoesNotReuseStaleState() {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.edit().putString(KEY_SIGNAL_BASELINE_SNAPSHOT, "{not-json").apply()
        PreferencesUtil.init(remotePrefs)
        LocationUtil.latitude = 48.8584
        LocationUtil.longitude = 2.2945
        LocationUtil.accuracy = 8.25f

        LocationUtil.updateLocation()

        assertEquals(0.0, LocationUtil.latitude, COORDINATE_DELTA)
        assertEquals(0.0, LocationUtil.longitude, COORDINATE_DELTA)
        assertEquals(0f, LocationUtil.accuracy, FLOAT_DELTA)
        assertNull(PreferencesUtil.getLastClickedLocation())
    }

    private fun FakeSharedPreferences.putBaseline(snapshot: SignalBaselineSnapshot) {
        edit().putString(
            KEY_SIGNAL_BASELINE_SNAPSHOT,
            requireNotNull(SignalBaselineCodec.encodeToJson(snapshot))
        ).apply()
    }

    private fun FakeSharedPreferences.putLastClickedLocation(lastClickedLocation: LastClickedLocation) {
        edit().putString(KEY_LAST_CLICKED_LOCATION, gson.toJson(lastClickedLocation)).apply()
    }

    private fun resetPreferencesUtil() {
        val preferencesField = PreferencesUtil::class.java.getDeclaredField("preferences")
        preferencesField.isAccessible = true
        preferencesField.set(PreferencesUtil, null)
    }

    private fun resetLocationUtil() {
        LocationUtil.logger = null
        LocationUtil.currentSdkIntProvider = { 0 }
        LocationUtil.currentBuildFingerprintProvider = { "" }
        LocationUtil.currentTimeMillisProvider = { 0L }
        LocationUtil.elapsedRealtimeNanosProvider = { 0L }
        LocationUtil.latitude = 0.0
        LocationUtil.longitude = 0.0
        LocationUtil.accuracy = 0F
        LocationUtil.altitude = 0.0
        LocationUtil.verticalAccuracy = 0F
        LocationUtil.meanSeaLevel = 0.0
        LocationUtil.meanSeaLevelAccuracy = 0F
        LocationUtil.speed = 0F
        LocationUtil.speedAccuracy = 0F
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        const val COORDINATE_DELTA = 0.0000001
        const val FLOAT_DELTA = 0.0001f
    }
}
