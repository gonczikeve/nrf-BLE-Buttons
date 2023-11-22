package com.example.bluetooth_le_appv2


import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetooth_le_appv2.ui.theme.Bluetooth_LE_APPv2Theme
import com.example.bluetooth_le_appv2.BleAppViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Bluetooth_LE_APPv2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BluetoothScreen(leDeviceListAdapter) { scanLeDevice() }
                    try{
                        connectedBt()
                        getScanPermission()

                    }catch(e:Exception){
                        Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private var btPermission = false
    private var scanning = false
    private val SCAN_PERIOD: Long = 10000
    private val REQUEST_BLUETOOTH_PERMISSION = 1

    private val leDeviceListAdapter = LeDeviceListAdapter(this)


    private fun getScanPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_BLUETOOTH_PERMISSION
            )
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            leDeviceListAdapter.addDevice(result.device)
        }
    }


    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()



    fun createScanFilterList(): List<ScanFilter> {
        val scanFilterList = mutableListOf<ScanFilter>()

        // Filter by device name
        val deviceNameFilter = ScanFilter.Builder()
            .setDeviceName("YourDeviceName")
            .build()
        scanFilterList.add(deviceNameFilter)

        // Filter by device address
        val deviceAddressFilter = ScanFilter.Builder()
            .setDeviceAddress("00:11:22:33:44:55")
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

    @SuppressLint("MissingPermission")
    fun scanLeDevice() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter:BluetoothAdapter? = bluetoothManager.adapter
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Handler(Looper.getMainLooper()).postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(null,scanSettings,leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    fun connectedBt() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter:BluetoothAdapter? = bluetoothManager.adapter
        if(bluetoothAdapter==null){
            Toast.makeText(this,"Device does not support Bluetooth",Toast.LENGTH_LONG).show()
        }else{
            if(VERSION.SDK_INT >= Build.VERSION_CODES.S){
                blueToothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }else{
                blueToothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
    }

    private val blueToothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){
            isGranted:Boolean ->
        if(isGranted){
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter:BluetoothAdapter? = bluetoothManager.adapter
            btPermission = true
            if(bluetoothAdapter?.isEnabled == false){
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enableBtIntent)
            }else{
                btConnect()
            }

        }else{
            btPermission = false
        }
    }

    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){
            result: ActivityResult ->
        if(result.resultCode == RESULT_OK){
            btConnect()
        }
    }
    private fun btConnect(){
        Toast.makeText(this,"Bluetooth Connected successfully",Toast.LENGTH_LONG).show()
    }

    companion object {
        const val UUID_DEFAULT = "-0000-1000-8000-00805F9B34FB"
    }

}


@Composable
fun BluetoothScreen(leDeviceListAdapter: LeDeviceListAdapter,scanLeDevice: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Blue),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        TitleImage()
        BluetoothScanButton(scanLeDevice)
        leDeviceListAdapter.DeviceList()
    }
}

@Composable
fun BluetoothScanButton(scanLeDevice:()-> Unit) {
    var scanning by remember { mutableStateOf(false) }

    Button(
        onClick = {
            scanLeDevice()
            scanning = !scanning},
        modifier = Modifier.padding(16.dp)
    ) {
        if (scanning) {
            Text("Stop Scanning")
        } else {
            Text("Start Scanning")
        }
    }
}

@Composable
fun TitleImage(){
    Box(
        modifier = Modifier
            .size(100.dp)
            .padding()
    ){
        Image(
            painter = painterResource(id = R.drawable.bluetooth_low_energy_ble),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}
