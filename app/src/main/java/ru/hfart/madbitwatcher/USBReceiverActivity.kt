package ru.hfart.madbitwatcher

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.hfart.madbitwatcher.service.DSPWatcherService
import ru.hfart.madbitwatcher.service.DSPWatherBinder
import ru.hfart.madbitwatcher.service.HandleService
import ru.hfart.madbitwatcher.service.USBSerialHandler

/**
 * Activity для регистрации событий подключения USB устройств.
 * Чтобы андройд запомнил разрешения на USB нужно в манифесте добавить intent-filter именно для
 *  activity, а не brReceiver или service.
 */
class USBReceiverActivity : AppCompatActivity() {

    private val TAG = "USB_RECEIVER_ACTIVITY"

    val INTENT_ACTION_GRANT_USB: String =
        BuildConfig.APPLICATION_ID + ".GRANT_USB"
    private var broadcastReceiver: BroadcastReceiver? = null
    private var hasPermission = false
    private var permissionPendind = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent == null) {
            finish()
            return
        }
        if (intent.action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            checkAndGetUSBPermission(this)
        }
        if (hasPermission) {
            startService(this)
        }
        /* права есть - завершаем
           прав нет и не запрашиваем - завершаем
           прав нет и запрашиваем - продолжаем
         */
        if (!(!hasPermission && permissionPendind)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver)
    }

    private fun startService(context: Context) {
        val appContext = context.getApplicationContext()
        HandleService.bindService(appContext, object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Service disconnected")
                finish()
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
                finish()
            }
        }
        )
    }

    private fun checkAndGetUSBPermission(context: Context) {
        val device = USBSerialHandler.getDevice(context)
        if (device == null) {
            Log.d(TAG, "connection failed: device not found")
            return
        }

        hasPermission = USBSerialHandler.checkUSBPermision(context, device)
        if (!hasPermission) {
            Log.d(TAG, "missing permision")
            Toast.makeText(context, "USBReceiverActivity: missing permision", Toast.LENGTH_SHORT).show()
            createPermissionReceiver()

            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(INTENT_ACTION_GRANT_USB), 0)
            val filter = IntentFilter(INTENT_ACTION_GRANT_USB)
            registerReceiver(broadcastReceiver, filter)
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            permissionPendind = true
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun createPermissionReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == INTENT_ACTION_GRANT_USB) {
                    permissionPendind = false
                    hasPermission =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (hasPermission) {
                        // TODO: подключение к сервису и запуск подключения
                        Log.d(TAG, "Permission granted")
                        Toast.makeText(context, "USBReceiverActivity: Permission granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "Permission rejected")
                        Toast.makeText(context, "USBReceiverActivity: Permission rejected", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
        }
    }
}