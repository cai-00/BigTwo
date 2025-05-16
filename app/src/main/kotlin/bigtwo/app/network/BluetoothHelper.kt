package bigtwo.app.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.lifecycle.MutableLiveData

class BluetoothHelper(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(BluetoothManager::class.java))?.adapter

    private val discoveredDevices = mutableSetOf<BluetoothDevice>()

    // LiveData 用于观察设备列表的变化
    val discoveredDevicesLiveData = MutableLiveData<Set<BluetoothDevice>>()

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    private fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (e: SecurityException) {
        println("SecurityException: ${e.message}")
        false
    }

    // 对外提供蓝牙状态
    fun isBluetoothEnabledPublic(): Boolean = isBluetoothEnabled()

    fun startDiscovery(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            println("权限 BLUETOOTH_SCAN 未授予，无法启动蓝牙发现。")
            return false
        }

        // 清空之前发现的设备
        discoveredDevices.clear()

        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        return try {
            bluetoothAdapter?.startDiscovery() == true
        } catch (e: SecurityException) {
            println("SecurityException: ${e.message}")
            false
        }
    }

    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            println("权限 BLUETOOTH_CONNECT 未授予，无法获取已配对设备。")
            return null
        }
        return try {
            bluetoothAdapter?.bondedDevices
        } catch (e: SecurityException) {
            println("SecurityException: ${e.message}")
            null
        }
    }

    // 更新已发现的设备列表，并通知 UI 层
    private fun updateDiscoveredDevices(devices: Set<BluetoothDevice>) {
        discoveredDevicesLiveData.postValue(devices)
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            discoveredDevices.add(it)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    println("蓝牙扫描完成，共发现 ${discoveredDevices.size} 个设备。")
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: IllegalArgumentException) {
                        println("广播接收器未注册或已注销：${e.message}")
                    }

                    // 扫描完成时更新 UI
                    updateDiscoveredDevices(discoveredDevices)
                }
            }
        }
    }
}
