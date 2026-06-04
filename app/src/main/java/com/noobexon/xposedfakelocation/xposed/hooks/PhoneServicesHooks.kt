package com.noobexon.xposedfakelocation.xposed.hooks

import android.os.Bundle
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.NeighboringCellInfo
import android.util.Log
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellularBaselineSnapshot
import com.noobexon.xposedfakelocation.xposed.utils.CellularBaselineReplay
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

class PhoneServicesHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[PhoneServicesHooks]"

    fun initHooks() {
        val phoneInterfaceManagerClass = findClass(
            classLoader,
            "com.android.phone.PhoneInterfaceManager"
        ) ?: return

        hookCellLocation(phoneInterfaceManagerClass)
        hookCellInfo(phoneInterfaceManagerClass)
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun hookCellLocation(phoneInterfaceManagerClass: Class<*>) {
        hookAll(phoneInterfaceManagerClass, "getCellLocation") { chain ->
            when (val replay = cellLocationHookResult(chain.args, chain.executable as? Method)) {
                PhoneServiceHookResult.Passthrough -> chain.proceed()
                is PhoneServiceHookResult.Spoofed -> {
                    module.log(Log.INFO, tag, "Replayed saved cell location while spoofing.")
                    replay.value
                }
            }
        }
    }

    private fun hookCellInfo(phoneInterfaceManagerClass: Class<*>) {
        hookAll(phoneInterfaceManagerClass, "getAllCellInfo") { chain ->
            when (val replay = allCellInfoHookResult(chain.args)) {
                PhoneServiceHookResult.Passthrough -> chain.proceed()
                is PhoneServiceHookResult.Spoofed -> {
                    module.log(Log.INFO, tag, "Replayed saved all cell info while spoofing (${replay.value.size} records).")
                    replay.value
                }
            }
        }

        hookAll(phoneInterfaceManagerClass, "getNeighboringCellInfo") { chain ->
            when (val replay = neighboringCellInfoHookResult(chain.args)) {
                PhoneServiceHookResult.Passthrough -> chain.proceed()
                is PhoneServiceHookResult.Spoofed -> {
                    module.log(Log.INFO, tag, "Replayed saved neighboring cell info while spoofing (${replay.value.size} records).")
                    replay.value
                }
            }
        }

        hookAll(phoneInterfaceManagerClass, "requestCellInfoUpdateInternal") { chain ->
            if (shouldSpoofPhoneServiceArgs(chain.args)) {
                module.log(Log.INFO, tag, "Blocked async cell info update while spoofing.")
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No method named $methodName on ${clazz.name}")
            return
        }

        var hooked = 0
        methods.forEach { method ->
            try {
                module.hook(method).intercept(hooker)
                hooked++
            } catch (e: Throwable) {
                module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${e.message}")
            }
        }

        if (hooked > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($hooked overloads).")
        }
    }

    private fun findClass(classLoader: ClassLoader, vararg names: String): Class<*>? {
        names.forEach { name ->
            try {
                return Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                // Keep trying ROM-specific framework names.
            }
        }
        module.log(Log.WARN, tag, "None of these classes were found: ${names.joinToString()}")
        return null
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

    internal companion object {
        internal fun cellLocationHookResult(
            args: List<Any?>?,
            method: Method?,
            cellularProvider: () -> CellularBaselineSnapshot? = ::activeCellularBaseline,
            replayProvider: (CellularBaselineSnapshot?, Method?) -> Any? = ::replayCellLocationValue
        ): PhoneServiceHookResult<Any?> {
            if (!shouldSpoofPhoneServiceArgs(args)) return PhoneServiceHookResult.Passthrough
            return PhoneServiceHookResult.Spoofed(replayProvider(cellularProvider(), method))
        }

        private fun replayCellLocationValue(cellular: CellularBaselineSnapshot?, method: Method?): Any? {
            return when {
                method?.returnType == Bundle::class.java -> CellularBaselineReplay.replayCellLocationBundle(cellular)
                method?.returnType?.let { CellIdentity::class.java.isAssignableFrom(it) } == true -> {
                    CellularBaselineReplay.replayCellIdentity(cellular)
                }
                method?.returnType?.let { CellLocation::class.java.isAssignableFrom(it) } == true -> {
                    CellularBaselineReplay.replayCellLocation(cellular)
                }
                method == null -> CellularBaselineReplay.replayCellLocation(cellular)
                else -> null
            }
        }

        internal fun allCellInfoHookResult(
            args: List<Any?>?,
            cellularProvider: () -> CellularBaselineSnapshot? = ::activeCellularBaseline
        ): PhoneServiceHookResult<List<CellInfo>> {
            if (!shouldSpoofPhoneServiceArgs(args)) return PhoneServiceHookResult.Passthrough
            return PhoneServiceHookResult.Spoofed(
                CellularBaselineReplay.replayAllCellInfo(cellularProvider())
            )
        }

        internal fun neighboringCellInfoHookResult(
            args: List<Any?>?,
            cellularProvider: () -> CellularBaselineSnapshot? = ::activeCellularBaseline
        ): PhoneServiceHookResult<List<NeighboringCellInfo>> {
            if (!shouldSpoofPhoneServiceArgs(args)) return PhoneServiceHookResult.Passthrough
            return PhoneServiceHookResult.Spoofed(CellularBaselineReplay.replayNeighboringCellInfo(cellularProvider()))
        }

        internal fun shouldSpoofPhoneServiceArgs(args: List<Any?>?): Boolean {
            if (PreferencesUtil.getIsPlaying() != true) return false
            return args?.asSequence()
                ?.mapNotNull(::extractPackageName)
                ?.any(LocationUtil::shouldSpoofPackage) == true
        }

        private fun activeCellularBaseline(): CellularBaselineSnapshot? {
            return PreferencesUtil.getSignalBaseline()?.cellular
        }

        private fun extractPackageName(value: Any?): String? {
            if (value is String) return value.takeIf { "." in it && !it.startsWith("android.") }
            return null
        }
    }
}

internal sealed class PhoneServiceHookResult<out T> {
    data object Passthrough : PhoneServiceHookResult<Nothing>()
    data class Spoofed<out T>(val value: T) : PhoneServiceHookResult<T>()
}
