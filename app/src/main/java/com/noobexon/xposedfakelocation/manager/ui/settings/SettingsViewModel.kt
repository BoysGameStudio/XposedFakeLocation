//SettingsViewModel.kt
package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.SYSTEM_HOOK_PACKAGES
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.control.ControlReceiver
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-shot messages emitted while toggling system-level hooks, consumed once by the settings UI
 * (e.g. shown as a snackbar or restart dialog). Delivered through a [kotlinx.coroutines.channels.Channel]
 * so events are never dropped or replayed.
 */
sealed interface SystemHooksEvent {
    /**
     * The scope change succeeded and the device must be rebooted for it to take effect (or be
     * undone).
     *
     * @property enabled `true` if hooks were just enabled, `false` if they were disabled.
     */
    data class RestartRequired(val enabled: Boolean) : SystemHooksEvent

    /** The Xposed module is not active, so the scope cannot be changed. */
    data object ModuleNotActive : SystemHooksEvent

    /**
     * The scope request was rejected or threw.
     *
     * @property message Human-readable failure reason from the Xposed service.
     */
    data class ScopeRequestFailed(val message: String) : SystemHooksEvent
}

private const val TAG = "SettingsViewModel"

/**
 * Backs the settings screen. Exposes every spoofing/preference value as a hot [StateFlow] for the
 * UI to observe and a matching setter to mutate it, persisting through [PreferencesRepository].
 *
 * Most preferences are wrapped in a [Preference] holder that updates optimistically and then
 * commits to disk. System-level hooks are special: they require an Xposed scope change that may
 * fail or need a reboot, so their result is surfaced as one-shot [SystemHooksEvent]s.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    /**
     * Holds a single preference as a hot [StateFlow]. [set] updates the value optimistically for
     * instant UI feedback and then persists it; the repository [flow] remains the source of truth
     * and re-emits the committed value.
     *
     * @param T the preference value type (e.g. [Boolean], [Double], [Float], [String]).
     * @param initialValue value emitted until the repository's first value arrives.
     * @param flow source-of-truth stream collected for the lifetime of the [SettingsViewModel].
     * @param save suspending persistence call invoked on every [set].
     */
    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val save: suspend (T) -> Unit
    ) {
        private val _state = MutableStateFlow(initialValue)

        /** The current value, observable by the UI. */
        val state: StateFlow<T> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                flow.collect { _state.value = it }
            }
        }

        /**
         * Applies [value] immediately to [state], then persists it asynchronously. Persistence
         * failures are logged (not surfaced) since the in-memory value already reflects the intent;
         * cancellation is rethrown to respect structured concurrency.
         */
        fun set(value: T) {
            _state.value = value
            viewModelScope.launch {
                try {
                    save(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist preference value: $value", e)
                }
            }
        }
    }

    // Accuracy
    private val _useAccuracy = Preference(
        DEFAULT_USE_ACCURACY,
        preferencesRepository.getUseAccuracyFlow(),
        preferencesRepository::saveUseAccuracy
    )

    /** Whether the spoofed location reports a custom horizontal accuracy ([accuracy]). */
    val useAccuracy: StateFlow<Boolean> = _useAccuracy.state

    private val _accuracy = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy
    )

    /** Horizontal accuracy reported with the spoofed location, in meters. */
    val accuracy: StateFlow<Double> = _accuracy.state

    // Altitude
    private val _useAltitude = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude
    )

    /** Whether the spoofed location reports a custom [altitude]. */
    val useAltitude: StateFlow<Boolean> = _useAltitude.state

    private val _altitude = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude
    )

    /** Altitude reported with the spoofed location, in meters above the WGS84 ellipsoid. */
    val altitude: StateFlow<Double> = _altitude.state

    // Randomize
    private val _useRandomize = Preference(
        DEFAULT_USE_RANDOMIZE,
        preferencesRepository.getUseRandomizeFlow(),
        preferencesRepository::saveUseRandomize
    )

    /** Whether each reported location is jittered within [randomizeRadius] of the chosen point. */
    val useRandomize: StateFlow<Boolean> = _useRandomize.state

    private val _randomizeRadius = Preference(
        DEFAULT_RANDOMIZE_RADIUS,
        preferencesRepository.getRandomizeRadiusFlow(),
        preferencesRepository::saveRandomizeRadius
    )

    /** Radius of the randomization circle around the chosen point, in meters. */
    val randomizeRadius: StateFlow<Double> = _randomizeRadius.state

    // Vertical Accuracy
    private val _useVerticalAccuracy = Preference(
        DEFAULT_USE_VERTICAL_ACCURACY,
        preferencesRepository.getUseVerticalAccuracyFlow(),
        preferencesRepository::saveUseVerticalAccuracy
    )

    /** Whether the spoofed location reports a custom [verticalAccuracy]. */
    val useVerticalAccuracy: StateFlow<Boolean> = _useVerticalAccuracy.state

    private val _verticalAccuracy = Preference(
        DEFAULT_VERTICAL_ACCURACY,
        preferencesRepository.getVerticalAccuracyFlow(),
        preferencesRepository::saveVerticalAccuracy
    )

    /** Vertical (altitude) accuracy reported with the spoofed location, in meters. */
    val verticalAccuracy: StateFlow<Float> = _verticalAccuracy.state

    // Mean Sea Level
    private val _useMeanSeaLevel = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL,
        preferencesRepository.getUseMeanSeaLevelFlow(),
        preferencesRepository::saveUseMeanSeaLevel
    )

    /** Whether the spoofed location reports a custom mean-sea-level altitude ([meanSeaLevel]). */
    val useMeanSeaLevel: StateFlow<Boolean> = _useMeanSeaLevel.state

    private val _meanSeaLevel = Preference(
        DEFAULT_MEAN_SEA_LEVEL,
        preferencesRepository.getMeanSeaLevelFlow(),
        preferencesRepository::saveMeanSeaLevel
    )

    /** Mean-sea-level altitude reported with the spoofed location, in meters. */
    val meanSeaLevel: StateFlow<Double> = _meanSeaLevel.state

    // Mean Sea Level Accuracy
    private val _useMeanSeaLevelAccuracy = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getUseMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveUseMeanSeaLevelAccuracy
    )

    /** Whether the spoofed location reports a custom [meanSeaLevelAccuracy]. */
    val useMeanSeaLevelAccuracy: StateFlow<Boolean> = _useMeanSeaLevelAccuracy.state

    private val _meanSeaLevelAccuracy = Preference(
        DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveMeanSeaLevelAccuracy
    )

    /** Accuracy of the reported mean-sea-level altitude, in meters. */
    val meanSeaLevelAccuracy: StateFlow<Float> = _meanSeaLevelAccuracy.state

    // Speed
    private val _useSpeed = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed
    )

    /** Whether the spoofed location reports a custom [speed]. */
    val useSpeed: StateFlow<Boolean> = _useSpeed.state

    private val _speed = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed
    )

    /** Speed reported with the spoofed location, in meters per second. */
    val speed: StateFlow<Float> = _speed.state

    // Speed Accuracy
    private val _useSpeedAccuracy = Preference(
        DEFAULT_USE_SPEED_ACCURACY,
        preferencesRepository.getUseSpeedAccuracyFlow(),
        preferencesRepository::saveUseSpeedAccuracy
    )

    /** Whether the spoofed location reports a custom [speedAccuracy]. */
    val useSpeedAccuracy: StateFlow<Boolean> = _useSpeedAccuracy.state

    private val _speedAccuracy = Preference(
        DEFAULT_SPEED_ACCURACY,
        preferencesRepository.getSpeedAccuracyFlow(),
        preferencesRepository::saveSpeedAccuracy
    )

    /** Accuracy of the reported speed, in meters per second. */
    val speedAccuracy: StateFlow<Float> = _speedAccuracy.state

    // Hide Fake Location Toast
    private val _hideFakeLocationToast = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast
    )

    /** Whether the per-fix toast shown by the hook when a fake location is served is suppressed. */
    val hideFakeLocationToast: StateFlow<Boolean> = _hideFakeLocationToast.state

    // External Broadcast Control
    private val _enableBroadcastControl = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl
    )

    /**
     * Whether the external broadcast [ControlReceiver] is enabled, allowing other apps/ADB to
     * control spoofing via broadcasts. Kept in sync with the receiver's manifest state by
     * [setEnableBroadcastControl].
     */
    val enableBroadcastControl: StateFlow<Boolean> = _enableBroadcastControl.state

    /**
     * Whether the system framework packages are in the module's hook scope. Mirrors the persisted
     * preference, which is only updated once a scope change actually succeeds (see
     * [setEnableSystemHooks]), so the switch never flips optimistically.
     */
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    private val _systemHooksEvents = Channel<SystemHooksEvent>(Channel.BUFFERED)

    /** Stream of one-shot [SystemHooksEvent]s produced by [setEnableSystemHooks]. */
    val systemHooksEvents: Flow<SystemHooksEvent> = _systemHooksEvents.receiveAsFlow()

    // Language
    private val _languageTag = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag
    )

    /** BCP-47 language tag of the selected UI language, or empty to follow the system locale. */
    val languageTag: StateFlow<String> = _languageTag.state

    // Setters for the preferences above. Each updates its StateFlow optimistically and persists the
    // value (see Preference.set); the parameter unit/semantics match the documented flow of the
    // same name. enableBroadcastControl and enableSystemHooks have dedicated setters below.
    fun setUseAccuracy(value: Boolean) = _useAccuracy.set(value)
    fun setAccuracy(value: Double) = _accuracy.set(value)
    fun setUseAltitude(value: Boolean) = _useAltitude.set(value)
    fun setAltitude(value: Double) = _altitude.set(value)
    fun setUseRandomize(value: Boolean) = _useRandomize.set(value)
    fun setRandomizeRadius(value: Double) = _randomizeRadius.set(value)
    fun setUseVerticalAccuracy(value: Boolean) = _useVerticalAccuracy.set(value)
    fun setVerticalAccuracy(value: Float) = _verticalAccuracy.set(value)
    fun setUseMeanSeaLevel(value: Boolean) = _useMeanSeaLevel.set(value)
    fun setMeanSeaLevel(value: Double) = _meanSeaLevel.set(value)
    fun setUseMeanSeaLevelAccuracy(value: Boolean) = _useMeanSeaLevelAccuracy.set(value)
    fun setMeanSeaLevelAccuracy(value: Float) = _meanSeaLevelAccuracy.set(value)
    fun setUseSpeed(value: Boolean) = _useSpeed.set(value)
    fun setSpeed(value: Float) = _speed.set(value)
    fun setUseSpeedAccuracy(value: Boolean) = _useSpeedAccuracy.set(value)
    fun setSpeedAccuracy(value: Float) = _speedAccuracy.set(value)
    fun setHideFakeLocationToast(value: Boolean) = _hideFakeLocationToast.set(value)
    fun setLanguageTag(value: String) = _languageTag.set(value)

    /**
     * Persists the broadcast-control toggle and enables/disables the [ControlReceiver] manifest
     * component in lockstep, so the stored flag and the receiver's exported state never diverge.
     *
     * @param value `true` to enable external broadcast control, `false` to disable it.
     */
    fun setEnableBroadcastControl(value: Boolean) {
        _enableBroadcastControl.set(value)
        val context = getApplication<Application>()
        val component = ComponentName(context, ControlReceiver::class.java)
        val newState = if (value) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Adds (or removes) the system framework packages ([SYSTEM_HOOK_PACKAGES]) to the module scope.
     * The persisted [enableSystemHooks] toggle is only updated once the scope change succeeds, after
     * which the user is prompted to reboot. Progress and failures are reported through
     * [systemHooksEvents]; if the module is inactive a [SystemHooksEvent.ModuleNotActive] is emitted
     * and no change is made.
     *
     * @param enabled `true` to request the system scope, `false` to remove it.
     */
    fun setEnableSystemHooks(enabled: Boolean) {
        val service = App.service
        if (service == null) {
            _systemHooksEvents.trySend(SystemHooksEvent.ModuleNotActive)
            return
        }

        if (enabled) {
            val callback = object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    viewModelScope.launch {
                        preferencesRepository.saveEnableSystemHooks(true)
                        _systemHooksEvents.trySend(SystemHooksEvent.RestartRequired(true))
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(message))
                }
            }
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { service.requestScope(SYSTEM_HOOK_PACKAGES, callback) }
                } catch (e: XposedService.ServiceException) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(e.message ?: e.toString()))
                }
            }
        } else {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { service.removeScope(SYSTEM_HOOK_PACKAGES) }
                    preferencesRepository.saveEnableSystemHooks(false)
                    _systemHooksEvents.trySend(SystemHooksEvent.RestartRequired(false))
                } catch (e: XposedService.ServiceException) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(e.message ?: e.toString()))
                }
            }
        }
    }
}
