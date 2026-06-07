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
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
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
 * One-shot messages emitted while toggling system-level hooks, consumed exactly once by the
 * settings UI (shown as a snackbar or restart dialog). Delivered through a
 * [kotlinx.coroutines.channels.Channel] so events are never dropped or replayed across
 * configuration changes.
 */
sealed interface SystemHooksEvent {
    /**
     * The scope change succeeded and the device must be rebooted for it to take effect (or be
     * undone). The UI should show a non-dismissible informational dialog.
     *
     * @property enabled `true` if hooks were just enabled, `false` if they were just disabled.
     */
    data class RestartRequired(val enabled: Boolean) : SystemHooksEvent

    /**
     * The Xposed module is not currently active (no [App.service] connection), so the scope cannot
     * be changed. The UI should inform the user that LSPosed must be active.
     */
    data object ModuleNotActive : SystemHooksEvent

    /**
     * The scope request was rejected by the service or threw an unexpected exception. The UI should
     * surface [message] so the user knows why the toggle did not apply.
     *
     * @property message Human-readable failure reason returned by the Xposed service.
     */
    data class ScopeRequestFailed(val message: String) : SystemHooksEvent
}

private const val TAG = "SettingsViewModel"

/**
 * Backs the settings screen. Exposes every spoofing and app preference as a hot [StateFlow] for the
 * UI to observe, with a matching setter that updates the flow optimistically and persists through
 * [PreferencesRepository].
 *
 * All standard preferences are managed by the private [Preference] holder. System-level hooks are
 * the exception: they require a live Xposed service call that may fail or need a reboot, so their
 * outcome is surfaced as one-shot [SystemHooksEvent]s rather than an optimistic state update.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    /**
     * Holds a single preference value as a hot [StateFlow] and wires it to disk persistence.
     *
     * On creation the [flow] is collected for the ViewModel's lifetime so [state] always reflects
     * the repository's current value. [set] writes to [state] immediately (optimistic update) and
     * then fires a background coroutine to call [save], ensuring instant UI feedback without
     * blocking the main thread.
     *
     * @param T The preference value type (e.g. [Boolean], [Double], [Float], [String]).
     * @param initialValue Value emitted by [state] until the repository's first emission arrives.
     * @param flow Source-of-truth stream; its emissions overwrite [state].
     * @param save Suspending function that persists a new value to the repository.
     */
    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val save: suspend (T) -> Unit
    ) {
        private val _state = MutableStateFlow(initialValue)

        /**
         * The current preference value. Updated immediately by [set] for optimistic feedback, then
         * again when the repository confirms the write via [flow].
         */
        val state: StateFlow<T> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                flow.collect { _state.value = it }
            }
        }

        /**
         * Applies [value] to [state] immediately, then persists it asynchronously. Persistence
         * failures are logged but not rethrown — the in-memory state already reflects intent and
         * the user sees the correct value. [CancellationException] is rethrown to honour structured
         * concurrency.
         *
         * @param value The new value to apply and persist.
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

    // ---- Spoofing preferences ----------------------------------------------------------------

    private val _useAccuracy = Preference(
        DEFAULT_USE_ACCURACY,
        preferencesRepository.getUseAccuracyFlow(),
        preferencesRepository::saveUseAccuracy
    )

    /** Whether the spoofed location reports a custom horizontal accuracy radius ([accuracy]). */
    val useAccuracy: StateFlow<Boolean> = _useAccuracy.state

    private val _accuracy = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy
    )

    /** Horizontal accuracy radius reported with the spoofed location, in metres. */
    val accuracy: StateFlow<Double> = _accuracy.state

    private val _useAltitude = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude
    )

    /** Whether the spoofed location reports a custom ellipsoidal [altitude]. */
    val useAltitude: StateFlow<Boolean> = _useAltitude.state

    private val _altitude = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude
    )

    /** Altitude above the WGS84 ellipsoid reported with the spoofed location, in metres. */
    val altitude: StateFlow<Double> = _altitude.state

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

    /** Radius of the per-fix randomisation circle around the chosen point, in metres. */
    val randomizeRadius: StateFlow<Double> = _randomizeRadius.state

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

    /** Vertical (altitude) accuracy reported with the spoofed location, in metres. */
    val verticalAccuracy: StateFlow<Float> = _verticalAccuracy.state

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

    /** Mean-sea-level (MSL) altitude reported with the spoofed location, in metres. */
    val meanSeaLevel: StateFlow<Double> = _meanSeaLevel.state

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

    /** Accuracy of the reported mean-sea-level altitude, in metres. */
    val meanSeaLevelAccuracy: StateFlow<Float> = _meanSeaLevelAccuracy.state

    private val _useSpeed = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed
    )

    /** Whether the spoofed location reports a custom ground [speed]. */
    val useSpeed: StateFlow<Boolean> = _useSpeed.state

    private val _speed = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed
    )

    /** Ground speed reported with the spoofed location, in metres per second. */
    val speed: StateFlow<Float> = _speed.state

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

    /** Accuracy of the reported ground speed, in metres per second. */
    val speedAccuracy: StateFlow<Float> = _speedAccuracy.state

    // ---- Behaviour preferences ---------------------------------------------------------------

    private val _hideFakeLocationToast = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast
    )

    /**
     * Whether the per-fix toast shown by the Xposed hook when a fake location is served is
     * suppressed. Useful to reduce visual noise when spoofing continuously in the background.
     */
    val hideFakeLocationToast: StateFlow<Boolean> = _hideFakeLocationToast.state

    // ---- External control --------------------------------------------------------------------

    private val _enableBroadcastControl = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl
    )

    /**
     * Whether the external broadcast [ControlReceiver] is enabled, allowing other apps or ADB to
     * start/stop spoofing via broadcasts. Kept in sync with the receiver's manifest component state
     * by [setEnableBroadcastControl] so the stored flag and the exported state never diverge.
     */
    val enableBroadcastControl: StateFlow<Boolean> = _enableBroadcastControl.state

    // ---- System hooks ------------------------------------------------------------------------

    /**
     * Whether the system framework packages ([SYSTEM_HOOK_PACKAGES]) are in the module's hook
     * scope. Unlike other preferences, this is a read-only view backed directly by the repository
     * — it is only updated once a scope change actually succeeds (see [setEnableSystemHooks]),
     * so the switch never flips optimistically on a failed service call.
     */
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    private val _systemHooksEvents = Channel<SystemHooksEvent>(Channel.BUFFERED)

    /**
     * Stream of one-shot [SystemHooksEvent]s produced by [setEnableSystemHooks]. Collect this
     * inside `repeatOnLifecycle(STARTED)` to avoid consuming events while the screen is in the
     * background.
     */
    val systemHooksEvents: Flow<SystemHooksEvent> = _systemHooksEvents.receiveAsFlow()

    // ---- App preferences ---------------------------------------------------------------------

    private val _languageTag = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag
    )

    /**
     * BCP-47 language tag of the currently selected UI language (e.g. `"en"`, `"zh"`), or an
     * empty string to follow the device's system locale.
     */
    val languageTag: StateFlow<String> = _languageTag.state

    // ---- Setters -----------------------------------------------------------------------------

    /**
     * Enables or disables custom horizontal accuracy reporting.
     * @param value `true` to include [accuracy] in each spoofed fix.
     */
    fun setUseAccuracy(value: Boolean) = _useAccuracy.set(value)

    /**
     * Sets the horizontal accuracy radius included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [useAccuracy] is `true`.
     */
    fun setAccuracy(value: Double) = _accuracy.set(value)

    /**
     * Enables or disables custom ellipsoidal altitude reporting.
     * @param value `true` to include [altitude] in each spoofed fix.
     */
    fun setUseAltitude(value: Boolean) = _useAltitude.set(value)

    /**
     * Sets the ellipsoidal altitude included in each spoofed fix.
     * @param value Altitude above the WGS84 ellipsoid in metres. Only applied when [useAltitude] is `true`.
     */
    fun setAltitude(value: Double) = _altitude.set(value)

    /**
     * Enables or disables per-fix randomisation around the chosen point.
     * @param value `true` to jitter each reported location within [randomizeRadius].
     */
    fun setUseRandomize(value: Boolean) = _useRandomize.set(value)

    /**
     * Sets the radius of the randomisation circle around the chosen point.
     * @param value Radius in metres. Only applied when [useRandomize] is `true`.
     */
    fun setRandomizeRadius(value: Double) = _randomizeRadius.set(value)

    /**
     * Enables or disables custom vertical accuracy reporting.
     * @param value `true` to include [verticalAccuracy] in each spoofed fix.
     */
    fun setUseVerticalAccuracy(value: Boolean) = _useVerticalAccuracy.set(value)

    /**
     * Sets the vertical accuracy included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [useVerticalAccuracy] is `true`.
     */
    fun setVerticalAccuracy(value: Float) = _verticalAccuracy.set(value)

    /**
     * Enables or disables custom mean-sea-level altitude reporting.
     * @param value `true` to include [meanSeaLevel] in each spoofed fix.
     */
    fun setUseMeanSeaLevel(value: Boolean) = _useMeanSeaLevel.set(value)

    /**
     * Sets the mean-sea-level altitude included in each spoofed fix.
     * @param value MSL altitude in metres. Only applied when [useMeanSeaLevel] is `true`.
     */
    fun setMeanSeaLevel(value: Double) = _meanSeaLevel.set(value)

    /**
     * Enables or disables custom MSL accuracy reporting.
     * @param value `true` to include [meanSeaLevelAccuracy] in each spoofed fix.
     */
    fun setUseMeanSeaLevelAccuracy(value: Boolean) = _useMeanSeaLevelAccuracy.set(value)

    /**
     * Sets the MSL accuracy included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [useMeanSeaLevelAccuracy] is `true`.
     */
    fun setMeanSeaLevelAccuracy(value: Float) = _meanSeaLevelAccuracy.set(value)

    /**
     * Enables or disables custom ground speed reporting.
     * @param value `true` to include [speed] in each spoofed fix.
     */
    fun setUseSpeed(value: Boolean) = _useSpeed.set(value)

    /**
     * Sets the ground speed included in each spoofed fix.
     * @param value Speed in metres per second. Only applied when [useSpeed] is `true`.
     */
    fun setSpeed(value: Float) = _speed.set(value)

    /**
     * Enables or disables custom speed accuracy reporting.
     * @param value `true` to include [speedAccuracy] in each spoofed fix.
     */
    fun setUseSpeedAccuracy(value: Boolean) = _useSpeedAccuracy.set(value)

    /**
     * Sets the speed accuracy included in each spoofed fix.
     * @param value Accuracy in metres per second. Only applied when [useSpeedAccuracy] is `true`.
     */
    fun setSpeedAccuracy(value: Float) = _speedAccuracy.set(value)

    /**
     * Suppresses or restores the per-fix toast shown by the Xposed hook.
     * @param value `true` to hide the toast; `false` to show it.
     */
    fun setHideFakeLocationToast(value: Boolean) = _hideFakeLocationToast.set(value)

    /**
     * Selects the UI language identified by [tag]. Persists the choice through both the normal
     * [PreferencesRepository] store and [LocaleController]'s synchronous store, which
     * `attachBaseContext` reads before this ViewModel is created. The caller is responsible for
     * recreating the Activity so the new locale takes effect immediately.
     *
     * @param tag BCP-47 language tag (e.g. `"en"`, `"zh"`), or an empty string to follow the
     *   system locale.
     */
    fun setLanguage(tag: String) {
        _languageTag.set(tag)
        LocaleController.persistLanguageTag(getApplication(), tag)
    }

    /**
     * Enables or disables external broadcast control and keeps the [ControlReceiver] manifest
     * component state in lockstep. This ensures the stored preference flag and the receiver's
     * exported/unexported state never diverge — toggling via ADB or the UI always produces the
     * same outcome.
     *
     * @param value `true` to enable the [ControlReceiver] and allow external broadcast commands;
     *   `false` to disable it.
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
     * Restores every spoofing and behavioural preference to its constant default value (see
     * `data/Constants.kt`). Updates all flows optimistically so the UI reflects the reset
     * immediately without waiting for disk writes.
     *
     * Two preferences are intentionally excluded from this reset:
     * - **[enableSystemHooks]** — resetting it would trigger a live Xposed scope change, which
     *   requires the service, may fail, and prompts a reboot dialog.
     * - **[languageTag]** — resetting it would require recreating the Activity to apply the new
     *   locale, which is a disruptive side effect for a "reset spoofing defaults" action.
     */
    fun resetToDefaults() {
        setUseAccuracy(DEFAULT_USE_ACCURACY)
        setAccuracy(DEFAULT_ACCURACY)
        setUseAltitude(DEFAULT_USE_ALTITUDE)
        setAltitude(DEFAULT_ALTITUDE)
        setUseRandomize(DEFAULT_USE_RANDOMIZE)
        setRandomizeRadius(DEFAULT_RANDOMIZE_RADIUS)
        setUseVerticalAccuracy(DEFAULT_USE_VERTICAL_ACCURACY)
        setVerticalAccuracy(DEFAULT_VERTICAL_ACCURACY)
        setUseMeanSeaLevel(DEFAULT_USE_MEAN_SEA_LEVEL)
        setMeanSeaLevel(DEFAULT_MEAN_SEA_LEVEL)
        setUseMeanSeaLevelAccuracy(DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY)
        setMeanSeaLevelAccuracy(DEFAULT_MEAN_SEA_LEVEL_ACCURACY)
        setUseSpeed(DEFAULT_USE_SPEED)
        setSpeed(DEFAULT_SPEED)
        setUseSpeedAccuracy(DEFAULT_USE_SPEED_ACCURACY)
        setSpeedAccuracy(DEFAULT_SPEED_ACCURACY)
        setHideFakeLocationToast(DEFAULT_HIDE_FAKE_LOCATION_TOAST)
        setEnableBroadcastControl(DEFAULT_ENABLE_BROADCAST_CONTROL)
    }

    /**
     * Requests that the system framework packages ([SYSTEM_HOOK_PACKAGES]) are added to (or
     * removed from) the module's Xposed hook scope.
     *
     * The stored [enableSystemHooks] preference is only updated once the scope change actually
     * succeeds, after which a [SystemHooksEvent.RestartRequired] is emitted to prompt a reboot.
     * If no service connection exists a [SystemHooksEvent.ModuleNotActive] is emitted instead and
     * no change is made. Any service-level failure produces a [SystemHooksEvent.ScopeRequestFailed]
     * with the reason from the Xposed service.
     *
     * @param enabled `true` to add the system scope; `false` to remove it.
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
