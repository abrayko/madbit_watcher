package ru.hfart.madbitwatcher

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.hfart.madbitwatcher.service.*
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity(), DSPWatcher {

    private val TAG = "MAIN_ACTIVITY"

    private var receiveText: TextView? = null

    private val OVERLAY_PERMISSION_REQUEST_CODE = 100

    private lateinit var weakService : WeakReference<DSPWatcherService>

    private val serviceConnection  = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            if (binder is DSPWatherBinder) {
                val service: DSPWatcherService? = binder.getService()
                if (service != null) {
                    weakService = WeakReference(service)
                    service.forceForeground()
                    service.registerDSPWatcher(this@MainActivity)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        receiveText = findViewById(R.id.receive_text) // TextView performance decreases with number of spans

        bindMadbitDSPService()
        //showFloatingView(this, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        getService()!!.unregisterDSPWatcher()
        unbindMadbitDSPService()
    }


    fun getService() : DSPWatcherService? {
        return if (weakService == null) null else weakService.get()
    }

    @SuppressLint("NewApi")
    private fun showFloatingView(context: Context, isShowOverlayPermission: Boolean) { // API22
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            startMadbitDSPService()
            return
        }

        if (Settings.canDrawOverlays(context)) {
            startMadbitDSPService()
            return
        }

        if (isShowOverlayPermission) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
            startActivityForResult(
                intent,
                OVERLAY_PERMISSION_REQUEST_CODE
            )
        }
    }


    /**
     * Handles permission to display overlays.
     */
    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            showFloatingView(this, false)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startMadbitDSPService() {
        HandleService.startService(this)
    }

    private fun unbindMadbitDSPService() {
        Log.d(TAG, "Start unbinding from service")
        this.unbindService(serviceConnection)
    }

    private fun bindMadbitDSPService() {
        Log.d(TAG, "Start binding to service")
        HandleService.bindService(this, serviceConnection)
    }


    /** получение значение громкости */
    override fun onDSPChangeVolume(volume: Int) {
        val msg = StringBuilder()
            .append("Set volume: $volume")
            .toString()
        Log.d(TAG, msg)
        receiveText!!.append("$msg\n")
    }

    /** получение значения типа входа */
    override fun onDSPChangeInput(input: String) {
        val msg = StringBuilder()
            .append("Set input: $input")
            .toString()
        Log.d(TAG, msg)
        receiveText!!.append("$msg\n")
    }

    /** получение значения частоты дискретизации */
    override fun onDSPChangeFS(fs: Int) {
        val msg = StringBuilder()
            .append("Set FS: $fs")
            .toString()
        Log.d(TAG, msg)
        receiveText!!.append("$msg\n")
    }

    /** получение значения пресета */
    override fun onDSPChangePreset(preset: String) {
        val msg = StringBuilder()
            .append("Set preset: $preset")
            .toString()
        Log.d(TAG, msg)
        receiveText!!.append("$msg\n")
    }

    /** получение события о подключении к процу */
    override fun onDSPConnect() {
        Log.d(TAG, "DSP connected")
        receiveText!!.append("DSP connected\n")
    }

    /** получение события об отключении от проца */
    override fun onDSPDisconnect() {
        Log.d(TAG, "DSP disconnected")
        receiveText!!.append("DSP disconnected\n")
    }

    /** TODO: временно, только для отладки */
    override fun onDataRecieve(line: String) {
        receiveText!!.append(line)
    }
}
