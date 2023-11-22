package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


class LeDeviceListAdapter(val context: Context) {

    private var devices: List<BluetoothDevice> by mutableStateOf(emptyList())
    val dropDownMenu: List<DropDownOption> = listOf(DropDownOption.CONNECT,DropDownOption.DISCONNECT,
        DropDownOption.INFO)

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

    enum class DropDownOption{
        CONNECT,
        DISCONNECT,
        INFO
    }

    private fun onItemClick(dropDownMenu: DropDownOption): Unit {
        
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun DeviceItem(device: BluetoothDevice) {
        // Compose UI for each device item
        var expanded by rememberSaveable{ mutableStateOf(false)}
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable {
                    expanded = true
                    Toast
                        .makeText(context, "Button Clicked", Toast.LENGTH_LONG)
                        .show()
                },
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
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (index in dropDownMenu) {
                    var txt = "${index}"
                    Button(onClick = { Toast.makeText(context, txt, Toast.LENGTH_LONG).show() }) {
                        Text(txt)
                    }
                }
            }
        }

    }

}