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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.test.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.schedule
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var mBluetoothAdapter: BluetoothAdapter
    private val mBluetoothManager : BluetoothManager by lazy { applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private var mBleGatt: BluetoothGatt? = null
    lateinit var mDevice: BluetoothDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mBluetoothAdapter = mBluetoothManager.adapter

        permissionRequest()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        binding.btnConnect.setOnClickListener {
            val filters: MutableList<ScanFilter> = ArrayList()
            val scanFilter: ScanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("00001809-0000-1000-8000-00805F9B34FB"))).build()
            filters.add(scanFilter)
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()

            mBluetoothAdapter.bluetoothLeScanner.startScan(filters, settings, BLEScanCallback)
            Timer("SettingUp", false).schedule(3000) { stopScan() } }
    }

    @SuppressLint("MissingPermission")
    private val BLEScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            mDevice = result.device
            Log.d("", "onScanResult device name: " + result.device.name)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                Log.d("", "onBatchScanResults device name: " + result.device.name)
            }
        }

        override fun onScanFailed(_error: Int) {
            Log.d("", "BLE scan failed with code $_error")
        }
    }

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
            enableNotify(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d("", "characteristic changed: " + characteristic.uuid.toString())
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
        }

        /**
         * Log the value of the characteristic
         * @param characteristic
         */
        private fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
            Log.e("", getParsingTemperature(characteristic.value))
            runOnUiThread {
                binding.tvTemperature.text = getParsingTemperature(characteristic.value)
            }
        }


    }

    /**
     * Disconnect Gatt Server
     */
    @SuppressLint("MissingPermission")
    fun disconnectGattServer() {
        Log.d("", "Closing Gatt connection")
        // disconnect and close the gatt
        if (mBleGatt != null) {
            mBleGatt!!.disconnect()
            mBleGatt!!.close()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mBluetoothAdapter.bluetoothLeScanner?.stopScan(BLEScanCallback)
        mBleGatt = mDevice.connectGatt(applicationContext, false, gattClientCallback)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt?) {
        val service = gatt?.getService(UUID.fromString("00001809-0000-1000-8000-00805F9B34FB"))
        val characteristic = service!!.getCharacteristic(UUID.fromString("00002a1c-0000-1000-8000-00805F9B34FB"))

        gatt.setCharacteristicNotification(characteristic, true)

        characteristic?.let {
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))

            descriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(descriptor)
            gatt.readCharacteristic(characteristic)
            gatt.readDescriptor(descriptor)
        }

    }

    /**
     * 장비에서 수신한 체온값을 변환
     * @param byteArray 체온 배열
     * @return String 체온값
     */
    private fun getParsingTemperature(byteArray: ByteArray?): String {
        if(byteArray == null)
            return "0.0"

        if(byteArray.size > 5) {
            //sb.append(String.format("%02x ", byteArray[idx] and 0xff.toByte()))
            val sb = StringBuffer()
            sb.append(String.format("%02x", byteArray[3] and 0xff.toByte()))
            sb.append(String.format("%02x", byteArray[2] and 0xff.toByte()))
            sb.append(String.format("%02x", byteArray[1] and 0xff.toByte()))

            val temperature = Integer.parseInt(sb.toString(), 16)

            val value: Float = if(String.format("%02x", byteArray[4] and 0xff.toByte()) == "ff") {
                temperature.toFloat() / 10.toFloat()
            } else
                temperature.toFloat() / 100.0f


            return value.toString()
        }
        else {
            return "0.0"
        }
    }

    private fun permissionRequest(){
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
    }
}