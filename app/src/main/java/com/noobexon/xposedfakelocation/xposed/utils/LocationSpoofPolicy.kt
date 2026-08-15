package com.noobexon.xposedfakelocation.xposed.utils

object LocationSpoofPolicy {
    fun shouldSpoof(
        packageNames: Collection<String>,
        targetPackages: Set<String>,
        proxyPackages: Set<String>
    ): Boolean {
        return findSpoofTarget(packageNames, targetPackages, proxyPackages) != null
    }

    fun findSpoofTarget(
        packageNames: Collection<String>,
        targetPackages: Set<String>,
        proxyPackages: Set<String>
    ): String? {
        val effectiveTargets = targetPackages - proxyPackages
        return packageNames.firstOrNull { it in effectiveTargets }
    }
}
