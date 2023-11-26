package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.bluetooth_le_appv2.ui.theme.Bluetooth_LE_APPv2Theme

class InfoActivity: AppCompatActivity() {
    private var scanning:Boolean = false
    val SCAN_PERIOD = 5000L

    private val deviceAddress: String = DataStorage.getAs("MacAddress")


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            Bluetooth_LE_APPv2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    scanLeDevice()
                    BluetoothScreen()


                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanLeDevice() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            scanning = true
            bluetoothLeScanner?.startScan(createScanFilterList(),scanSettings,leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val sparseArray = result.scanRecord?.manufacturerSpecificData
                val manufacturer = Manufacturer(sparseArray!!)
                info = "${manufacturer.companyName}:${manufacturer.data.contentToString()}"
                advertising = String(manufacturer.data)
                Log.d("Calling Callback",info)
            }
        }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()


    private fun createScanFilterList(): List<ScanFilter> {
        val scanFilterList = mutableListOf<ScanFilter>()

        // Filter by device name
//        val deviceNameFilter = ScanFilter.Builder()
//            .setDeviceName("YourDeviceName")
//            .build()
//        scanFilterList.add(deviceNameFilter)

        // Filter by device address
        val deviceAddressFilter = ScanFilter.Builder()
            .setDeviceAddress(deviceAddress)
            .build()
        scanFilterList.add(deviceAddressFilter)

        // Filter by service UUID
        /*
        val serviceUuidFilter = ScanFilter.Builder()
            .setServiceUuid(UUID_DEFAULT)
            .build()
        scanFilterList.add(serviceUuidFilter)
        */
        // Add more filters as needed

        return scanFilterList
    }

    var info by mutableStateOf("")
    var advertising by mutableStateOf("")
    @Composable
    fun BluetoothScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(info,color = Color.Black)
            Text(deviceAddress,color = Color.Black)
            Text(advertising,color= Color.Black)
        }
    }


}
