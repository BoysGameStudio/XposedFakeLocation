package com.noobexon.xposedfakelocation.manager.ui.about

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A single GitHub contributor, mapped from the GitHub API response and ready for display.
 *
 * @property name The contributor's GitHub login (username).
 * @property githubUrl The full URL to the contributor's GitHub profile.
 * @property avatarUrl The URL of the contributor's GitHub avatar image.
 * @property contributions The total number of commits the contributor has made to the repository.
 */
@Immutable
data class Contributor(
    val name: String,
    val githubUrl: String,
    val avatarUrl: String,
    val contributions: Int
)

/**
 * Represents the loading lifecycle of the contributors list.
 *
 * - [Loading] — the initial state while a network request is in flight.
 * - [Success] — the request completed; [Success.developer] is the repo owner (may be `null` if not
 *   found in the response) and [Success.contributors] is everyone else, sorted by contribution count.
 * - [Error] — the request failed; [Error.message] carries the underlying exception message, if any.
 */
@Immutable
sealed interface ContributorsUiState {
    data object Loading : ContributorsUiState
    data class Success(
        val developer: Contributor?,
        val contributors: List<Contributor>
    ) : ContributorsUiState
    data class Error(val message: String? = null) : ContributorsUiState
}

/**
 * ViewModel for the About screen.
 *
 * Fetches the repository's contributor list from the GitHub API, splits it into a developer entry
 * and a general contributors list, and exposes the result as [contributorsState].
 *
 * A process-level [cache] is used so that navigating away from and back to the About screen reuses
 * a recent fetch (within [CACHE_TTL_MILLIS]) instead of hitting the API on every ViewModel
 * creation.
 */
class AboutViewModel : ViewModel() {

    private val gson = Gson()

    private val _contributorsState = MutableStateFlow<ContributorsUiState>(ContributorsUiState.Loading)

    /** The current state of the contributors fetch, observed by the About screen. */
    val contributorsState: StateFlow<ContributorsUiState> = _contributorsState.asStateFlow()

    /**
     * A pre-built [Contributor] for the developer that is displayed immediately while the live
     * data is loading, or as a permanent fallback when the API request fails and the developer's
     * entry cannot be extracted from the response.
     */
    val developerFallback = Contributor(
        name = DEVELOPER,
        githubUrl = "https://github.com/$DEVELOPER",
        avatarUrl = "https://github.com/$DEVELOPER.png",
        contributions = 0
    )

    init {
        loadContributors()
    }

    /**
     * Loads the contributor list, optionally bypassing the in-memory cache.
     *
     * If [forceRefresh] is `false` and a fresh cached result exists (within [CACHE_TTL_MILLIS]),
     * the cache is used and no network request is made. Otherwise the state is reset to
     * [ContributorsUiState.Loading] and [fetchContributors] is called on [Dispatchers.IO].
     *
     * A [CancellationException] from a cancelled coroutine is always re-thrown so that structured
     * concurrency is not silently broken.
     *
     * @param forceRefresh When `true`, skips the cache and always performs a network request.
     */
    fun loadContributors(forceRefresh: Boolean = false) {
        val cached = cachedContributors()
        if (!forceRefresh && cached != null) {
            _contributorsState.value = splitContributors(cached)
            return
        }

        _contributorsState.value = ContributorsUiState.Loading
        viewModelScope.launch {
            _contributorsState.value = runCatching { fetchContributors() }
                .fold(
                    onSuccess = {
                        cache = CachedResult(it, System.currentTimeMillis())
                        splitContributors(it)
                    },
                    onFailure = {
                        if (it is CancellationException) throw it
                        ContributorsUiState.Error(it.message)
                    }
                )
        }
    }

    /**
     * Separates [all] contributors into the repo owner and everyone else.
     *
     * The developer is identified by a case-insensitive match against [DEVELOPER]. The remaining
     * contributors preserve the sort order from [fetchContributors] (descending by contribution
     * count).
     *
     * @param all The full, sorted list of contributors returned by the GitHub API.
     * @return A [ContributorsUiState.Success] with the split result.
     */
    private fun splitContributors(all: List<Contributor>): ContributorsUiState.Success {
        val developer = all.firstOrNull { it.name.equals(DEVELOPER, ignoreCase = true) }
        val others = all.filterNot { it.name.equals(DEVELOPER, ignoreCase = true) }
        return ContributorsUiState.Success(developer = developer, contributors = others)
    }

    /**
     * Returns the cached contributor list if one exists and is still within [CACHE_TTL_MILLIS],
     * or `null` otherwise.
     */
    private fun cachedContributors(): List<Contributor>? {
        val snapshot = cache ?: return null
        val isFresh = System.currentTimeMillis() - snapshot.timestampMillis < CACHE_TTL_MILLIS
        return if (isFresh) snapshot.contributors else null
    }

    /**
     * Performs the GitHub API request and maps the JSON response to a list of [Contributor]s.
     *
     * Bots and entries with a blank login are excluded. Results are sorted by descending
     * contribution count. The connection is always disconnected in a `finally` block.
     *
     * Must be called from a coroutine; switches to [Dispatchers.IO] internally.
     *
     * @throws IllegalStateException if the GitHub API returns a non-2xx HTTP status code.
     */
    private suspend fun fetchContributors(): List<Contributor> = withContext(Dispatchers.IO) {
        val connection = (URL(CONTRIBUTORS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            // GitHub rejects requests without a User-Agent header.
            setRequestProperty("User-Agent", "XposedFakeLocation-App")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("GitHub API responded with HTTP $responseCode")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<GithubContributor>>() {}.type
            gson.fromJson<List<GithubContributor>>(body, type)
                .orEmpty()
                .filter { it.type != "Bot" && !it.login.isNullOrBlank() }
                .sortedByDescending { it.contributions }
                .map {
                    Contributor(
                        name = it.login.orEmpty(),
                        githubUrl = it.htmlUrl ?: "https://github.com/${it.login}",
                        avatarUrl = it.avatarUrl ?: "https://github.com/${it.login}.png",
                        contributions = it.contributions
                    )
                }
        } finally {
            connection.disconnect()
        }
    }

    /** Raw contributor object as returned by the GitHub contributors API. */
    private data class GithubContributor(
        @SerializedName("login") val login: String?,
        @SerializedName("avatar_url") val avatarUrl: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        @SerializedName("type") val type: String?,
        @SerializedName("contributions") val contributions: Int = 0
    )

    /** An in-memory snapshot of a successful API response together with the time it was fetched. */
    private data class CachedResult(
        val contributors: List<Contributor>,
        val timestampMillis: Long
    )

    private companion object {
        const val CONTRIBUTORS_URL =
            "https://api.github.com/repos/noobexon1/XposedFakeLocation/contributors?per_page=100"
        const val DEVELOPER = "noobexon1"
        const val TIMEOUT_MILLIS = 10_000
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L

        // Process-level cache so re-opening the About screen (which recreates the
        // ViewModel) reuses a recent result instead of hitting the API again.
        @Volatile
        var cache: CachedResult? = null
    }
}
