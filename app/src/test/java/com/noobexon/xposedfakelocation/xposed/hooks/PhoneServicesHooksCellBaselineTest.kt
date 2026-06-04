package com.noobexon.xposedfakelocation.xposed.hooks

import android.telephony.CellIdentity
import com.google.gson.Gson
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.testutil.FakeSharedPreferences
import com.noobexon.xposedfakelocation.testutil.SignalBaselineTestFixtures
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhoneServicesHooksCellBaselineTest {
    private val gson = Gson()

    @Before
    fun setUp() {
        resetPreferencesUtil()
    }

    @After
    fun tearDown() {
        resetPreferencesUtil()
    }

    @Test
    fun cellLocationHookResult_failsClosedForSpoofedTargetWhenBaselineIsMissing() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))

        val missing = PhoneServicesHooks.cellLocationHookResult(
            args = listOf(TARGET_PACKAGE),
            method = HookReturnTypes::class.java.getDeclaredMethod("cellLocation"),
            cellularProvider = { null }
        )

        require(missing is PhoneServiceHookResult.Spoofed)
        assertNull(missing.value)
    }

    @Test
    fun cellLocationHookResult_usesCellIdentityReturnTypeForModernPhoneService() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))
        val cellular = SignalBaselineTestFixtures.validBaseline().cellular
        val method = HookReturnTypes::class.java.getDeclaredMethod("cellIdentity")
        val sentinel = Any()
        var replayedCellular: Any? = null
        var replayedMethod: Any? = null

        val replay = PhoneServicesHooks.cellLocationHookResult(
            args = listOf(TARGET_PACKAGE),
            method = method,
            cellularProvider = { cellular },
            replayProvider = { replayCellular, replayMethod ->
                replayedCellular = replayCellular
                replayedMethod = replayMethod
                sentinel
            }
        )

        require(replay is PhoneServiceHookResult.Spoofed)
        assertSame(sentinel, replay.value)
        assertSame(cellular, replayedCellular)
        assertSame(method, replayedMethod)
    }

    @Test
    fun cellInfoHookResultsReturnEmptyForSpoofedTargetWithoutRealFallback() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE))
        var allCellProviderCalls = 0
        var neighboringProviderCalls = 0

        val allCellInfo = PhoneServicesHooks.allCellInfoHookResult(
            args = listOf(TARGET_PACKAGE),
            cellularProvider = {
                allCellProviderCalls++
                null
            }
        )
        val neighboringCellInfo = PhoneServicesHooks.neighboringCellInfoHookResult(
            args = listOf(TARGET_PACKAGE),
            cellularProvider = {
                neighboringProviderCalls++
                null
            }
        )

        require(allCellInfo is PhoneServiceHookResult.Spoofed)
        require(neighboringCellInfo is PhoneServiceHookResult.Spoofed)
        assertTrue(allCellInfo.value.isEmpty())
        assertTrue(neighboringCellInfo.value.isEmpty())
        assertEquals(1, allCellProviderCalls)
        assertEquals(1, neighboringProviderCalls)
    }

    @Test
    fun hookResultsArePassthroughForNonTargetsDisabledStateAndManagerPackage() {
        initPreferences(isPlaying = true, targets = listOf(TARGET_PACKAGE, MANAGER_APP_PACKAGE_NAME))
        var providerCalled = false

        val nonTarget = PhoneServicesHooks.allCellInfoHookResult(
            args = listOf("com.example.other"),
            cellularProvider = {
                providerCalled = true
                SignalBaselineTestFixtures.validBaseline().cellular
            }
        )

        assertSame(PhoneServiceHookResult.Passthrough, nonTarget)
        assertFalse(providerCalled)
        assertFalse(PhoneServicesHooks.shouldSpoofPhoneServiceArgs(listOf(MANAGER_APP_PACKAGE_NAME)))

        initPreferences(isPlaying = false, targets = listOf(TARGET_PACKAGE))
        assertFalse(PhoneServicesHooks.shouldSpoofPhoneServiceArgs(listOf(TARGET_PACKAGE)))
    }

    private fun initPreferences(isPlaying: Boolean, targets: List<String>) {
        val remotePrefs = FakeSharedPreferences()
        remotePrefs.edit()
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .putString(KEY_TARGET_APPS, gson.toJson(targets))
            .apply()
        PreferencesUtil.init(remotePrefs)
    }

    private fun resetPreferencesUtil() {
        val preferencesField = PreferencesUtil::class.java.getDeclaredField("preferences")
        preferencesField.isAccessible = true
        preferencesField.set(PreferencesUtil, null)
    }

    private class HookReturnTypes {
        @Suppress("unused")
        fun cellLocation(): Any? = null

        @Suppress("unused")
        fun cellIdentity(): CellIdentity? = null
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
    }
}
