package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfile
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.manager.baseline.SignalBaselineCapture
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelSignalBaselineMenuTest {
    @Test
    fun saveRealEnvironmentBaseline_savesSnapshotAndReturnsCountOnlyToastMessage() = runBlocking {
        val baseline = SignalBaselineTestFixtures.validBaseline(
            cellInfo = listOf(
                SignalBaselineTestFixtures.gsmCellInfoSnapshot(),
                SignalBaselineTestFixtures.lteCellInfoSnapshot()
            ),
            scanResults = List(3) { SignalBaselineTestFixtures.scanResultSnapshot() }
        )
        val store = FakeSignalBaselineStore()
        val actions = actions(
            store = store,
            captureResult = SignalBaselineCapture.CaptureResult.Success(baseline)
        )

        val message = actions.saveRealEnvironmentBaseline("  Office baseline  ")

        assertEquals(1, store.saveCalls)
        assertEquals(1, store.saveProfileCalls)
        assertSame(baseline, store.savedBaseline)
        assertSame(baseline, store.savedProfiles.single().baseline)
        assertEquals("Office baseline", store.savedProfiles.single().label)
        assertEquals(R.string.toast_signal_baseline_save_success, message.messageRes)
        assertEquals(listOf(2, 3), message.formatArgs)
        assertCountOnlyFormatArgs(message)
    }

    @Test
    fun clearRealEnvironmentBaseline_clearsStoredBaselineAndReturnsSafeToastMessage() = runBlocking {
        val existingBaseline = SignalBaselineTestFixtures.validBaseline()
        val store = FakeSignalBaselineStore(savedBaseline = existingBaseline)
        val actions = actions(store = store)

        val message = actions.clearRealEnvironmentBaseline()

        assertEquals(1, store.clearCalls)
        assertNull(store.savedBaseline)
        assertEquals(R.string.toast_signal_baseline_clear_success, message.messageRes)
        assertTrue(message.formatArgs.isEmpty())
    }

    @Test
    fun saveRealEnvironmentBaseline_noRealLocationDoesNotOverwriteExistingBaseline() = runBlocking {
        val existingBaseline = SignalBaselineTestFixtures.validBaseline()
        val store = FakeSignalBaselineStore(savedBaseline = existingBaseline)
        val actions = actions(
            store = store,
            captureResult = SignalBaselineCapture.CaptureResult.NoRealLocation
        )

        val message = actions.saveRealEnvironmentBaseline()

        assertEquals(0, store.saveCalls)
        assertEquals(0, store.saveProfileCalls)
        assertSame(existingBaseline, store.savedBaseline)
        assertEquals(R.string.toast_signal_baseline_save_no_location, message.messageRes)
        assertTrue(message.formatArgs.isEmpty())
    }

    @Test
    fun saveRealEnvironmentBaseline_missingPermissionSkipsCaptureAndSave() = runBlocking {
        val store = FakeSignalBaselineStore()
        val actions = SignalBaselineMenuActions(
            hasLocationPermission = { false },
            captureBaseline = { error("capture should not run without location permission") },
            baselineStore = store
        )

        val message = actions.saveRealEnvironmentBaseline()

        assertEquals(0, store.saveCalls)
        assertEquals(0, store.saveProfileCalls)
        assertNull(store.savedBaseline)
        assertEquals(R.string.toast_signal_baseline_save_missing_permission, message.messageRes)
        assertTrue(message.formatArgs.isEmpty())
    }

    private fun actions(
        store: FakeSignalBaselineStore,
        captureResult: SignalBaselineCapture.CaptureResult = SignalBaselineCapture.CaptureResult.NoRealLocation
    ): SignalBaselineMenuActions {
        return SignalBaselineMenuActions(
            hasLocationPermission = { true },
            captureBaseline = { captureResult },
            baselineStore = store
        )
    }

    private fun assertCountOnlyFormatArgs(message: SignalBaselineToastMessage) {
        assertTrue(message.formatArgs.all { it is Int })
        val payload = message.formatArgs.joinToString(separator = " ")
        listOf(
            "37.4219983",
            "-122.084",
            "TestNet",
            "12:34:56:78:9a:bc",
            "12345",
            "345678",
            "AQIDBA=="
        ).forEach { rawValue ->
            assertFalse("Toast format args leaked $rawValue", payload.contains(rawValue))
        }
    }

    private class FakeSignalBaselineStore(
        var savedBaseline: SignalBaselineSnapshot? = null,
        private val saveResult: Boolean = true,
        private val clearResult: Boolean = true
    ) : SignalBaselineStore {
        var saveCalls = 0
            private set
        var saveProfileCalls = 0
            private set
        var clearCalls = 0
            private set
        val savedProfiles = mutableListOf<SavedLocationProfile>()

        override suspend fun saveSignalBaseline(snapshot: SignalBaselineSnapshot): Boolean {
            saveCalls++
            if (saveResult) savedBaseline = snapshot
            return saveResult
        }

        override suspend fun saveLocationProfile(profile: SavedLocationProfile): Boolean {
            saveProfileCalls++
            savedProfiles.add(profile)
            return true
        }

        override suspend fun clearSignalBaseline(): Boolean {
            clearCalls++
            if (clearResult) savedBaseline = null
            return clearResult
        }
    }
}
