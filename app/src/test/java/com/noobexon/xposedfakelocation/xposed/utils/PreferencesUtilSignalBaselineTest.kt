package com.noobexon.xposedfakelocation.xposed.utils

import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_SIGNAL_BASELINE_SNAPSHOT
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesUtilSignalBaselineTest {
    private val gson = Gson()

    @Test
    fun getSignalBaseline_matchesRepositoryForSavedValidBaseline() = runBlocking {
        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)
        val baseline = SignalBaselineTestFixtures.validBaseline()

        repository.saveSignalBaseline(baseline)
        PreferencesUtil.init(remotePrefs)

        assertEquals(baseline, repository.getSignalBaseline())
        assertEquals(
            repository.getSignalBaseline(),
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            )
        )
    }

    @Test
    fun getSignalBaseline_returnsNullForMissingPreferencesMissingKeyAndCorruptJson() {
        resetPreferencesUtil()
        assertNull(
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            )
        )

        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)
        PreferencesUtil.init(remotePrefs)
        assertNull(repository.getSignalBaseline())
        assertNull(
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            )
        )

        remotePrefs.edit().putString(KEY_SIGNAL_BASELINE_SNAPSHOT, "{not-json").apply()
        assertNull(repository.getSignalBaseline())
        assertNull(
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            )
        )
    }

    @Test
    fun getSignalBaseline_matchesRepositoryForInvalidSchemaOversizedAndBuildMismatch() {
        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)
        PreferencesUtil.init(remotePrefs)

        remotePrefs.edit().putString(
            KEY_SIGNAL_BASELINE_SNAPSHOT,
            gson.toJson(
                SignalBaselineTestFixtures.validBaseline().copy(schemaVersion = Int.MAX_VALUE)
            )
        ).apply()
        assertNull(repository.getSignalBaseline())
        assertNull(hookSignalBaseline())

        remotePrefs.edit().putString(
            KEY_SIGNAL_BASELINE_SNAPSHOT,
            gson.toJson(
                SignalBaselineTestFixtures.validBaseline(
                    cellInfo = List(65) { SignalBaselineTestFixtures.gsmCellInfoSnapshot(parcelBytes = null) }
                )
            )
        ).apply()
        assertNull(repository.getSignalBaseline())
        assertNull(hookSignalBaseline())

        remotePrefs.edit().putString(
            KEY_SIGNAL_BASELINE_SNAPSHOT,
            requireNotNull(
                com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec.encodeToJson(
                    SignalBaselineTestFixtures.validBaseline()
                )
            )
        ).apply()
        assertNull(
            PreferencesUtil.getSignalBaseline(
                currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                currentBuildFingerprint = "other/build/fingerprint"
            )
        )
    }

    private fun hookSignalBaseline() = PreferencesUtil.getSignalBaseline(
        currentSdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
        currentBuildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
    )

    private fun repository(remotePrefs: FakeSharedPreferences): PreferencesRepository {
        val remotePrefsState = MutableStateFlow(remotePrefs)
        return PreferencesRepository(
            localPrefs = FakeSharedPreferences(),
            remotePrefsProvider = { remotePrefsState.value },
            remotePrefsState = remotePrefsState,
            sdkIntProvider = { SignalBaselineTestFixtures.CURRENT_SDK },
            buildFingerprintProvider = { SignalBaselineTestFixtures.CURRENT_BUILD }
        )
    }

    private fun resetPreferencesUtil() {
        val preferencesField = PreferencesUtil::class.java.getDeclaredField("preferences")
        preferencesField.isAccessible = true
        preferencesField.set(PreferencesUtil, null)
    }
}
