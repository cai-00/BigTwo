package bigtwo.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bigtwo.app.network.WiFiHelper
import bigtwo.app.network.BluetoothHelper
import androidx.compose.runtime.livedata.observeAsState
import android.net.wifi.ScanResult
import androidx.core.content.ContextCompat

@Composable
fun DeviceControlScreen() {
    val context = LocalContext.current
    val wifiHelper = remember { WiFiHelper(context) }
    val bluetoothHelper = remember { BluetoothHelper(context) }

    var connectedNetwork by remember { mutableStateOf<String?>(null) }
    var availableNetworks by remember { mutableStateOf<List<String>>(emptyList()) }
    var pairedDevices by remember { mutableStateOf<List<String>>(emptyList()) }

    // 观察蓝牙设备列表的 LiveData
    val availableDevices by bluetoothHelper.discoveredDevicesLiveData.observeAsState(emptySet())

    // 处理蓝牙发现的结果
    val handleBluetoothDiscovery = rememberUpdatedState {
        // 启动蓝牙扫描并更新设备列表
        try {
            val discoveryStarted = bluetoothHelper.startDiscovery()
            if (discoveryStarted) {
                // 获取已配对设备
                pairedDevices = bluetoothHelper.getPairedDevices()
                    ?.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }
                    ?.distinct() ?: emptyList()
            } else {
                Toast.makeText(context, "未扫描到任何蓝牙设备", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "权限不足，无法扫描蓝牙设备", Toast.LENGTH_SHORT).show()
            println("SecurityException: ${e.message}")
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // WiFi 扫描按钮和显示区域
        Button(modifier = Modifier.padding(vertical = 8.dp), onClick = {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(context, "请授权位置权限以扫描 WiFi 网络", Toast.LENGTH_SHORT).show()
                return@Button
            }
            if (!wifiHelper.isWifiEnabled()) {
                Toast.makeText(context, "请打开 WiFi 后再扫描", Toast.LENGTH_SHORT).show()
                return@Button
            }
            try {
                val results = wifiHelper.startScan()
                if (results != null && results.isNotEmpty()) {
                    connectedNetwork = wifiHelper.getConnectedNetwork()
                    availableNetworks = results
                        .mapNotNull { it.SSID.takeIf { ssid -> ssid.isNotBlank() } }
                        .distinct()
                        .filter { it != connectedNetwork }
                } else {
                    Toast.makeText(context, "未扫描到任何新的 WiFi 网络", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(context, "权限不足，无法扫描 WiFi 网络", Toast.LENGTH_SHORT).show()
                println("SecurityException: ${e.message}")
            }
        }) {
            Text("扫描WiFi")
        }

        // 显示已连接的网络
        Text("已连接的网络：")
        Text(text = connectedNetwork ?: "无")

        Spacer(modifier = Modifier.height(16.dp))

        // 显示可用网络
        Text("可用网络：")
        LazyColumn(modifier = Modifier.height(120.dp)) {
            items(availableNetworks) { network ->
                Text(text = network)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 蓝牙扫描按钮和显示区域
        Button(modifier = Modifier.padding(vertical = 8.dp), onClick = {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(context, "请授权蓝牙权限以扫描设备", Toast.LENGTH_SHORT).show()
                return@Button
            }
            if (!bluetoothHelper.isBluetoothEnabledPublic()) {
                Toast.makeText(context, "请打开蓝牙后再扫描", Toast.LENGTH_SHORT).show()
                return@Button
            }

            // 调用蓝牙发现的处理函数
            handleBluetoothDiscovery.value()
        }) {
            Text("扫描蓝牙")
        }

        // 显示已配对设备
        Text("已配对设备：")
        LazyColumn(modifier = Modifier.height(120.dp)) {
            items(pairedDevices) { device ->
                Text(text = device)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 显示可用设备
        Text("可用设备：")
        LazyColumn(modifier = Modifier.height(120.dp)) {
            items(availableDevices.toList()) { device ->
                // 处理 null 值，若设备的 name 为 null，则显示 "未命名设备"
                Text(text = device.name ?: "未命名设备")
            }
        }
    }
}
