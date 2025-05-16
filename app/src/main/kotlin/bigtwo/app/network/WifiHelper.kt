package bigtwo.app.network

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.Manifest
import android.content.pm.PackageManager

class WiFiHelper(private val context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isWifiEnabled(): Boolean = try {
        wifiManager.isWifiEnabled
    } catch (e: SecurityException) {
        println("SecurityException: ${e.message}")
        false
    }

    fun startScan(): List<ScanResult>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            println("位置权限未授予，无法扫描WiFi网络。")
            return null
        }
        return try {
            @Suppress("DEPRECATION")
            val success = wifiManager.startScan()
            if (success) {
                // 确保扫描结果不为空
                val results = wifiManager.scanResults
                if (results.isNotEmpty()) results else null
            } else null
        } catch (e: SecurityException) {
            println("SecurityException: ${e.message}")
            null
        }
    }

    fun getConnectedNetwork(): String? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            println("权限 ACCESS_FINE_LOCATION 未授予，无法获取已连接的网络。")
            return null
        }
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.ssid != null && wifiInfo.ssid != WifiManager.UNKNOWN_SSID) {
                wifiInfo.ssid.replace("\"", "") // 去掉引号
            } else {
                null
            }
        } catch (e: SecurityException) {
            println("SecurityException: ${e.message}")
            null
        }
    }
}
