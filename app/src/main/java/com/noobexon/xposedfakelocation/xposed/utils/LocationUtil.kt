package com.noobexon.xposedfakelocation.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LocationBaselineSnapshot
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Singleton holding the current spoofed location state and utilities for building
 * fake [Location] objects.
 *
 * All mutable fields are updated exclusively by [updateLocation], which pulls the
 * latest values from [PreferencesUtil] on every call. External callers (hooks) read
 * the fields but cannot write them directly.
 */
object LocationUtil {
    private const val TAG = "[LocationUtil]"
    private const val MAX_REPLAY_EXTRAS = 32
    private const val MAX_EXTRA_KEY_LENGTH = 1_024

    /**
     * Optional logger wired in by [com.noobexon.xposedfakelocation.xposed.ModuleEntry].
     * When set, all [log] calls are routed through the libxposed logging channel.
     * Must be `@Volatile` because it is written from one thread and read from many.
     */
    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null
    private fun log(message: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, message)

    private const val DEBUG: Boolean = false

    @Volatile
    internal var currentSdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    @Volatile
    internal var currentBuildFingerprintProvider: () -> String = { Build.FINGERPRINT.orEmpty() }

    @Volatile
    internal var currentTimeMillisProvider: () -> Long = { System.currentTimeMillis() }

    @Volatile
    internal var elapsedRealtimeNanosProvider: () -> Long = { SystemClock.elapsedRealtimeNanos() }

    /** Current spoofed latitude in decimal degrees. Updated by [updateLocation]. */
    var latitude: Double = 0.0
    /** Current spoofed longitude in decimal degrees. Updated by [updateLocation]. */
    var longitude: Double = 0.0
    /** Current spoofed horizontal accuracy in metres. Zero means "not overridden". */
    var accuracy: Float = 0F
    /** Current spoofed altitude in metres above WGS-84. Zero means "not overridden". */
    var altitude: Double = 0.0
    /** Current spoofed vertical accuracy in metres. Zero means "not overridden". */
    var verticalAccuracy: Float = 0F
    /** Current spoofed MSL altitude in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevel: Double = 0.0
    /** Current spoofed MSL altitude accuracy in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevelAccuracy: Float = 0F
    /** Current spoofed ground speed in m/s. Zero means "not overridden". */
    var speed: Float = 0F
    /** Current spoofed speed accuracy in m/s. Zero means "not overridden". */
    var speedAccuracy: Float = 0F

    internal data class BaselineLocationReplayValues(
        val provider: String,
        val latitude: Double,
        val longitude: Double,
        val timeMillis: Long,
        val elapsedRealtimeNanos: Long,
        val elapsedRealtimeUncertaintyNanos: Double?,
        val altitudeMeters: Double?,
        val accuracyMeters: Float?,
        val speedMetersPerSecond: Float?,
        val bearingDegrees: Float?,
        val verticalAccuracyMeters: Float?,
        val speedAccuracyMetersPerSecond: Float?,
        val bearingAccuracyDegrees: Float?,
        val mslAltitudeMeters: Double?,
        val mslAltitudeAccuracyMeters: Float?,
        val extras: Map<String, Any?>
    )

    /**
     * Builds a [Location] object populated with the current spoofed field values.
     *
     * If a saved real-environment baseline exists, its captured coordinates and signal
     * metadata are replayed; otherwise the last clicked location is used. If
     * [originalLocation] is provided its metadata (time, bearing, elapsed realtime, etc.)
     * is preserved; otherwise a fresh [Location] is created with a slightly backdated timestamp
     * to satisfy recency checks in some apps.
     *
     * Only non-zero spoofed fields are applied, so unset optional fields fall back to whatever
     * the [originalLocation] carried. The mock-provider flag is cleared via [attemptHideMockProvider].
     *
     * This method is `@Synchronized` to prevent reading partially-updated fields if
     * [updateLocation] is called concurrently.
     *
     * @param originalLocation Optional real location whose metadata is copied into the result.
     * @param provider Location provider string written into the returned [Location].
     */
    @Synchronized
    fun createFakeLocation(originalLocation: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        getBaselineLocation()?.let { baselineLocation ->
            updateFromBaselineLocation(baselineLocation)
            return createFakeLocationFromBaseline(baselineLocation, provider)
        }

        updateFromLastClickedLocation()
        return createFakeLocationFromLastClicked(originalLocation, provider)
    }

