package com.noobexon.xposedfakelocation.xposed.hooks

import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_WIFI_IDENTITY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiIdentityHookPolicyTest {
    @Test
    fun wifiIdentityHooksAreDisabledByDefault() {
        assertFalse(DEFAULT_ENABLE_WIFI_IDENTITY)
    }

    @Test
    fun gateRequiresActiveSpoofingAndExplicitOptIn() {
        assertFalse(WifiIdentityHookPolicy.shouldSpoof(isPlaying = null, wifiIdentityEnabled = true))
        assertFalse(WifiIdentityHookPolicy.shouldSpoof(isPlaying = false, wifiIdentityEnabled = true))
        assertFalse(WifiIdentityHookPolicy.shouldSpoof(isPlaying = true, wifiIdentityEnabled = false))
        assertTrue(WifiIdentityHookPolicy.shouldSpoof(isPlaying = true, wifiIdentityEnabled = true))
    }

    @Test
    fun identityOnlyAppliesToSelectedTargetApps() {
        val identity = WifiIdentity(
            ssid = "CodexLab",
            bssid = "12:34:56:78:9A:BC",
            rssi = -42,
            targetApps = setOf("codex.wifiprobe")
        )

        assertTrue(identity.targets("codex.wifiprobe"))
        assertFalse(identity.targets("com.noobexon.xposedfakelocation"))
    }
}
