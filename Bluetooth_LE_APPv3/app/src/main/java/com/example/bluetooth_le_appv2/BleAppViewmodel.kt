package com.example.bluetooth_le_appv2

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BleAppViewModel: ViewModel() {
    private val _bleDevices = MutableLiveData<List<BluetoothDevice>>()
    val bleDevices: LiveData<List<BluetoothDevice>> get() = _bleDevices

    private val _selectedDevice = MutableLiveData<BluetoothDevice>()
    val selectedDevice: LiveData<BluetoothDevice> get() = _selectedDevice


}