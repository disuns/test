package com.example.test

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.test.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.schedule


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var mBluetoothAdapter: BluetoothAdapter

    lateinit var mDevice: BluetoothDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH
                ),
                1
            )
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }


        binding.btnScanBLE.setOnClickListener {
            val filters: MutableList<ScanFilter> = ArrayList()
            val scanFilter: ScanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString("00001809-0000-1000-8000-00805F9B34FB")))
                .build()
            filters.add(scanFilter)
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()

            mBluetoothAdapter.bluetoothLeScanner.startScan(filters, settings, BLEScanCallback)
            Timer("SettingUp", false).schedule(3000) { stopScan() } }

        binding.btnReadData.setOnClickListener {
            bleGatt = mDevice.connectGatt(applicationContext, false, gattClientCallback)
            setNotifications()
            readData()
        }
    }

    @SuppressLint("MissingPermission")
    private val BLEScanCallback: ScanCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            mDevice = result.device
            Log.e("", "onScanResult device name: " + result.device.name)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                Log.e("", "onBatchScanResults device name: " + result.device.name)
            }
        }

        override fun onScanFailed(_error: Int) {
            Log.e("", "BLE scan failed with code $_error")
        }
    }

    private var bleGatt: BluetoothGatt? = null
    private val gattClientCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer()
                return
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // update the connection status message

                Log.d("", "Connected to the GATT server")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)


            // check if the discovery failed
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("", "Device service discovery failed, status: $status")
                return
            }

            // log for successful discovery
            Log.d("", "Services discovery is successful")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            //Log.d(TAG, "characteristic changed: " + characteristic.uuid.toString())
            readCharacteristic(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("", "Characteristic written successfully")
            } else {
                Log.e("", "Characteristic write unsuccessful, status: $status")
                disconnectGattServer()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("", "Characteristic read successfully")
                readCharacteristic(characteristic)
            } else {
                Log.e("", "Characteristic read unsuccessful, status: $status")
                // Trying to read from the Time Characteristic? It doesnt have the property or permissions
                // set to allow this. Normally this would be an error and you would want to:
                // disconnectGattServer();
            }
        }

        /**
         * Log the value of the characteristic
         * @param characteristic
         */
        private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {

            val msg = characteristic.getStringValue(0)
            Log.e("", msg)
        }


    }

    /**
     * Disconnect Gatt Server
     */
    @SuppressLint("MissingPermission")
    fun disconnectGattServer() {
        Log.d("", "Closing Gatt connection")
        // disconnect and close the gatt
        if (bleGatt != null) {
            bleGatt!!.disconnect()
            bleGatt!!.close()
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun stopScan() {
        mBluetoothAdapter.bluetoothLeScanner?.stopScan(BLEScanCallback)
    }


    lateinit var charac : BluetoothGattCharacteristic
    lateinit var desc : BluetoothGattDescriptor
    fun setNotifications() {
        val btGattServicesList: List<BluetoothGattService> = bleGatt!!.services
        for(b in btGattServicesList.indices){
            btGattServicesList[b].characteristics
        }
        btGattServicesList[0].characteristics
        val btGattCharList = btGattServicesList[2].characteristics
        val btDescList = btGattCharList[1].descriptors
        val characteristic = btGattCharList[1]
        val descriptor = btDescList[0]
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleGatt?.setCharacteristicNotification(characteristic, true)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        charac = characteristic
        desc = descriptor
    }

    fun readData() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bleGatt?.writeDescriptor(desc)
        bleGatt?.readCharacteristic(charac)
        bleGatt?.readDescriptor(desc)
    }
}