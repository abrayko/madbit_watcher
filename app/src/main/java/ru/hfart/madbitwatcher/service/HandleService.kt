package ru.hfart.madbitwatcher.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import androidx.core.content.ContextCompat

class HandleService {

    companion object {
        private val TAG = "HANDLE_SERVICE"

        fun startService(context: Context) {
            Log.d(TAG, "Starting service")
            val service = DSPWatcherService::class.java
            val intent = Intent(context, service)
            ContextCompat.startForegroundService(context, intent)
        }

        fun bindService(context: Context, connector: ServiceConnection) {
            Log.d(TAG, "Binding service")
            val service = DSPWatcherService::class.java
            val intent = Intent(context, service)
            context.bindService(intent, connector, Context.BIND_AUTO_CREATE)
        }
    }
}