package com.example.bluetooth_le_appv2

object DataStorage {
    private val map = HashMap<String, Any?>()

    fun put(key: String, obj: Any?) {
        map[key] = obj;
    }

    fun <T> getAs(key: String): T {
        return map[key] as T
    }

    fun get(key: String): Any? {
        return map[key]
    }

    fun size(): Int {
        return map.size;
    }

}