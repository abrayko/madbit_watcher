package ru.hfart.madbitwatcher

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import ru.hfart.madbitwatcher.service.DSPWatcherService
import ru.hfart.madbitwatcher.service.DSPWatherBinder
import ru.hfart.madbitwatcher.service.HandleService
import ru.hfart.madbitwatcher.service.USBSerialHandler

class MadbitWathcerReceiver : BroadcastReceiver() {

    private val TAG = "BOOT_BROADCAST_RECEIVER"


    override fun onReceive(context: Context, intent: Intent) {
        StringBuilder().apply {
            append("Action: ${intent.action}\n")
            append("URI: ${intent.toUri(Intent.URI_INTENT_SCHEME)}\n")
            toString().also { log ->
                Log.d(TAG, log)
            }
        }

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> startService(context)
        }
    }

    private fun startService(context: Context) {
        val appContext = context.getApplicationContext()
        HandleService.bindService(appContext, object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Service disconnected")
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                Log.d(TAG, "Service connected")
                if (binder is DSPWatherBinder) {
                    val service: DSPWatcherService? = binder.getService()
                    if (service != null) {
                        service.forceForeground()
                        Log.d(TAG, "Unbinding service")
                        context.getApplicationContext().unbindService(this)
                    }
                }
            }
        }
        )
    }

}