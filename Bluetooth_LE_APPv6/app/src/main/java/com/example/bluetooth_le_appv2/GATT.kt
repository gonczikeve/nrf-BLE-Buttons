package com.example.bluetooth_le_appv2

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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

private const val TAG = "BluetoothLeService"
private var connected: Boolean by mutableStateOf(false)


class BluetoothLeService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null

    private var connectionState = STATE_DISCONNECTED
    
    
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                Log.d(TAG,"State connected")
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                Log.d(TAG,"State disconnected")
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }
    }
    fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
        } else {
            Log.w(TAG, "onServicesDiscovered received: $status")
        }
    }


    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return bluetoothGatt?.
        services
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

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }


    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter:BluetoothAdapter = bluetoothManager.adapter
        val address: String = device.address
        Log.d(TAG, "Test LOG")
        bluetoothAdapter.let { adapter ->
            try {
                adapter.getRemoteDevice(address)
            } catch (exception: IllegalArgumentException) {
                Log.d(TAG, "Device not found with provided address.")
                return false
            }
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        bluetoothGatt?.let { gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }

}

class DeviceControlActivity : AppCompatActivity() {

    private var bluetoothService : BluetoothLeService? = null

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
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
    val leDevice:LeDeviceListAdapter = DataStorage.getAs("LeDevice")

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
//                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
//                    // Show all the supported services and characteristics on the user interface.
//                    displayGattServices(bluetoothService?.getSupportedGattServices())
//                }
            }
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
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun ConnectionScreen(device: BluetoothDevice){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(color = Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Name of the device: ${device.name}\nAddress of the device: ${device.address}",color = Color.Black)
            ConnectionStatusView(connected)

        }
    }


}