    internal fun baselineLocationReplayValues(
        baselineLocation: LocationBaselineSnapshot,
        requestedProvider: String,
        nowMillis: Long = currentTimeMillisProvider(),
        elapsedRealtimeNanos: Long = elapsedRealtimeNanosProvider()
    ): BaselineLocationReplayValues? {
        if (!isValidCoordinates(baselineLocation.latitude, baselineLocation.longitude)) return null

        return BaselineLocationReplayValues(
            provider = providerOrDefault(baselineLocation.provider, requestedProvider),
            latitude = baselineLocation.latitude,
            longitude = baselineLocation.longitude,
            timeMillis = nowMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            elapsedRealtimeUncertaintyNanos = baselineLocation.elapsedRealtimeUncertaintyNanos
                .takeIf { baselineLocation.hasElapsedRealtimeUncertaintyNanos },
            altitudeMeters = baselineLocation.altitudeMeters.takeIf { baselineLocation.hasAltitude },
            accuracyMeters = baselineLocation.accuracyMeters.takeIf { baselineLocation.hasAccuracy },
            speedMetersPerSecond = baselineLocation.speedMetersPerSecond.takeIf { baselineLocation.hasSpeed },
            bearingDegrees = baselineLocation.bearingDegrees.takeIf { baselineLocation.hasBearing },
            verticalAccuracyMeters = baselineLocation.verticalAccuracyMeters.takeIf { baselineLocation.hasVerticalAccuracy },
            speedAccuracyMetersPerSecond = baselineLocation.speedAccuracyMetersPerSecond
                .takeIf { baselineLocation.hasSpeedAccuracy },
            bearingAccuracyDegrees = baselineLocation.bearingAccuracyDegrees
                .takeIf { baselineLocation.hasBearingAccuracy },
            mslAltitudeMeters = baselineLocation.mslAltitudeMeters.takeIf { baselineLocation.hasMslAltitude },
            mslAltitudeAccuracyMeters = baselineLocation.mslAltitudeAccuracyMeters
                .takeIf { baselineLocation.hasMslAltitudeAccuracy },
            extras = boundedExtras(baselineLocation.extras)
        )
    }

