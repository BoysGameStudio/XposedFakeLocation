package com.noobexon.xposedfakelocation.manager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Activity-scoped ViewModel that holds app-wide UI state. Currently exposes [themeOption] so
 * [MainActivity] can apply the user's theme preference reactively — without requiring an Activity
 * recreation — by passing [darkTheme] directly to [XposedFakeLocationTheme].
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    /**
     * Currently active [ThemeOption], derived from the persisted theme tag. Eagerly started so
     * [StateFlow.value] is always current and [MainActivity] sees the correct theme on first frame.
     */
    val themeOption: StateFlow<ThemeOption> = preferencesRepository.getThemeOptionFlow()
        .map { ThemeOption.fromTag(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeOption.SYSTEM)
}
