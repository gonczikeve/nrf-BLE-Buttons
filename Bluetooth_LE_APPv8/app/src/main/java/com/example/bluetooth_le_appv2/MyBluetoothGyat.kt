package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.ContentValues.TAG
import android.util.Log
import java.util.UUID

class MyBluetoothGyat(private val service: BluetoothLeService): BluetoothGattCallback() {

    private var connectionState = BluetoothLeService.STATE_DISCONNECTED

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            connectionState = BluetoothLeService.STATE_CONNECTED
            service.broadcastUpdate(BluetoothLeService.ACTION_GATT_CONNECTED)
            gatt?.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            connectionState = BluetoothLeService.STATE_DISCONNECTED
            service.broadcastUpdate(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }
    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            service.broadcastUpdate(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)

            val characteristic: BluetoothGattCharacteristic? = gatt?.getService(UUID.fromString("b390e1aa-cab5-4f75-bc6f-7b8a8d4cea88"))
                ?.getCharacteristic(UUID.fromString("b390e1ab-cab5-4f75-bc6f-7b8a8d4cea88"))
            val test = gatt?.setCharacteristicNotification(characteristic,true)
            Log.d("setCharacteristicNOtif","$test")
            val descriptor: BluetoothGattDescriptor = characteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val result = gatt.writeDescriptor(descriptor)
            Log.d("Final test","$result")

        } else {
            Log.d(TAG, "onServicesDiscovered received: $status")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            service.broadcastUpdate(BluetoothLeService.ACTION_DATA_AVAILABLE,characteristic)
            Log.d(TAG1,"onCharacteristicRead")
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        values: ByteArray
    ) {
        Log.d(TAG1,"onCharacteristicChanged")
        service.broadcastUpdate(BluetoothLeService.ACTION_DATA_AVAILABLE,characteristic)
    }


}