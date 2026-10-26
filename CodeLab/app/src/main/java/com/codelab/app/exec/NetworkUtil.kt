package com.codelab.app.exec

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Công cụ kiểm tra máy có đang kết nối Internet hay không. */
object NetworkUtil {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        // Có khả năng truy cập Internet qua wifi, 4G/5G hoặc mạng dây
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
}
