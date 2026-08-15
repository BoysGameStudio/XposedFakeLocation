package com.noobexon.xposedfakelocation.xposed.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSpoofPolicyTest {
    private val targetPackages = setOf("com.example.target")
    private val proxyPackages = setOf("com.android.location.fused", "com.google.android.gms")

    @Test
    fun directTargetPackage_isSpoofTarget() {
        val target = LocationSpoofPolicy.findSpoofTarget(
            packageNames = setOf("com.example.target"),
            targetPackages = targetPackages,
            proxyPackages = proxyPackages
        )

        assertEquals("direct target package must be spoofed", "com.example.target", target)
        assertTrue(
            "direct target package must enable spoofing",
            LocationSpoofPolicy.shouldSpoof(setOf("com.example.target"), targetPackages, proxyPackages)
        )
    }

    @Test
    fun proxyOnlyPackage_isNotSpoofTarget() {
        val target = LocationSpoofPolicy.findSpoofTarget(
            packageNames = setOf("com.google.android.gms"),
            targetPackages = targetPackages,
            proxyPackages = proxyPackages
        )

        assertEquals("proxy-only attribution must not spoof all GMS traffic", null, target)
        assertFalse(
            "proxy-only attribution must pass through real location",
            LocationSpoofPolicy.shouldSpoof(setOf("com.google.android.gms"), targetPackages, proxyPackages)
        )
    }

    @Test
    fun proxyWithNestedTarget_isSpoofTarget() {
        val target = LocationSpoofPolicy.findSpoofTarget(
            packageNames = setOf("com.google.android.gms", "com.example.target"),
            targetPackages = targetPackages,
            proxyPackages = proxyPackages
        )

        assertEquals("proxy attribution with nested target must spoof the target", "com.example.target", target)
        assertTrue(
            "proxy attribution with nested target must enable spoofing",
            LocationSpoofPolicy.shouldSpoof(
                setOf("com.google.android.gms", "com.example.target"),
                targetPackages,
                proxyPackages
            )
        )
    }

    @Test
    fun unrelatedPackage_isNotSpoofTarget() {
        val target = LocationSpoofPolicy.findSpoofTarget(
            packageNames = setOf("com.example.other"),
            targetPackages = targetPackages,
            proxyPackages = proxyPackages
        )

        assertEquals("unrelated package must not spoof", null, target)
        assertFalse(
            "unrelated package must pass through real location",
            LocationSpoofPolicy.shouldSpoof(setOf("com.example.other"), targetPackages, proxyPackages)
        )
    }

    @Test
    fun selectedProxyPackage_isNotSpoofTarget() {
        val target = LocationSpoofPolicy.findSpoofTarget(
            packageNames = setOf("com.google.android.gms"),
            targetPackages = targetPackages + "com.google.android.gms",
            proxyPackages = proxyPackages
        )

        assertEquals("selected proxy package must not spoof all proxy traffic", null, target)
        assertFalse(
            "selected proxy package must remain pass-through without nested target",
            LocationSpoofPolicy.shouldSpoof(
                setOf("com.google.android.gms"),
                targetPackages + "com.google.android.gms",
                proxyPackages
            )
        )
    }
}
