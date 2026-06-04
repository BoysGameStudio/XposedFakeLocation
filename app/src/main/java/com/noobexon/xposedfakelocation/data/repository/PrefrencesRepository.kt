// PreferencesRepository.kt
package com.noobexon.xposedfakelocation.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.noobexon.xposedfakelocation.data.*
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfile
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfileCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.manager.App
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private fun defaultRemotePrefs(): SharedPreferences? =
    runCatching { App.service?.getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull()

private fun defaultRemotePrefsState(): Flow<SharedPreferences?> =
    App.serviceState.map { service ->
        runCatching { service?.getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull()
    }

/**
 * Single-source-of-truth preferences store.
 *
 * Each setting lives in exactly one place:
 *  - Hook-shared settings (read by the Xposed module) live in the LSPosed remote
 *    preferences exposed through [App.service]. They are only available while the
 *    XposedService is bound; when it isn't, reads fall back to defaults and writes
 *    are dropped (the UI is gated behind a bound service anyway).
 *  - Manager-only settings (language, favorites, broadcast control) live in a local
 *    [SharedPreferences] file so they are always available, including at startup and
 *    when the module is disabled.
 *
 * Doubles are encoded as raw long bits because [SharedPreferences] has no putDouble,
 * keeping read/write symmetry with the hook-side PreferencesUtil.
 */
class PreferencesRepository internal constructor(
    private val localPrefs: SharedPreferences,
    private val remotePrefsProvider: () -> SharedPreferences?,
    private val remotePrefsState: Flow<SharedPreferences?>,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val buildFingerprintProvider: () -> String = { Build.FINGERPRINT }
) {
    private val tag = "PreferencesRepository"

    private val gson = Gson()

    constructor(context: Context) : this(
        localPrefs = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE),
        remotePrefsProvider = ::defaultRemotePrefs,
        remotePrefsState = defaultRemotePrefsState(),
        sdkIntProvider = { Build.VERSION.SDK_INT },
        buildFingerprintProvider = { Build.FINGERPRINT }
    )

    private fun remotePrefs(): SharedPreferences? =
        runCatching { remotePrefsProvider() }.getOrNull()

    // region Flow helpers

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> remoteFlow(key: String, default: T, read: (SharedPreferences) -> T): Flow<T> =
        remotePrefsState.flatMapLatest { prefs ->
            if (prefs == null) {
                flowOf(default)
            } else {
                prefsChangeFlow(prefs, key) { read(prefs) }
            }
        }

    private fun <T> localFlow(key: String, read: (SharedPreferences) -> T): Flow<T> =
        prefsChangeFlow(localPrefs, key) { read(localPrefs) }

    private fun <T> prefsChangeFlow(
        prefs: SharedPreferences,
        key: String,
        read: () -> T
    ): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey == key) trySend(read())
        }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // endregion

    // region Write helpers

    private inline fun editRemote(action: SharedPreferences.Editor.() -> Unit) {
        val prefs = remotePrefs()
        if (prefs == null) {
            logWarning("Remote preferences unavailable (service not bound); write skipped")
            return
        }
        prefs.edit(action = action)
    }

    private inline fun editRemoteIfAvailable(action: SharedPreferences.Editor.() -> Unit): Boolean {
        val prefs = remotePrefs() ?: return false
        return runCatching {
            prefs.edit(action = action)
            true
        }.getOrDefault(false)
    }

    private inline fun editLocal(action: SharedPreferences.Editor.() -> Unit) {
        localPrefs.edit(action = action)
    }

    private fun readRemoteDouble(key: String, default: Double): Double {
        val prefs = remotePrefs() ?: return default
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    // endregion

    // region Is Playing (remote)
    fun getIsPlayingFlow(): Flow<Boolean> = remoteFlow(KEY_IS_PLAYING, false) { it.getBoolean(KEY_IS_PLAYING, false) }
    suspend fun saveIsPlaying(isPlaying: Boolean) = editRemote { putBoolean(KEY_IS_PLAYING, isPlaying) }
    fun getIsPlaying(): Boolean = remotePrefs()?.getBoolean(KEY_IS_PLAYING, false) ?: false
    // endregion

    // region Last Clicked Location (remote)
    fun getLastClickedLocationFlow(): Flow<LastClickedLocation?> =
        remoteFlow<LastClickedLocation?>(KEY_LAST_CLICKED_LOCATION, null) {
            parseLastClickedLocation(it.getString(KEY_LAST_CLICKED_LOCATION, null))
        }

    suspend fun saveLastClickedLocation(latitude: Double, longitude: Double) {
        val json = gson.toJson(LastClickedLocation(latitude, longitude))
        editRemote { putString(KEY_LAST_CLICKED_LOCATION, json) }
    }

    fun getLastClickedLocation(): LastClickedLocation? =
        parseLastClickedLocation(remotePrefs()?.getString(KEY_LAST_CLICKED_LOCATION, null))

    suspend fun clearLastClickedLocation() {
        editRemote { remove(KEY_LAST_CLICKED_LOCATION) }
        saveIsPlaying(false)
        logDebug("Cleared 'LastClickedLocation' and set 'IsPlaying' to false")
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json == null) return null
        return try {
            gson.fromJson(json, LastClickedLocation::class.java)
        } catch (e: JsonSyntaxException) {
            logError("Error parsing LastClickedLocation: ${e.message}")
            null
        }
    }
    // endregion

    // region Signal Baseline Snapshot (remote)
    fun signalBaselineFlow(): Flow<SignalBaselineSnapshot?> =
        remoteFlow<SignalBaselineSnapshot?>(KEY_SIGNAL_BASELINE_SNAPSHOT, null) { prefs ->
            parseSignalBaseline(readSignalBaselineJson(prefs))
        }

    fun getSignalBaselineFlow(): Flow<SignalBaselineSnapshot?> = signalBaselineFlow()

    suspend fun saveSignalBaseline(snapshot: SignalBaselineSnapshot): Boolean {
        val json = SignalBaselineCodec.encodeToJson(snapshot) ?: return false
        return editRemoteIfAvailable {
            putString(KEY_SIGNAL_BASELINE_SNAPSHOT, json)
        }
    }

    fun getSignalBaseline(): SignalBaselineSnapshot? =
        parseSignalBaseline(readSignalBaselineJson(remotePrefs()))

    suspend fun clearSignalBaseline(): Boolean =
        editRemoteIfAvailable {
            remove(KEY_SIGNAL_BASELINE_SNAPSHOT)
        }

    private fun readSignalBaselineJson(prefs: SharedPreferences?): String? {
        return runCatching { prefs?.getString(KEY_SIGNAL_BASELINE_SNAPSHOT, null) }.getOrNull()
    }

    private fun parseSignalBaseline(json: String?): SignalBaselineSnapshot? {
        return runCatching {
            SignalBaselineCodec.parseOrNull(
                json = json,
                currentSdkInt = sdkIntProvider(),
                currentBuildFingerprint = buildFingerprintProvider()
            )
        }.getOrNull()
    }
    // endregion

    // region Use Accuracy / Accuracy (remote)
    fun getUseAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) { it.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) }
    suspend fun saveUseAccuracy(useAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_ACCURACY, useAccuracy) }
    fun getUseAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) ?: DEFAULT_USE_ACCURACY

    fun getAccuracyFlow(): Flow<Double> = remoteFlow(KEY_ACCURACY, DEFAULT_ACCURACY) { readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY) }
    suspend fun saveAccuracy(accuracy: Double) = editRemote { putLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(accuracy)) }
    fun getAccuracy(): Double = readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY)
    // endregion

    // region Use Altitude / Altitude (remote)
    fun getUseAltitudeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) { it.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) }
    suspend fun saveUseAltitude(useAltitude: Boolean) = editRemote { putBoolean(KEY_USE_ALTITUDE, useAltitude) }
    fun getUseAltitude(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) ?: DEFAULT_USE_ALTITUDE

    fun getAltitudeFlow(): Flow<Double> = remoteFlow(KEY_ALTITUDE, DEFAULT_ALTITUDE) { readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE) }
    suspend fun saveAltitude(altitude: Double) = editRemote { putLong(KEY_ALTITUDE, java.lang.Double.doubleToRawLongBits(altitude)) }
    fun getAltitude(): Double = readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE)
    // endregion

    // region Use Randomize / Randomize Radius (remote)
    fun getUseRandomizeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) { it.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) }
    suspend fun saveUseRandomize(randomize: Boolean) = editRemote { putBoolean(KEY_USE_RANDOMIZE, randomize) }
    fun getUseRandomize(): Boolean = remotePrefs()?.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) ?: DEFAULT_USE_RANDOMIZE

    fun getRandomizeRadiusFlow(): Flow<Double> = remoteFlow(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) { readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) }
    suspend fun saveRandomizeRadius(radius: Double) = editRemote { putLong(KEY_RANDOMIZE_RADIUS, java.lang.Double.doubleToRawLongBits(radius)) }
    fun getRandomizeRadius(): Double = readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS)
    // endregion

    // region Vertical Accuracy (remote)
    fun getUseVerticalAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) { it.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) }
    suspend fun saveUseVerticalAccuracy(useVerticalAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_VERTICAL_ACCURACY, useVerticalAccuracy) }
    fun getUseVerticalAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) ?: DEFAULT_USE_VERTICAL_ACCURACY

    fun getVerticalAccuracyFlow(): Flow<Float> = remoteFlow(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) { it.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) }
    suspend fun saveVerticalAccuracy(verticalAccuracy: Float) = editRemote { putFloat(KEY_VERTICAL_ACCURACY, verticalAccuracy) }
    fun getVerticalAccuracy(): Float = remotePrefs()?.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) ?: DEFAULT_VERTICAL_ACCURACY
    // endregion

    // region Mean Sea Level (remote)
    fun getUseMeanSeaLevelFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) }
    suspend fun saveUseMeanSeaLevel(useMeanSeaLevel: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL, useMeanSeaLevel) }
    fun getUseMeanSeaLevel(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) ?: DEFAULT_USE_MEAN_SEA_LEVEL

    fun getMeanSeaLevelFlow(): Flow<Double> = remoteFlow(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) { readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) }
    suspend fun saveMeanSeaLevel(meanSeaLevel: Double) = editRemote { putLong(KEY_MEAN_SEA_LEVEL, java.lang.Double.doubleToRawLongBits(meanSeaLevel)) }
    fun getMeanSeaLevel(): Double = readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL)

    fun getUseMeanSeaLevelAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveUseMeanSeaLevelAccuracy(useMeanSeaLevelAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, useMeanSeaLevelAccuracy) }
    fun getUseMeanSeaLevelAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY

    fun getMeanSeaLevelAccuracyFlow(): Flow<Float> = remoteFlow(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) { it.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveMeanSeaLevelAccuracy(meanSeaLevelAccuracy: Float) = editRemote { putFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, meanSeaLevelAccuracy) }
    fun getMeanSeaLevelAccuracy(): Float = remotePrefs()?.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    // endregion

    // region Speed (remote)
    fun getUseSpeedFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED, DEFAULT_USE_SPEED) { it.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) }
    suspend fun saveUseSpeed(useSpeed: Boolean) = editRemote { putBoolean(KEY_USE_SPEED, useSpeed) }
    fun getUseSpeed(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) ?: DEFAULT_USE_SPEED

    fun getSpeedFlow(): Flow<Float> = remoteFlow(KEY_SPEED, DEFAULT_SPEED) { it.getFloat(KEY_SPEED, DEFAULT_SPEED) }
    suspend fun saveSpeed(speed: Float) = editRemote { putFloat(KEY_SPEED, speed) }
    fun getSpeed(): Float = remotePrefs()?.getFloat(KEY_SPEED, DEFAULT_SPEED) ?: DEFAULT_SPEED

    fun getUseSpeedAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) { it.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) }
    suspend fun saveUseSpeedAccuracy(useSpeedAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_SPEED_ACCURACY, useSpeedAccuracy) }
    fun getUseSpeedAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) ?: DEFAULT_USE_SPEED_ACCURACY

    fun getSpeedAccuracyFlow(): Flow<Float> = remoteFlow(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) { it.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) }
    suspend fun saveSpeedAccuracy(speedAccuracy: Float) = editRemote { putFloat(KEY_SPEED_ACCURACY, speedAccuracy) }
    fun getSpeedAccuracy(): Float = remotePrefs()?.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) ?: DEFAULT_SPEED_ACCURACY
    // endregion

    // region Enable System-Level Hooks (remote)
    fun getEnableSystemHooksFlow(): Flow<Boolean> = remoteFlow(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) { it.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) }
    suspend fun saveEnableSystemHooks(enabled: Boolean) = editRemote { putBoolean(KEY_ENABLE_SYSTEM_HOOKS, enabled) }
    fun getEnableSystemHooks(): Boolean = remotePrefs()?.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) ?: DEFAULT_ENABLE_SYSTEM_HOOKS
    // endregion

    // region Hide Fake Location Toast (remote)
    fun getHideFakeLocationToastFlow(): Flow<Boolean> = remoteFlow(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) { it.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) }
    suspend fun saveHideFakeLocationToast(hideFakeLocationToast: Boolean) = editRemote { putBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, hideFakeLocationToast) }
    fun getHideFakeLocationToast(): Boolean = remotePrefs()?.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) ?: DEFAULT_HIDE_FAKE_LOCATION_TOAST
    // endregion

    // region Target Apps (remote)
    fun getTargetAppsFlow(): Flow<Set<String>> =
        remoteFlow(KEY_TARGET_APPS, emptySet()) { parseTargetApps(it.getString(KEY_TARGET_APPS, null)) }

    suspend fun saveTargetApps(packageNames: Set<String>) {
        val normalized = packageNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val json = gson.toJson(normalized)
        editRemote { putString(KEY_TARGET_APPS, json) }
    }

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (e: JsonSyntaxException) {
            logError("Error parsing target apps: ${e.message}")
            emptySet()
        }
    }
    // endregion

    // region Favorites (local)
    fun getFavoritesFlow(): Flow<List<FavoriteLocation>> =
        localFlow(KEY_FAVORITES) { parseFavorites(it.getString(KEY_FAVORITES, null)) }

    suspend fun addFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { add(favorite) }
        saveFavorites(updated)
        logDebug("Added Favorite: $favorite")
    }

    suspend fun removeFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { remove(favorite) }
        saveFavorites(updated)
        logDebug("Removed Favorite: $favorite")
    }

    fun getFavorites(): List<FavoriteLocation> = parseFavorites(localPrefs.getString(KEY_FAVORITES, null))

    private fun saveFavorites(favorites: List<FavoriteLocation>) {
        val json = gson.toJson(favorites)
        editLocal { putString(KEY_FAVORITES, json) }
    }

    private fun parseFavorites(json: String?): List<FavoriteLocation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteLocation>>() {}.type
            gson.fromJson(json, type)
        } catch (e: JsonSyntaxException) {
            logError("Error parsing Favorites: ${e.message}")
            emptyList()
        }
    }
    // endregion

    fun getSavedLocationProfilesFlow(): Flow<List<SavedLocationProfile>> =
        localFlow(KEY_SAVED_LOCATION_PROFILES) { parseSavedLocationProfiles(it.getString(KEY_SAVED_LOCATION_PROFILES, null)) }

    fun getSavedLocationProfiles(): List<SavedLocationProfile> =
        parseSavedLocationProfiles(localPrefs.getString(KEY_SAVED_LOCATION_PROFILES, null))

    suspend fun saveLocationProfile(profile: SavedLocationProfile): Boolean {
        val updated = (listOf(profile) + getSavedLocationProfiles().filterNot { it.id == profile.id })
            .sortedByDescending(SavedLocationProfile::savedAtMillis)
            .take(SavedLocationProfileCodec.MAX_PROFILE_COUNT)
        return saveSavedLocationProfiles(updated)
    }

    suspend fun removeSavedLocationProfile(id: String): Boolean {
        val updated = getSavedLocationProfiles().filterNot { it.id == id }
        return saveSavedLocationProfiles(updated)
    }

    suspend fun importSavedLocationProfilesJson(json: String): Int? {
        val result = SavedLocationProfileCodec.parseProfiles(
            json = json,
            currentSdkInt = sdkIntProvider(),
            currentBuildFingerprint = buildFingerprintProvider()
        )
        if (!result.isValid) return null

        val currentProfilesById = getSavedLocationProfiles().associateBy(SavedLocationProfile::id)
        val importedProfilesById = result.profiles.associateBy(SavedLocationProfile::id)
        val mergedProfiles = (currentProfilesById + importedProfilesById)
            .values
            .sortedByDescending(SavedLocationProfile::savedAtMillis)
            .take(SavedLocationProfileCodec.MAX_PROFILE_COUNT)

        return if (saveSavedLocationProfiles(mergedProfiles)) result.profiles.size else null
    }

    fun exportSavedLocationProfilesJson(): String? =
        SavedLocationProfileCodec.encodeProfiles(getSavedLocationProfiles())

    private fun saveSavedLocationProfiles(profiles: List<SavedLocationProfile>): Boolean {
        val json = SavedLocationProfileCodec.encodeProfiles(profiles) ?: return false
        editLocal { putString(KEY_SAVED_LOCATION_PROFILES, json) }
        return true
    }

    private fun parseSavedLocationProfiles(json: String?): List<SavedLocationProfile> {
        return SavedLocationProfileCodec.parseProfiles(
            json = json,
            currentSdkInt = sdkIntProvider(),
            currentBuildFingerprint = buildFingerprintProvider()
        ).profiles
    }

    // region Broadcast Control (local)
    fun getEnableBroadcastControlFlow(): Flow<Boolean> = localFlow(KEY_ENABLE_BROADCAST_CONTROL) { it.getBoolean(KEY_ENABLE_BROADCAST_CONTROL, DEFAULT_ENABLE_BROADCAST_CONTROL) }
    suspend fun saveEnableBroadcastControl(enable: Boolean) = editLocal { putBoolean(KEY_ENABLE_BROADCAST_CONTROL, enable) }
    // endregion

    // region Language (local; shared with LocaleController)
    fun getLanguageTagFlow(): Flow<String> = localFlow(KEY_LANGUAGE_TAG) { it.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG }
    suspend fun saveLanguageTag(languageTag: String) = editLocal { putString(KEY_LANGUAGE_TAG, languageTag) }
    // endregion

    private fun logWarning(message: String) {
        runCatching { Log.w(tag, message) }
    }

    private fun logError(message: String) {
        runCatching { Log.e(tag, message) }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(tag, message) }
    }
}
