package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.example.bluetooth_le_appv2.ui.theme.Bluetooth_LE_APPv2Theme
import java.util.Arrays


private const val TAG = "BluetoothLeService"
private var connected: Boolean by mutableStateOf(false)
private var btData: String? by mutableStateOf("Please Press the Buttons")
const val TAG1 = "ValueRead"


class BluetoothLeService : Service() {

    fun setup(){
        bluetoothGattCallback = MyBluetoothGyat(this)
    }

    private lateinit var  bluetoothGatt: BluetoothGatt
    private lateinit var bluetoothGattCallback: MyBluetoothGyat

    @SuppressLint("MissingPermission")
    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean
    ) {

        Log.d(TAG1,"setCharacteristicNotification")
    }

    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"
        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTED = 2
    }

    fun getSupportedGattServices(): List<BluetoothGattService>? {
        return bluetoothGatt.services
    }

    fun initialize(): Boolean {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter:BluetoothAdapter = bluetoothManager.adapter
        return true
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        val data: ByteArray? = characteristic.value
        if (data?.isNotEmpty() == true) {
            val hexString: String = data.joinToString(separator = " ") {
                String.format("%02X", it)
            }
            val stringedData = Arrays.toString(data)
            //intent.putExtra(EXTRA_DATA, "$stringedData\n$hexString")
            intent.putExtra(EXTRA_DATA, hexString)
        }
        Log.d(TAG1,"broadcastUpdate")

        sendBroadcast(intent)
    }

    @SuppressLint("MissingPermission")
    fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter:BluetoothAdapter = bluetoothManager.adapter
        val address: String = device.address
        bluetoothAdapter.let { adapter ->
            try {
                adapter.getRemoteDevice(address)
            } catch (exception: IllegalArgumentException) {
                Log.d(TAG, "Device not found with provided address.")
                return false
            }
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback,0,
                BluetoothDevice.PHY_LE_CODED or BluetoothDevice.PHY_LE_1M_MASK)
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt.close()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val test = characteristic.uuid
        Log.d("UUDI TEST","$test")
        val result = bluetoothGatt.readCharacteristic(characteristic)
        Log.d("UUDI TEST","$result")
    }

}

class DeviceControlActivity : AppCompatActivity() {


    private var bluetoothService : BluetoothLeService? = null
    private var mGattCharacteristic: MutableList<BluetoothGattCharacteristic> = mutableListOf()

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService!!.setup()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.d(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                bluetooth.connect(selectedDevice)
                Log.d("Frisk","Connected")
            }
        }
        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG,"Connection killed or lost")
            bluetoothService = null
        }
    }

    private val selectedDevice:BluetoothDevice = DataStorage.getAs("SelectedDevice")
    //In case I need the whole list of Bluetooth Devices
    //val leDevice:LeDeviceListAdapter = DataStorage.getAs("LeDevice")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            Bluetooth_LE_APPv2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionScreen(selectedDevice)
                    Log.d(TAG, "Started")
                    val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
                    bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                    Log.d(TAG, "$bluetoothService")

                }
            }
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null) {
            val result = bluetoothService!!.connect(selectedDevice)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG,"Received")
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    Log.d(TAG,"GYATT Connected")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    Log.d(TAG,"Gyatt disconnected")
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // Show all the supported services and characteristics on the user interface.
                    val listOfBlChara = displayGattServices(bluetoothService?.getSupportedGattServices())
                    listOfBlChara?.get(6)?.let { bluetoothService?.readCharacteristic(it) }
                    Log.d("DISCOVERY","Acquired !")
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    btData = intent.getStringExtra(EXTRA_DATA)
                    Log.d("FINAL DATA","$btData")
                }
            }
        }
    }


    private fun displayGattServices(gattServices: List<BluetoothGattService>?): MutableList<BluetoothGattCharacteristic>? {
        if (gattServices== null) return null

        var uuid: String?
        val unknownServiceString: String = resources.getString(R.string.unknown_service)
        val unknownCharaString: String = resources.getString(R.string.unknown_characteristic)
        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> =
            mutableListOf()
        mGattCharacteristic = mutableListOf()

        gattServices.forEach{gattService->
            val currentServiceData = HashMap<String,String>()
            uuid = gattService.uuid.toString()
            currentServiceData["ServiceName"] = SampleGattAttributes.lookup(uuid!!,unknownServiceString)
            currentServiceData["ServiceUUID"] = uuid!!
            gattServiceData +=currentServiceData

            val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
            val gattCharacteristics = gattService.characteristics
            val charas: MutableList<BluetoothGattCharacteristic> = mutableListOf()

            gattCharacteristics.forEach{ gattCharacteristic ->
                charas +=gattCharacteristic
                val currentCharaData: HashMap<String,String> = hashMapOf()
                uuid = gattCharacteristic.uuid.toString()
                currentCharaData["CharaName"] = SampleGattAttributes.lookup(uuid!!,unknownCharaString)
                currentCharaData["CharaUUid"] = uuid!!
                gattCharacteristicGroupData += currentCharaData
            }
            mGattCharacteristic += charas
            gattCharacteristicData += gattCharacteristicGroupData
        }

        Log.d("Service Data","n$gattServiceData")
        Log.d("CHARA and DATA","n$mGattCharacteristic\n\n$gattCharacteristicData")

        return mGattCharacteristic
    }

    object SampleGattAttributes {
        private val attributes = HashMap<String, String>()
        // Add your GATT service and characteristic UUIDs along with their corresponding names here
        init {
            // GATT Service UUIDs
            attributes["00001800-0000-1000-8000-00805f9b34fb"] = "Generic Access"
            attributes["00001801-0000-1000-8000-00805f9b34fb"] = "Generic Attribute"
            attributes["0000180F-0000-1000-8000-00805f9b34fb"] = "Battery"
            attributes["0000181C-0000-1000-8000-00805f9b34fb"] = "User Data"
            attributes["00001847-0000-1000-8000-00805f9b34fb"] = "Device Time"
            attributes["0000180D-0000-1000-8000-00805f9b34fb"] = "Heart Rate"

            // GATT Characteristic UUIDs
            attributes["00002a00-0000-1000-8000-00805f9b34fb"] = "Device Name"
            attributes["00002a01-0000-1000-8000-00805f9b34fb"] = "Appearance"
            attributes["00002A37-0000-1000-8000-00805f9b34fb"] = "Heart Rate Measurement"
            attributes["b390e1ab-cab5-4f75-bc6f-7b8a8d4cea88"] = "Button States"

            // Add more characteristics as needed
        }
        fun lookup(uuid: String, defaultName: String): String {
            val name = attributes[uuid]
            return name ?: defaultName
        }
    }


    @SuppressLint("UnrememberedMutableState")
    @Composable
    fun ConnectionStatusView(connected: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (connected) {
                Text("Connected", color = Color.Green)
            } else {
                Text("Disconnected", color = Color.Red)
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
            addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        }
    }

    @Composable
    fun CharaDisplay(mGattCharacteristics: MutableList<BluetoothGattCharacteristic>){
        Row {
            mGattCharacteristics.forEach {
                    mGattCharacteristic ->
                Text("$mGattCharacteristic")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun ConnectionScreen(device: BluetoothDevice){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(color = Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text("Name of the device: ${device.name}\nAddress of the device: ${device.address}\nButton Data: ${btData.toString()}",color = Color.Black)
            ConnectionStatusView(connected)
            CharaDisplay(mGattCharacteristics = mGattCharacteristic)
        }

    }

}

