package android.content.pm

class ParceledListSlice<T>(private val list: List<T>) {
    fun getList(): List<T> = list

    companion object {
        @JvmStatic
        fun <T> emptyList(): ParceledListSlice<T> = ParceledListSlice(kotlin.collections.emptyList())
    }
}
