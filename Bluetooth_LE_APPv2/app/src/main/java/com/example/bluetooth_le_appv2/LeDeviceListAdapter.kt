package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


class LeDeviceListAdapter {

    private var devices: List<BluetoothDevice> by mutableStateOf(emptyList())

    fun addDevice(device: BluetoothDevice) {
        devices = devices + device
    }

    @Composable
    fun DeviceList() {
        LazyColumn {
            items(devices) { device ->
                DeviceItem(device = device)
            }
        }
    }
    @SuppressLint("MissingPermission")
    @Composable
    private fun DeviceItem(device: BluetoothDevice) {
        // Compose UI for each device item
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Text(
                text = device.name ?: "Unknown Device",
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
            )
        }

    }
}