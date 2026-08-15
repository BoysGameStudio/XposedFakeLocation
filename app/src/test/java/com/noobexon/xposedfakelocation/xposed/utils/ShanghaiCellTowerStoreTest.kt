package com.noobexon.xposedfakelocation.xposed.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShanghaiCellTowerStoreTest {
    @Test
    fun fromCsv_parsesOpenCellIdRowsAndSkipsInvalidRows() {
        val store = ShanghaiCellTowerStore.fromCsv(
            """
            radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal
            LTE,460,0,12001,345678,384,121.4737,31.2304,600,21,1,0,0,-82
            bad,row
            GSM,460,1,22001,45678,0,121.4800,31.2400,800,7,1,0,0,-75
            """.trimIndent().byteInputStream()
        )

        val towers = store.findNearest(latitude = 31.2304, longitude = 121.4737, limit = 10)

        assertEquals("valid Shanghai rows should be parsed", 2, towers.size)
        assertEquals("radio should be parsed", "LTE", towers[0].radio)
        assertEquals("mcc should be parsed", 460, towers[0].mcc)
        assertEquals("net should map to mnc", 0, towers[0].mnc)
        assertEquals("area should map to lac/tac", 12001, towers[0].lac)
        assertEquals("cell should map to cid", 345678, towers[0].cid)
        assertEquals("unit should map to psc/pci", 384, towers[0].pscOrPci)
        assertEquals("averageSignal should be parsed", -82, towers[0].averageSignal)
    }

    @Test
    fun findNearest_sortsByDistanceAndLimitsResults() {
        val store = ShanghaiCellTowerStore.fromCsv(
            """
            radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal
            LTE,460,0,1,100,10,121.4738,31.2305,600,10,1,0,0,-80
            LTE,460,0,1,200,20,121.6000,31.3000,600,10,1,0,0,-85
            LTE,460,0,1,300,30,121.4739,31.2306,600,10,1,0,0,-90
            """.trimIndent().byteInputStream()
        )

        val towers = store.findNearest(latitude = 31.2304, longitude = 121.4737, limit = 2)

        assertEquals("query should honor limit", 2, towers.size)
        assertEquals("nearest tower should be first", 100, towers[0].cid)
        assertEquals("second nearest tower should follow", 300, towers[1].cid)
    }

    @Test
    fun findNearest_returnsEmptyOutsideShanghai() {
        val store = ShanghaiCellTowerStore.fromCsv(
            """
            radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal
            LTE,460,0,12001,345678,384,121.4737,31.2304,600,21,1,0,0,-82
            """.trimIndent().byteInputStream()
        )

        val towers = store.findNearest(latitude = 39.9042, longitude = 116.4074, limit = 8)

        assertTrue("outside Shanghai should not return Shanghai towers", towers.isEmpty())
    }

    @Test
    fun fromCsv_emptyInputReturnsEmptyStore() {
        val store = ShanghaiCellTowerStore.fromCsv("".byteInputStream())

        assertTrue("empty input should return no towers", store.findNearest(31.2304, 121.4737).isEmpty())
    }

    @Test
    fun fromFile_missingFileReturnsEmptyStore() {
        val store = ShanghaiCellTowerStore.fromFile("/sdcard/XposedFakeLocation/missing-shanghai-cells.csv")

        assertTrue("missing external file should safely return no towers", store.findNearest(31.2304, 121.4737).isEmpty())
    }
}
