package com.noobexon.xposedfakelocation.data.repository

import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_SIGNAL_BASELINE_SNAPSHOT
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfileArchive
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfileCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalBaselinePreferencesRepositoryTest {
    private val gson = Gson()

    @Test
    fun saveLoadClearAndFlow_roundTripsThroughRemotePrefs() = runBlocking {
        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)
        val baseline = SignalBaselineTestFixtures.validBaseline()
        val observed = async { repository.signalBaselineFlow().take(3).toList() }

        delay(25)
        assertTrue(repository.saveSignalBaseline(baseline))
        delay(25)
        assertTrue(repository.clearSignalBaseline())

        assertEquals(listOf(null, baseline, null), observed.await())
        assertNull(remotePrefs.getString(KEY_SIGNAL_BASELINE_SNAPSHOT, null))
        assertNull(repository.getSignalBaseline())
    }

    @Test
    fun getSignalBaseline_returnsNullForMissingRemotePrefsAndMissingKey() = runBlocking {
        val remotePrefsState = MutableStateFlow<FakeSharedPreferences?>(null)
        val repository = repository(remotePrefsState)

        assertNull(repository.getSignalBaseline())
        assertNull(repository.signalBaselineFlow().take(1).toList().single())
        assertFalse(repository.saveSignalBaseline(SignalBaselineTestFixtures.validBaseline()))
        assertFalse(repository.clearSignalBaseline())

        remotePrefsState.value = FakeSharedPreferences()
        assertNull(repository.getSignalBaseline())
    }

    @Test
    fun saveSignalBaseline_doesNotOverwriteExistingRemotePrefsWithInvalidSnapshot() = runBlocking {
        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)
        val baseline = SignalBaselineTestFixtures.validBaseline()
        val invalidBaseline = baseline.copy(
            location = baseline.location.copy(latitude = 999.0)
        )

        assertTrue(repository.saveSignalBaseline(baseline))
        val savedJson = remotePrefs.getString(KEY_SIGNAL_BASELINE_SNAPSHOT, null)

        assertFalse(repository.saveSignalBaseline(invalidBaseline))
        assertEquals(savedJson, remotePrefs.getString(KEY_SIGNAL_BASELINE_SNAPSHOT, null))
        assertEquals(baseline, repository.getSignalBaseline())
    }

    @Test
    fun getSignalBaseline_rejectsCorruptUnsupportedOversizedAndMismatchedData() {
        val remotePrefs = FakeSharedPreferences()
        val repository = repository(remotePrefs)

        remotePrefs.putBaselineJson("{not-json")
        assertNull(repository.getSignalBaseline())

        remotePrefs.putBaselineJson(
            gson.toJson(
                SignalBaselineTestFixtures.validBaseline().copy(
                    schemaVersion = SignalBaselineCodec.SCHEMA_VERSION + 1
                )
            )
        )
        assertNull(repository.getSignalBaseline())

        remotePrefs.putBaselineJson(
            gson.toJson(
                SignalBaselineTestFixtures.validBaseline(
                    scanResults = List(SignalBaselineCodec.MAX_WIFI_SCANS + 1) {
                        SignalBaselineTestFixtures.scanResultSnapshot()
                    }
                )
            )
        )
        assertNull(repository.getSignalBaseline())

        remotePrefs.putBaselineJson(
            requireNotNull(SignalBaselineCodec.encodeToJson(SignalBaselineTestFixtures.validBaseline()))
        )
        assertNull(repository(remotePrefs, sdkInt = SignalBaselineTestFixtures.CURRENT_SDK + 1).getSignalBaseline())
        assertNull(repository(remotePrefs, buildFingerprint = "other/build/fingerprint").getSignalBaseline())
    }

    @Test
    fun savedLocationProfiles_saveExportImportAndSortBySavedTime() = runBlocking {
        val sourceRepository = repository(FakeSharedPreferences())
        val firstBaseline = SignalBaselineTestFixtures.validBaseline()
        val secondBaseline = firstBaseline.copy(
            capturedAtMillis = firstBaseline.capturedAtMillis + 1,
            location = firstBaseline.location.copy(latitude = 31.230416, longitude = 121.473701)
        )
        val firstProfile = SavedLocationProfileCodec.createProfile(
            snapshot = firstBaseline,
            id = "first",
            savedAtMillis = 1_000L,
            label = "First"
        )
        val secondProfile = SavedLocationProfileCodec.createProfile(
            snapshot = secondBaseline,
            id = "second",
            savedAtMillis = 2_000L,
            label = "Second"
        )

        assertTrue(sourceRepository.saveLocationProfile(firstProfile))
        assertTrue(sourceRepository.saveLocationProfile(secondProfile))
        assertEquals(listOf(secondProfile, firstProfile), sourceRepository.getSavedLocationProfiles())

        val exportedJson = requireNotNull(sourceRepository.exportSavedLocationProfilesJson())
        val importedRepository = repository(FakeSharedPreferences())

        assertEquals(2, importedRepository.importSavedLocationProfilesJson(exportedJson))
        assertEquals(listOf(secondProfile, firstProfile), importedRepository.getSavedLocationProfiles())
    }

    @Test
    fun saveLocationProfile_replacesExistingProfileWithSameId() = runBlocking {
        val repository = repository(FakeSharedPreferences())
        val profile = SavedLocationProfileCodec.createProfile(
            snapshot = SignalBaselineTestFixtures.validBaseline(),
            id = "profile",
            savedAtMillis = 1_000L,
            label = "Original"
        )
        val renamedProfile = profile.copy(label = "Renamed")

        assertTrue(repository.saveLocationProfile(profile))
        assertTrue(repository.saveLocationProfile(renamedProfile))

        assertEquals(listOf(renamedProfile), repository.getSavedLocationProfiles())
    }

    @Test
    fun importSavedLocationProfilesJson_rejectsInvalidJsonWithoutOverwritingExistingProfiles() = runBlocking {
        val repository = repository(FakeSharedPreferences())
        val existingProfile = SavedLocationProfileCodec.createProfile(
            snapshot = SignalBaselineTestFixtures.validBaseline(),
            id = "existing",
            savedAtMillis = 1_000L,
            label = "Existing"
        )

        assertTrue(repository.saveLocationProfile(existingProfile))

        assertNull(repository.importSavedLocationProfilesJson("{not-json"))
        assertEquals(listOf(existingProfile), repository.getSavedLocationProfiles())
    }

    @Test
    fun importSavedLocationProfilesJson_keepsProfilesFromOtherBuildButActiveReplayStaysStrict() = runBlocking {
        val sourceRepository = repository(FakeSharedPreferences())
        val sourceProfile = SavedLocationProfileCodec.createProfile(
            snapshot = baselineForDevice(
                sdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                buildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            ),
            id = "source",
            savedAtMillis = 1_000L,
            label = "Source"
        )
        assertTrue(sourceRepository.saveLocationProfile(sourceProfile))
        val exportedJson = requireNotNull(sourceRepository.exportSavedLocationProfilesJson())

        val targetRemotePrefs = FakeSharedPreferences()
        val targetRepository = repository(
            remotePrefs = targetRemotePrefs,
            sdkInt = SignalBaselineTestFixtures.CURRENT_SDK + 1,
            buildFingerprint = "other/build/fingerprint"
        )

        assertEquals(1, targetRepository.importSavedLocationProfilesJson(exportedJson))
        assertEquals(listOf(sourceProfile), targetRepository.getSavedLocationProfiles())
        assertFalse(targetRepository.saveSignalBaseline(sourceProfile.baseline))
        assertNull(targetRemotePrefs.getString(KEY_SIGNAL_BASELINE_SNAPSHOT, null))
    }

    @Test
    fun saveLocationProfile_afterDeviceBuildChangePreservesOlderProfiles() = runBlocking {
        val localPrefs = FakeSharedPreferences()
        val remotePrefs = FakeSharedPreferences()
        val oldRepository = repository(
            localPrefs = localPrefs,
            remotePrefs = remotePrefs,
            sdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
            buildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
        )
        val oldProfile = SavedLocationProfileCodec.createProfile(
            snapshot = baselineForDevice(
                sdkInt = SignalBaselineTestFixtures.CURRENT_SDK,
                buildFingerprint = SignalBaselineTestFixtures.CURRENT_BUILD
            ),
            id = "old",
            savedAtMillis = 1_000L,
            label = "Old"
        )
        assertTrue(oldRepository.saveLocationProfile(oldProfile))

        val newSdkInt = SignalBaselineTestFixtures.CURRENT_SDK + 1
        val newBuildFingerprint = "other/build/fingerprint"
        val newRepository = repository(
            localPrefs = localPrefs,
            remotePrefs = remotePrefs,
            sdkInt = newSdkInt,
            buildFingerprint = newBuildFingerprint
        )
        val newProfile = SavedLocationProfileCodec.createProfile(
            snapshot = baselineForDevice(
                sdkInt = newSdkInt,
                buildFingerprint = newBuildFingerprint
            ),
            id = "new",
            savedAtMillis = 2_000L,
            label = "New"
        )

        assertEquals(listOf(oldProfile), newRepository.getSavedLocationProfiles())
        assertTrue(newRepository.saveLocationProfile(newProfile))
        assertEquals(listOf(newProfile, oldProfile), newRepository.getSavedLocationProfiles())
    }

    @Test
    fun importSavedLocationProfilesJson_rejectsArchiveWithOnlyInvalidProfilesWithoutOverwritingExistingProfiles() = runBlocking {
        val repository = repository(FakeSharedPreferences())
        val existingProfile = SavedLocationProfileCodec.createProfile(
            snapshot = SignalBaselineTestFixtures.validBaseline(),
            id = "existing",
            savedAtMillis = 1_000L,
            label = "Existing"
        )
        val invalidProfile = SavedLocationProfileCodec.createProfile(
            snapshot = SignalBaselineTestFixtures.validBaseline().copy(
                location = SignalBaselineTestFixtures.validBaseline().location.copy(latitude = 999.0)
            ),
            id = "invalid",
            savedAtMillis = 2_000L,
            label = "Invalid"
        )
        val invalidArchiveJson = gson.toJson(
            SavedLocationProfileArchive(
                schemaVersion = SavedLocationProfileCodec.SCHEMA_VERSION,
                exportedAtMillis = 3_000L,
                profiles = listOf(invalidProfile)
            )
        )

        assertTrue(repository.saveLocationProfile(existingProfile))

        assertNull(repository.importSavedLocationProfilesJson(invalidArchiveJson))
        assertEquals(listOf(existingProfile), repository.getSavedLocationProfiles())
    }

    private fun repository(
        remotePrefs: FakeSharedPreferences,
        sdkInt: Int = SignalBaselineTestFixtures.CURRENT_SDK,
        buildFingerprint: String = SignalBaselineTestFixtures.CURRENT_BUILD
    ): PreferencesRepository {
        return repository(MutableStateFlow<FakeSharedPreferences?>(remotePrefs), sdkInt, buildFingerprint)
    }

    private fun repository(
        localPrefs: FakeSharedPreferences,
        remotePrefs: FakeSharedPreferences,
        sdkInt: Int = SignalBaselineTestFixtures.CURRENT_SDK,
        buildFingerprint: String = SignalBaselineTestFixtures.CURRENT_BUILD
    ): PreferencesRepository {
        val remotePrefsState = MutableStateFlow<FakeSharedPreferences?>(remotePrefs)
        return PreferencesRepository(
            localPrefs = localPrefs,
            remotePrefsProvider = { remotePrefsState.value },
            remotePrefsState = remotePrefsState,
            sdkIntProvider = { sdkInt },
            buildFingerprintProvider = { buildFingerprint }
        )
    }

    private fun repository(
        remotePrefsState: MutableStateFlow<FakeSharedPreferences?>,
        sdkInt: Int = SignalBaselineTestFixtures.CURRENT_SDK,
        buildFingerprint: String = SignalBaselineTestFixtures.CURRENT_BUILD
    ): PreferencesRepository {
        return PreferencesRepository(
            localPrefs = FakeSharedPreferences(),
            remotePrefsProvider = { remotePrefsState.value },
            remotePrefsState = remotePrefsState,
            sdkIntProvider = { sdkInt },
            buildFingerprintProvider = { buildFingerprint }
        )
    }

    private fun FakeSharedPreferences.putBaselineJson(json: String) {
        edit().putString(KEY_SIGNAL_BASELINE_SNAPSHOT, json).apply()
    }

    private fun baselineForDevice(sdkInt: Int, buildFingerprint: String): SignalBaselineSnapshot {
        return SignalBaselineTestFixtures.validBaseline(
            cellInfo = listOf(
                SignalBaselineTestFixtures.gsmCellInfoSnapshot(parcelBytes = null),
                SignalBaselineTestFixtures.lteCellInfoSnapshot()
            ),
            neighboringCellInfo = emptyList()
        ).copy(
            captureSdkInt = sdkInt,
            captureBuildFingerprint = buildFingerprint
        )
    }
}
