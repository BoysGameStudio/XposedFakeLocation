package com.noobexon.xposedfakelocation.testutil

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(this) { LinkedHashMap(values) }

    override fun getString(key: String?, defValue: String?): String? = value(key) as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val stored = value(key) as? Set<String> ?: return defValues
        return stored.toMutableSet()
    }

    override fun getInt(key: String?, defValue: Int): Int = value(key) as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = value(key) as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = value(key) as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = value(key) as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = synchronized(this) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(this) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(this) { listeners -= listener }
    }

    private fun value(key: String?): Any? = synchronized(this) { values[key] }

    private fun notifyChanged(changedKeys: Set<String>) {
        if (changedKeys.isEmpty()) return
        val listenerSnapshot = synchronized(this) { listeners.toList() }
        for (changedKey in changedKeys) {
            listenerSnapshot.forEach { listener ->
                listener.onSharedPreferenceChanged(this, changedKey)
            }
        }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var shouldClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) changes[key] = Removed
        }

        override fun clear(): SharedPreferences.Editor = apply {
            shouldClear = true
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val changedKeys = linkedSetOf<String>()
            synchronized(this@FakeSharedPreferences) {
                if (shouldClear) {
                    changedKeys += values.keys
                    values.clear()
                }
                changes.forEach { (key, value) ->
                    if (value === Removed || value == null) {
                        if (values.containsKey(key)) changedKeys += key
                        values.remove(key)
                    } else {
                        if (values[key] != value) changedKeys += key
                        values[key] = value
                    }
                }
            }
            notifyChanged(changedKeys)
        }
    }

    private object Removed
}
