package com.noobexon.xposedfakelocation.xposed.utils

import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.NeighboringCellInfo
import android.telephony.TelephonyManager
import com.noobexon.xposedfakelocation.data.model.ShanghaiCellTower
import java.lang.reflect.Constructor

object CellTowerUtil {
    private const val CELL_UNAVAILABLE = Int.MAX_VALUE
    private const val CONNECTION_PRIMARY_SERVING = 1

    fun createCellInfo(towers: List<ShanghaiCellTower>): List<CellInfo> {
        return towers.mapNotNull { tower -> createCellInfo(tower) }
    }

    fun createNeighboringCellInfo(towers: List<ShanghaiCellTower>): List<NeighboringCellInfo> {
        return towers.mapNotNull { tower ->
            val radioType = when (tower.radio) {
                "GSM" -> TelephonyManager.NETWORK_TYPE_GPRS
                "UMTS", "WCDMA" -> TelephonyManager.NETWORK_TYPE_UMTS
                else -> return@mapNotNull null
            }
            runCatching {
                NeighboringCellInfo(signalAsu(tower), legacyLocation(tower), radioType)
            }.getOrNull()
        }
    }

    private fun createCellInfo(tower: ShanghaiCellTower): CellInfo? {
        return when (tower.radio) {
            "LTE" -> createLteCellInfo(tower)
            "GSM" -> createGsmCellInfo(tower)
            "UMTS", "WCDMA" -> createWcdmaCellInfo(tower)
            else -> null
        }
    }

    private fun createLteCellInfo(tower: ShanghaiCellTower): CellInfo? = runCatching {
        val identityClass = Class.forName("android.telephony.CellIdentityLte")
        val signalClass = Class.forName("android.telephony.CellSignalStrengthLte")
        val configClass = Class.forName("android.telephony.CellConfigLte")
        val infoClass = Class.forName("android.telephony.CellInfoLte")

        val identity = identityClass.constructor(
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            IntArray::class.java,
            Integer.TYPE,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Collection::class.java,
            Class.forName("android.telephony.ClosedSubscriberGroupInfo")
        ).newInstance(
            tower.cid,
            tower.pscOrPci,
            tower.lac,
            CELL_UNAVAILABLE,
            intArrayOf(),
            CELL_UNAVAILABLE,
            tower.mcc.toString(),
            tower.mnc.toString(),
            "",
            "",
            emptyList<String>(),
            null
        )

        val signal = signalClass.constructor(
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE
        ).newInstance(
            rssiDbm(tower),
            rssiDbm(tower),
            CELL_UNAVAILABLE,
            CELL_UNAVAILABLE,
            CELL_UNAVAILABLE,
            CELL_UNAVAILABLE
        )

        infoClass.constructor(
            Integer.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Long.TYPE,
            identityClass,
            signalClass,
            configClass
        ).newInstance(
            CONNECTION_PRIMARY_SERVING,
            true,
            SystemClock.elapsedRealtimeNanos(),
            identity,
            signal,
            configClass.constructor().newInstance()
        ) as? CellInfo
    }.getOrNull()

    private fun createGsmCellInfo(tower: ShanghaiCellTower): CellInfo? = runCatching {
        val identityClass = Class.forName("android.telephony.CellIdentityGsm")
        val signalClass = Class.forName("android.telephony.CellSignalStrengthGsm")
        val infoClass = Class.forName("android.telephony.CellInfoGsm")

        val identity = identityClass.constructor(
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Collection::class.java
        ).newInstance(
            tower.lac,
            tower.cid,
            CELL_UNAVAILABLE,
            CELL_UNAVAILABLE,
            tower.mcc.toString(),
            tower.mnc.toString(),
            "",
            "",
            emptyList<String>()
        )

        val signal = signalClass.constructor(Integer.TYPE, Integer.TYPE, Integer.TYPE)
            .newInstance(signalAsu(tower), CELL_UNAVAILABLE, CELL_UNAVAILABLE)

        infoClass.constructor(Integer.TYPE, java.lang.Boolean.TYPE, java.lang.Long.TYPE, identityClass, signalClass)
            .newInstance(CONNECTION_PRIMARY_SERVING, true, SystemClock.elapsedRealtimeNanos(), identity, signal) as? CellInfo
    }.getOrNull()

    private fun createWcdmaCellInfo(tower: ShanghaiCellTower): CellInfo? = runCatching {
        val identityClass = Class.forName("android.telephony.CellIdentityWcdma")
        val signalClass = Class.forName("android.telephony.CellSignalStrengthWcdma")
        val infoClass = Class.forName("android.telephony.CellInfoWcdma")

        val identity = identityClass.constructor(
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Collection::class.java,
            Class.forName("android.telephony.ClosedSubscriberGroupInfo")
        ).newInstance(
            tower.lac,
            tower.cid,
            tower.pscOrPci,
            CELL_UNAVAILABLE,
            tower.mcc.toString(),
            tower.mnc.toString(),
            "",
            "",
            emptyList<String>(),
            null
        )

        val signal = signalClass.constructor(Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
            .newInstance(signalAsu(tower), CELL_UNAVAILABLE, rssiDbm(tower), CELL_UNAVAILABLE)

        infoClass.constructor(Integer.TYPE, java.lang.Boolean.TYPE, java.lang.Long.TYPE, identityClass, signalClass)
            .newInstance(CONNECTION_PRIMARY_SERVING, true, SystemClock.elapsedRealtimeNanos(), identity, signal) as? CellInfo
    }.getOrNull()

    private fun Class<*>.constructor(vararg parameterTypes: Class<*>): Constructor<*> {
        return getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
    }

    private fun legacyLocation(tower: ShanghaiCellTower): String {
        val lac = tower.lac.coerceIn(0, 0xffff).toString(16).padStart(4, '0')
        val cid = tower.cid.coerceIn(0, 0xffff).toString(16).padStart(4, '0')
        return lac + cid
    }

    private fun signalAsu(tower: ShanghaiCellTower): Int {
        val dbm = rssiDbm(tower)
        return ((dbm + 113) / 2).coerceIn(0, 31)
    }

    private fun rssiDbm(tower: ShanghaiCellTower): Int {
        return tower.averageSignal ?: -85
    }
}
