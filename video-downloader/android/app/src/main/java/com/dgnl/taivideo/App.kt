package com.dgnl.taivideo

import android.app.Application
import android.util.Log
import fi.iki.elonen.NanoHTTPD

class App : Application() {
    lateinit var server: LocalServer
        private set

    override fun onCreate() {
        super.onCreate()
        DownloadService.createChannels(this)
        JobManager.init(this)
        Engine.start(this)
        server = LocalServer(this)
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        } catch (e: Exception) {
            Log.e("TaiVideo", "server start failed", e)
        }
    }

    companion object {
        /** Ứng dụng có đang hiện trên màn hình không (để quyết định có cần báo thông báo). */
        @Volatile var inForeground = false
    }
}
