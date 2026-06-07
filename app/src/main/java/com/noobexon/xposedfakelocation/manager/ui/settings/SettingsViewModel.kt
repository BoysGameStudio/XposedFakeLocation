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

/** One-shot messages surfaced to the settings UI. */
sealed interface SystemHooksEvent {
    /** The scope change succeeded; the user must reboot for it to take effect (or be undone). */
    data class RestartRequired(val enabled: Boolean) : SystemHooksEvent
    data object ModuleNotActive : SystemHooksEvent
    data class ScopeRequestFailed(val message: String) : SystemHooksEvent
}

private const val TAG = "SettingsViewModel"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    /**
     * Holds a single preference as a hot [StateFlow]. [set] updates the value optimistically for
     * instant UI feedback and then persists it; the repository [flow] remains the source of truth
     * and re-emits the committed value.
     */
    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val save: suspend (T) -> Unit
    ) {
        private val _state = MutableStateFlow(initialValue)
        val state: StateFlow<T> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                flow.collect { _state.value = it }
            }
        }

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
    val useAccuracy: StateFlow<Boolean> = _useAccuracy.state

    private val _accuracy = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy
    )
    val accuracy: StateFlow<Double> = _accuracy.state

    // Altitude
    private val _useAltitude = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude
    )
    val useAltitude: StateFlow<Boolean> = _useAltitude.state

    private val _altitude = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude
    )
    val altitude: StateFlow<Double> = _altitude.state

    // Randomize
    private val _useRandomize = Preference(
        DEFAULT_USE_RANDOMIZE,
        preferencesRepository.getUseRandomizeFlow(),
        preferencesRepository::saveUseRandomize
    )
    val useRandomize: StateFlow<Boolean> = _useRandomize.state

    private val _randomizeRadius = Preference(
        DEFAULT_RANDOMIZE_RADIUS,
        preferencesRepository.getRandomizeRadiusFlow(),
        preferencesRepository::saveRandomizeRadius
    )
    val randomizeRadius: StateFlow<Double> = _randomizeRadius.state

    // Vertical Accuracy
    private val _useVerticalAccuracy = Preference(
        DEFAULT_USE_VERTICAL_ACCURACY,
        preferencesRepository.getUseVerticalAccuracyFlow(),
        preferencesRepository::saveUseVerticalAccuracy
    )
    val useVerticalAccuracy: StateFlow<Boolean> = _useVerticalAccuracy.state

    private val _verticalAccuracy = Preference(
        DEFAULT_VERTICAL_ACCURACY,
        preferencesRepository.getVerticalAccuracyFlow(),
        preferencesRepository::saveVerticalAccuracy
    )
    val verticalAccuracy: StateFlow<Float> = _verticalAccuracy.state

    // Mean Sea Level
    private val _useMeanSeaLevel = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL,
        preferencesRepository.getUseMeanSeaLevelFlow(),
        preferencesRepository::saveUseMeanSeaLevel
    )
    val useMeanSeaLevel: StateFlow<Boolean> = _useMeanSeaLevel.state

    private val _meanSeaLevel = Preference(
        DEFAULT_MEAN_SEA_LEVEL,
        preferencesRepository.getMeanSeaLevelFlow(),
        preferencesRepository::saveMeanSeaLevel
    )
    val meanSeaLevel: StateFlow<Double> = _meanSeaLevel.state

    // Mean Sea Level Accuracy
    private val _useMeanSeaLevelAccuracy = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getUseMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveUseMeanSeaLevelAccuracy
    )
    val useMeanSeaLevelAccuracy: StateFlow<Boolean> = _useMeanSeaLevelAccuracy.state

    private val _meanSeaLevelAccuracy = Preference(
        DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveMeanSeaLevelAccuracy
    )
    val meanSeaLevelAccuracy: StateFlow<Float> = _meanSeaLevelAccuracy.state

    // Speed
    private val _useSpeed = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed
    )
    val useSpeed: StateFlow<Boolean> = _useSpeed.state

    private val _speed = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed
    )
    val speed: StateFlow<Float> = _speed.state

    // Speed Accuracy
    private val _useSpeedAccuracy = Preference(
        DEFAULT_USE_SPEED_ACCURACY,
        preferencesRepository.getUseSpeedAccuracyFlow(),
        preferencesRepository::saveUseSpeedAccuracy
    )
    val useSpeedAccuracy: StateFlow<Boolean> = _useSpeedAccuracy.state

    private val _speedAccuracy = Preference(
        DEFAULT_SPEED_ACCURACY,
        preferencesRepository.getSpeedAccuracyFlow(),
        preferencesRepository::saveSpeedAccuracy
    )
    val speedAccuracy: StateFlow<Float> = _speedAccuracy.state

    // Hide Fake Location Toast
    private val _hideFakeLocationToast = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast
    )
    val hideFakeLocationToast: StateFlow<Boolean> = _hideFakeLocationToast.state

    // External Broadcast Control
    private val _enableBroadcastControl = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl
    )
    val enableBroadcastControl: StateFlow<Boolean> = _enableBroadcastControl.state

    // System-Level Hooks (state mirrors the persisted pref; the switch only flips
    // once the scope change actually succeeds).
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    private val _systemHooksEvents = Channel<SystemHooksEvent>(Channel.BUFFERED)
    val systemHooksEvents: Flow<SystemHooksEvent> = _systemHooksEvents.receiveAsFlow()

    // Language
    private val _languageTag = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag
    )
    val languageTag: StateFlow<String> = _languageTag.state

    // Setter methods for all preferences
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
     * Adds (or removes) the system framework packages to the module scope. The persisted toggle is
     * only updated once the scope change succeeds, after which the user is prompted to reboot.
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
