package io.github.romanvht.byedpi.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Определяет уникальный идентификатор текущей сети провайдера (Wi-Fi или Мобильная сеть).
 */
object NetworkDetector {

    fun getCurrentNetworkId(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "UNKNOWN_NETWORK"

        val activeNetwork = cm.activeNetwork ?: return "NO_NETWORK"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "UNKNOWN_NETWORK"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                getWifiIdentifier(context)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                getCellularIdentifier(context)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                "ETHERNET"
            }
            else -> "OTHER_NETWORK"
        }
    }

    private fun getWifiIdentifier(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "WIFI"
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                "WIFI:$ssid"
            } else {
                "WIFI_GENERIC"
            }
        } catch (_: Exception) {
            "WIFI_GENERIC"
        }
    }

    private fun getCellularIdentifier(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val operatorName = telephonyManager?.networkOperatorName ?: ""
            val simOperator = telephonyManager?.simOperator ?: ""
            if (operatorName.isNotBlank()) {
                "CELLULAR:${operatorName.uppercase().replace(" ", "_")}"
            } else if (simOperator.isNotBlank()) {
                "CELLULAR:$simOperator"
            } else {
                "CELLULAR_GENERIC"
            }
        } catch (_: Exception) {
            "CELLULAR_GENERIC"
        }
    }
}
