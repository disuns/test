package com.example.test

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.test.databinding.ActivityMainBinding
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var mOutputStream: OutputStream
    lateinit var mInputStream: InputStream
    lateinit var mBluetoothAdapter : BluetoothAdapter

    lateinit var mSocket: BluetoothSocket

    lateinit var mConnectThread : ConnectThread

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
                    Manifest.permission.BLUETOOTH_SCAN
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

        val pairedDevices: Set<BluetoothDevice>? = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        } else {
            mBluetoothAdapter.bondedDevices
        }



        binding.btnReadData.setOnClickListener { pairedDevices?.forEach { device ->
            if(device.name == "BC-03"){
                mConnectThread = ConnectThread(device)
                mConnectThread.run()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            mInputStream.close()
            mOutputStream.close()
            mConnectThread.stopThread()
        }catch (e:Exception){}
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    inner class ConnectThread(device: BluetoothDevice) : Thread(){
        override fun run() {
            mBluetoothAdapter.cancelDiscovery()
            try {
                mSocket.connect()
            }catch (e:Exception){
                Log.e("socket connect",e.message.toString())
            }
        }

        fun stopThread(){
            try {
                mSocket.close()
            }catch (e:Exception){
                Log.e("socket close",e.message.toString())
            }
        }

        init {
            try {
                var uuid= UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
                //var uuid= UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
                mSocket = device.createRfcommSocketToServiceRecord(uuid)
            }catch (e:Exception){
                Log.e("socket create",e.message.toString())
            }
        }
    }
}