    private fun createFakeLocationFromBaseline(
        baselineLocation: LocationBaselineSnapshot,
        requestedProvider: String
    ): Location {
        val replayValues = baselineLocationReplayValues(baselineLocation, requestedProvider)
            ?: return createFakeLocationFromLastClicked(null, requestedProvider)

        val fakeLocation = Location(replayValues.provider).apply {
            latitude = replayValues.latitude
            longitude = replayValues.longitude
            time = replayValues.timeMillis
            elapsedRealtimeNanos = replayValues.elapsedRealtimeNanos
            replayValues.elapsedRealtimeUncertaintyNanos?.let { elapsedRealtimeUncertaintyNanos = it }
            replayValues.accuracyMeters?.let { accuracy = it }
            replayValues.altitudeMeters?.let { altitude = it }
            replayValues.speedMetersPerSecond?.let { speed = it }
            replayValues.bearingDegrees?.let { bearing = it }
            replayValues.verticalAccuracyMeters?.let { verticalAccuracyMeters = it }
            replayValues.speedAccuracyMetersPerSecond?.let { speedAccuracyMetersPerSecond = it }
            replayValues.bearingAccuracyDegrees?.let { bearingAccuracyDegrees = it }
            replayValues.extras.toBundleOrNull()?.let { extras = it }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                replayValues.mslAltitudeMeters?.let { mslAltitudeMeters = it }
                replayValues.mslAltitudeAccuracyMeters?.let { mslAltitudeAccuracyMeters = it }
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    private fun createFakeLocationFromLastClicked(
        originalLocation: Location? = null,
        provider: String = LocationManager.GPS_PROVIDER
    ): Location {
        val fakeLocation = if (originalLocation == null) {
            Location(provider).apply {
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
        } else {
            Location(originalLocation.provider).apply {
                time = System.currentTimeMillis()
                accuracy = originalLocation.accuracy
                bearing = originalLocation.bearing
                bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                verticalAccuracyMeters = originalLocation.verticalAccuracyMeters
            }
        }

        fakeLocation.latitude = latitude
        fakeLocation.longitude = longitude

        if (accuracy != 0F) {
            fakeLocation.accuracy = accuracy
        }

        if (altitude != 0.0) {
            fakeLocation.altitude = altitude
        }

        if (verticalAccuracy != 0F) {
            fakeLocation.verticalAccuracyMeters = verticalAccuracy
        }

        if (speed != 0F) {
            fakeLocation.speed = speed
        }

        if (speedAccuracy != 0F) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (meanSeaLevel != 0.0) {
                fakeLocation.mslAltitudeMeters = meanSeaLevel
            }

            if (meanSeaLevelAccuracy != 0F) {
                fakeLocation.mslAltitudeAccuracyMeters = meanSeaLevelAccuracy
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    /**
     * Name-based scope attribution for the system-level hooks: a package is spoofed only when it is
     * one of the manager-selected target apps (mirrored into the remote `target_apps` preference).
     * The manager app itself is never spoofed.
     */
    fun shouldSpoofPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == MANAGER_APP_PACKAGE_NAME) return false
        return LocationSpoofPolicy.shouldSpoof(
            packageNames = setOf(packageName),
            targetPackages = PreferencesUtil.getTargetApps(),
            proxyPackages = PreferencesUtil.getLocationProxyPackages()
        )
    }

    /**
     * Reads the latest spoofed location settings from [PreferencesUtil] and updates all
     * mutable fields on this object.
     *
     * Coordinates are taken from a saved real-environment baseline when one is available and
     * valid, otherwise directly from the last clicked location or randomized within a
     * user-configured radius using the Haversine formula. Optional fields (accuracy, altitude,
     * speed, etc.) are only updated when their corresponding "use" flag is enabled in preferences.
     *
     * This method is `@Synchronized` to guarantee that [createFakeLocation] always sees a
     * consistent snapshot even when called from a different thread.
     */
    @Synchronized
    fun updateLocation() {
        try {
            getBaselineLocation()?.let { baselineLocation ->
                updateFromBaselineLocation(baselineLocation)
                if (DEBUG) log("Updated fake location values from saved baseline location.")
                return
            }

            updateFromLastClickedLocation()
        } catch (e: Exception) {
            resetLocationValues()
            log("Error - ${e.message}", priority = Log.ERROR)
        }
    }

    private fun getBaselineLocation(): LocationBaselineSnapshot? {
        return PreferencesUtil.getSignalBaseline(
            currentSdkInt = currentSdkIntProvider(),
            currentBuildFingerprint = currentBuildFingerprintProvider()
        )?.location?.takeIf(::isValidBaselineLocation)
    }

    private fun isValidBaselineLocation(baselineLocation: LocationBaselineSnapshot): Boolean {
        return isValidCoordinates(baselineLocation.latitude, baselineLocation.longitude)
    }

    private fun updateFromBaselineLocation(baselineLocation: LocationBaselineSnapshot) {
        resetLocationValues()
        latitude = baselineLocation.latitude
        longitude = baselineLocation.longitude
        accuracy = baselineLocation.accuracyMeters.takeIf { baselineLocation.hasAccuracy } ?: 0F
        altitude = baselineLocation.altitudeMeters.takeIf { baselineLocation.hasAltitude } ?: 0.0
        verticalAccuracy = baselineLocation.verticalAccuracyMeters.takeIf { baselineLocation.hasVerticalAccuracy } ?: 0F
        meanSeaLevel = baselineLocation.mslAltitudeMeters.takeIf { baselineLocation.hasMslAltitude } ?: 0.0
        meanSeaLevelAccuracy = baselineLocation.mslAltitudeAccuracyMeters
            .takeIf { baselineLocation.hasMslAltitudeAccuracy } ?: 0F
        speed = baselineLocation.speedMetersPerSecond.takeIf { baselineLocation.hasSpeed } ?: 0F
        speedAccuracy = baselineLocation.speedAccuracyMetersPerSecond.takeIf { baselineLocation.hasSpeedAccuracy } ?: 0F
    }

    private fun updateFromLastClickedLocation() {
        val lastClickedLocation = PreferencesUtil.getLastClickedLocation()
        if (lastClickedLocation == null || !isValidLastClickedLocation(lastClickedLocation)) {
            resetLocationValues()
            log("No valid last clicked location is available")
            return
        }

        resetLocationValues()
        if (PreferencesUtil.getUseRandomize() == true) {
            val randomizationRadius = PreferencesUtil.getRandomizeRadius() ?: DEFAULT_RANDOMIZE_RADIUS
            val randomLocation = getRandomLocation(
                lastClickedLocation.latitude,
                lastClickedLocation.longitude,
                randomizationRadius
            )
            latitude = randomLocation.first
            longitude = randomLocation.second
        } else {
            latitude = lastClickedLocation.latitude
            longitude = lastClickedLocation.longitude
        }

        if (PreferencesUtil.getUseAccuracy() == true) {
            accuracy = (PreferencesUtil.getAccuracy() ?: DEFAULT_ACCURACY).toFloat()
        }

        if (PreferencesUtil.getUseAltitude() == true) {
            altitude = PreferencesUtil.getAltitude() ?: DEFAULT_ALTITUDE
        }

        if (PreferencesUtil.getUseVerticalAccuracy() == true) {
            verticalAccuracy = PreferencesUtil.getVerticalAccuracy()?.toFloat() ?: DEFAULT_VERTICAL_ACCURACY
        }

        if (PreferencesUtil.getUseMeanSeaLevel() == true) {
            meanSeaLevel = PreferencesUtil.getMeanSeaLevel()?.toDouble() ?: DEFAULT_MEAN_SEA_LEVEL
        }

        if (PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
            meanSeaLevelAccuracy = PreferencesUtil.getMeanSeaLevelAccuracy()?.toFloat() ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
        }

        if (PreferencesUtil.getUseSpeed() == true) {
            speed = PreferencesUtil.getSpeed()?.toFloat() ?: DEFAULT_SPEED
        }

        if (PreferencesUtil.getUseSpeedAccuracy() == true) {
            speedAccuracy = PreferencesUtil.getSpeedAccuracy()?.toFloat() ?: DEFAULT_SPEED_ACCURACY
        }

        if (DEBUG) log("Updated fake location values from last clicked location.")
    }

    private fun isValidLastClickedLocation(lastClickedLocation: LastClickedLocation): Boolean {
        return isValidCoordinates(lastClickedLocation.latitude, lastClickedLocation.longitude)
    }

    private fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        return latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0
    }

    private fun resetLocationValues() {
        latitude = 0.0
        longitude = 0.0
        accuracy = 0F
        altitude = 0.0
        verticalAccuracy = 0F
        meanSeaLevel = 0.0
        meanSeaLevelAccuracy = 0F
        speed = 0F
        speedAccuracy = 0F
    }

    private fun providerOrDefault(provider: String?, requestedProvider: String): String {
        return provider?.takeIf { it.isNotBlank() }
            ?: requestedProvider.takeIf { it.isNotBlank() }
            ?: LocationManager.GPS_PROVIDER
    }

    private fun boundedExtras(extras: Map<String, Any?>): Map<String, Any?> {
        return extras.asSequence()
            .filter { (key, value) -> key.length <= MAX_EXTRA_KEY_LENGTH && isSupportedExtraValue(value) }
            .take(MAX_REPLAY_EXTRAS)
            .associate { (key, value) -> key to value }
    }

    private fun isSupportedExtraValue(value: Any?): Boolean {
        return value == null || value is String || value is Number || value is Boolean
    }

    private fun Map<String, Any?>.toBundleOrNull(): Bundle? {
        if (isEmpty()) return null
        val bundle = Bundle()
        forEach { (key, value) ->
            when (value) {
                null -> bundle.putString(key, null)
                is String -> bundle.putString(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Byte -> bundle.putByte(key, value)
                is Short -> bundle.putShort(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putFloat(key, value)
                is Double -> bundle.putDouble(key, value)
                is Number -> bundle.putDouble(key, value.toDouble())
            }
        }
        return bundle.takeIf { it.size() > 0 }
    }

    /**
     * Calculates a uniformly distributed random point within a circle of [radiusInMeters]
     * centred at ([lat], [lon]) using the Haversine formula.
     *
     * @return A [Pair] of (latitude, longitude) in decimal degrees, clamped/normalised
     *         to valid WGS-84 ranges.
     */
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        val rand1 = Random.nextDouble()
        val rand2 = Random.nextDouble()

        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }

    /**
     * Attempts to clear the mock-provider flag on [fakeLocation] via the hidden API
     * `Location.setIsFromMockProvider(false)`, bypassed using [HiddenApiBypass].
     *
     * Failure is logged but silently swallowed — some ROM variants or future API levels
     * may block this call, in which case spoofing still works but the mock flag remains set.
     */
    private fun attemptHideMockProvider(fakeLocation: Location) {
        runCatching {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            log("invoked hidden API - setIsFromMockProvider: false)")
        }.onFailure { log("Not possible to mock - ${it.message}", priority = Log.ERROR) }
    }

    /**
     * Logs all current spoofed location values. Not called in production, but useful for debugging.
     */
    @Suppress("unused")
    private fun logCurrentValues() {
        log("Updated fake location values to:")
        log("\tCoordinates: (latitude = $latitude, longitude = $longitude)")
        log("\tAccuracy: $accuracy")
        log("\tAltitude: $altitude")
        log("\tVertical Accuracy: $verticalAccuracy")
        log("\tMean Sea Level: $meanSeaLevel")
        log("\tMean Sea Level Accuracy: $meanSeaLevelAccuracy")
        log("\tSpeed: $speed")
        log("\tSpeed Accuracy: $speedAccuracy")
    }
}