package com.noobexon.xposedfakelocation.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object LocationUtil {
    private const val TAG = "[LocationUtil]"

    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null
    private fun log(message: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, message)

    var latitude: Double = 0.0
        private set
    var longitude: Double = 0.0
        private set
    var accuracy: Float = 0F
        private set
    var altitude: Double = 0.0
        private set
    var verticalAccuracy: Float = 0F
        private set
    var meanSeaLevel: Double = 0.0
        private set
    var meanSeaLevelAccuracy: Float = 0F
        private set
    var speed: Float = 0F
        private set
    var speedAccuracy: Float = 0F
        private set

    @Synchronized
    fun createFakeLocation(originalLocation: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        val fakeLocation = if (originalLocation == null) {
            Location(provider).apply {
                time = System.currentTimeMillis() - 300
            }
        } else {
            Location(originalLocation.provider).apply {
                time = originalLocation.time
                accuracy = originalLocation.accuracy
                bearing = originalLocation.bearing
                bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos
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

    private fun attemptHideMockProvider(fakeLocation: Location) {
        runCatching {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            log("invoked hidden API - setIsFromMockProvider: false)")
        }.onFailure { log("Not possible to mock - ${it.message}", priority = Log.ERROR) }
    }

    @Synchronized
    fun updateLocation() {
        runCatching {
            val location = PreferencesUtil.getLastClickedLocation() ?: run {
                log("Last clicked location is null")
                return
            }

            if (PreferencesUtil.getUseRandomize() == true) {
                val randomizationRadius = PreferencesUtil.getRandomizeRadius() ?: DEFAULT_RANDOMIZE_RADIUS
                val (randomLat, randomLon) = getRandomLocation(location.latitude, location.longitude, randomizationRadius)
                latitude = randomLat
                longitude = randomLon
            } else {
                latitude = location.latitude
                longitude = location.longitude
            }

            if (PreferencesUtil.getUseAccuracy() == true) {
                accuracy = (PreferencesUtil.getAccuracy() ?: DEFAULT_ACCURACY).toFloat()
            }

            if (PreferencesUtil.getUseAltitude() == true) {
                altitude = PreferencesUtil.getAltitude() ?: DEFAULT_ALTITUDE
            }

            if (PreferencesUtil.getUseVerticalAccuracy() == true) {
                verticalAccuracy = PreferencesUtil.getVerticalAccuracy() ?: DEFAULT_VERTICAL_ACCURACY
            }

            if (PreferencesUtil.getUseMeanSeaLevel() == true) {
                meanSeaLevel = PreferencesUtil.getMeanSeaLevel() ?: DEFAULT_MEAN_SEA_LEVEL
            }

            if (PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                meanSeaLevelAccuracy = PreferencesUtil.getMeanSeaLevelAccuracy() ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
            }

            if (PreferencesUtil.getUseSpeed() == true) {
                speed = PreferencesUtil.getSpeed() ?: DEFAULT_SPEED
            }

            if (PreferencesUtil.getUseSpeedAccuracy() == true) {
                speedAccuracy = PreferencesUtil.getSpeedAccuracy() ?: DEFAULT_SPEED_ACCURACY
            }
        }.onFailure { log("Error - ${it.message}", priority = Log.ERROR) }
    }

    // Calculates a random point within a circle around the fake location that has the radius set by by the user. Uses Haversine's formula.
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
