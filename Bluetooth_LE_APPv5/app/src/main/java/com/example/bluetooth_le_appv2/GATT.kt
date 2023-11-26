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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.bluetooth_le_appv2.ui.theme.Bluetooth_LE_APPv2Theme

private const val TAG = "BluetoothLeService"

class BluetoothLeService(private val context: Context) : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private val  bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
    private var connectionState = STATE_DISCONNECTED


    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }
    }


    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }

    fun initialize(): Boolean {
        val bluetoothAdapter:BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
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
        val address: String = device.address
        val bluetoothAdapter:BluetoothAdapter? = bluetoothManager.adapter
        bluetoothAdapter?.let { adapter ->
            try {
                adapter.getRemoteDevice(address)
            } catch (exception: IllegalArgumentException) {
                Log.d(TAG, "Device not found with provided address.")
                return false
            }
            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback)
        } ?: run {
            Log.d(TAG, "BluetoothAdapter not initialized")
            return false
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
    //private val device: BluetoothDevice = intent.getParcelableExtra("EXTRA_DEVICE") ?: error("No BluetoothDevice found")
    //private val device = LeDeviceListAdapter(context).getDevice(deviceIndex)


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
            bluetoothService = null
        }
    }

    private val selectedDevice:BluetoothDevice = DataStorage.getAs("SelectedDevice")
    val leDevice:LeDeviceListAdapter = DataStorage.getAs("LeDevice")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            Bluetooth_LE_APPv2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GattScreen(selectedDevice)
                    Log.d(TAG, "Started")
                    val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
                    bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                    Log.d(TAG, "Service Bound")
                }
            }
        }
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG,"Received")
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
//                    connected = true
//                    updateConnectionState(R.string.GATT_Connected)
                    Log.d(TAG,"GYATT Connected")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    //connected = false
                    //updateConnectionState(R.string.GATT_Disconnected)
                    Log.d(TAG,"Gyatt disconnected")
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        if (bluetoothService != null) {
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
            val result = bluetoothService!!.connect(selectedDevice)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun GattScreen(device: BluetoothDevice){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Name of the device: ${device.name}\nAddress of the device: ${device.address}",color = Color.Black)
        }
    }

}

