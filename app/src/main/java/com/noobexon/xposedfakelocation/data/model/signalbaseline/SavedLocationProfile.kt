package com.noobexon.xposedfakelocation.data.model.signalbaseline

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.util.Locale
import java.util.UUID

data class SavedLocationProfile(
    val id: String,
    val savedAtMillis: Long,
    val label: String,
    val baseline: SignalBaselineSnapshot
)

data class SavedLocationProfileArchive(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val profiles: List<SavedLocationProfile>
)

data class SavedLocationProfileParseResult(
    val profiles: List<SavedLocationProfile>,
    val reason: String? = null
) {
    val isValid: Boolean get() = reason == null
}

object SavedLocationProfileCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_PROFILE_COUNT = 128

    private const val MAX_PROFILE_ID_LENGTH = 96
    private const val MAX_PROFILE_LABEL_LENGTH = 160

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun createProfile(
        snapshot: SignalBaselineSnapshot,
        id: String = UUID.randomUUID().toString(),
        savedAtMillis: Long = System.currentTimeMillis(),
        label: String = defaultLabel(snapshot)
    ): SavedLocationProfile {
        return SavedLocationProfile(
            id = id,
            savedAtMillis = savedAtMillis,
            label = label,
            baseline = snapshot
        )
    }

    fun encodeProfiles(profiles: List<SavedLocationProfile>): String? {
        val normalizedProfiles = profiles
            .distinctBy(SavedLocationProfile::id)
            .sortedByDescending(SavedLocationProfile::savedAtMillis)
            .take(MAX_PROFILE_COUNT)

        if (normalizedProfiles.any { !isValidProfile(it) }) return null

        return runCatching {
            gson.toJson(
                SavedLocationProfileArchive(
                    schemaVersion = SCHEMA_VERSION,
                    exportedAtMillis = System.currentTimeMillis(),
                    profiles = normalizedProfiles
                )
            )
        }.getOrNull()
    }

    fun parseProfiles(json: String?): SavedLocationProfileParseResult {
        if (json.isNullOrBlank()) return invalid("missing_json")

        return runCatching {
            val element = JsonParser.parseString(json)
            if (!element.isJsonObject) return invalid("json_not_object")

            val archive = gson.fromJson(element, SavedLocationProfileArchive::class.java)
                ?: return invalid("missing_archive")
            if (archive.schemaVersion != SCHEMA_VERSION) return invalid("unsupported_schema")
            if (archive.exportedAtMillis <= 0L) return invalid("exported_at_invalid")

            val rawProfiles = archive.profiles.orEmpty()
            val profiles = rawProfiles
                .asSequence()
                .filter(::isValidProfile)
                .distinctBy(SavedLocationProfile::id)
                .sortedByDescending(SavedLocationProfile::savedAtMillis)
                .take(MAX_PROFILE_COUNT)
                .toList()
            if (rawProfiles.isNotEmpty() && profiles.isEmpty()) return invalid("profiles_invalid")

            SavedLocationProfileParseResult(profiles = profiles)
        }.getOrElse {
            invalid("malformed_json")
        }
    }

    fun isValidProfile(profile: SavedLocationProfile): Boolean {
        return runCatching {
            !hasInvalidStableFields(profile) &&
                SignalBaselineCodec.validateForArchive(profile.baseline).isValid
        }.getOrDefault(false)
    }

    private fun hasInvalidStableFields(profile: SavedLocationProfile): Boolean {
        return profile.id.isBlank() ||
            profile.id.length > MAX_PROFILE_ID_LENGTH ||
            profile.savedAtMillis <= 0L ||
            profile.label.length > MAX_PROFILE_LABEL_LENGTH
    }

    private fun defaultLabel(snapshot: SignalBaselineSnapshot): String {
        return String.format(
            Locale.US,
            "%.6f, %.6f",
            snapshot.location.latitude,
            snapshot.location.longitude
        )
    }

    private fun invalid(reason: String): SavedLocationProfileParseResult {
        return SavedLocationProfileParseResult(profiles = emptyList(), reason = reason)
    }
}
