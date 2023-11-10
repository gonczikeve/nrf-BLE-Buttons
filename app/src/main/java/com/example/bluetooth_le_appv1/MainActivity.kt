package com.example.bluetooth_le_appv1


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bluetooth_le_appv1.Permissions.BluetoothHelper
import com.example.bluetooth_le_appv1.ui.theme.Bluetooth_LE_APPv1Theme

class MainActivity : ComponentActivity() {

    private val bluetoothHelper by lazy { BluetoothHelper(this,this) }
    private var btPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Bluetooth_LE_APPv1Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BluetoothScreen(scanBt())
                }
            }
        }
    }
     fun scanBt() {
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
                    btScan()
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
                btScan()
            }
    }
    private fun btScan(){
        Toast.makeText(this,"Bluetooth Connected successfully",Toast.LENGTH_LONG).show()
    }
}


@Composable
fun BluetoothScreen(bluetoothHelper: Unit) {
    Column(
        modifier = Modifier
                .fillMaxSize()
                .background(Color.Blue),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        TitleImage()
        BluetoothScanButton()
        NearbyDevicesList()
    }
}

@Composable
fun BluetoothScanButton() {
    var scanning by remember { mutableStateOf(false) }

    Button(
        onClick = {
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
fun NearbyDevicesList() {
    // Dummy list of nearby devices
    val nearbyDevices = listOf(
        "Device 1",
        "Device 2",
        "Device 3",
        "Device 4"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Blue)
            .padding(16.dp)
    ) {
        items(nearbyDevices) { device ->
            DeviceListItem(device)
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

@Composable
fun DeviceListItem(device: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Text(
            text = device,
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
        )
    }
}



//@Preview(showBackground = true)
//@Composable
//
//fun JetpackPreview() {
//    Bluetooth_LE_APPv1Theme {
//        BluetoothScreen()
//    }
//}
