package com.example.bluetooth_le_appv2

import android.util.Log
import android.util.SparseArray
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator

class Manufacturer(arr: SparseArray<ByteArray>) {


    val companyName: String
    val companyId: Int = getManufacturerId(arr)
    var data: ByteArray = getManufacturerData(arr)

    init {
        companyName = getCompanyName(companyId = companyId)

    }

    companion object {
        var companies = hashMapOf(
            76 to "Apple", 6 to "Microsoft", 158 to "Bose",
            117 to "Samsung", 1 to "Nokia", 8 to "Motorola",
            224 to "Google", 301 to "Sony", 0 to "Ericsson",
            220 to "OralP&G"
        )

    }

    private fun getCompanyName(companyId: Int): String {
        val name = companies[companyId]
        if (name == null) {
            return "Unknown $companyId"
        }
        return name
    }

    fun getManufacturerId(arr: SparseArray<ByteArray>): Int {
        Log.w("SizeOfSparseArray", "Empty Manufacturer's data")
        val iter = arr.keyIterator()

        return if (iter.hasNext()) {
            iter.next()
        } else {
            -1
        }
    }

    fun getManufacturerData(arr: SparseArray<ByteArray>): ByteArray {
        Log.w("SizeOfSparseArray", "Empty Manufacturer's data")
        val iter = arr.valueIterator()
        if (iter.hasNext()) {
            return iter.next()
        } else {
            return ByteArray(0)

        }
    }
}