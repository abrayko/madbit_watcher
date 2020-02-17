package ru.hfart.madbitwatcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import ru.hfart.madbitwatcher.HandleNotifications
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/*
Сообщения от Madbit DSP HD8:
SYS MESSAGE<SYS_VOL -14> - Установка уровня громкости -14dB
SYS MESSAGE<SYS_CFG 2> - Переключение на 2-й пресет
SYS MESSAGE<SYS_SRC SPDIF> - Переключение на вход SPDIF
SYS MESSAGE<SYS_FREQ 48000> - Определена частота дискретизации 48000

Regexp: (SYS MESSAGE<)([A-Z_]*)\s([-A-Z0-9]*)(>)
*/

class DSPWatcherService : Service() {

    private val TAG = "DSP_WATCHER_SERVICE"

    private val REGEXP_PATTERN = "(SYS MESSAGE<)([A-Z_]*)\\s([-A-Z0-9]*)(>)"
    private val regex = REGEXP_PATTERN.toRegex()

    private val binder = DSPWatherBinder()

    private var dspWatcher : DSPWatcher? = null

    private var handler: Handler? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var  serialIoManager: SerialInputOutputManager? = null

    private var serialListener = object : SerialInputOutputManager.Listener {
        /**
         * Called when [SerialInputOutputManager.run] aborts due to an error.
         */
        override fun onRunError(e: Exception?) {
            Log.d(TAG, "COM Runner stopped.");
        }

        /**
         * Called when new incoming data is available.
         */
        override fun onNewData(data: ByteArray?) {
            this@DSPWatcherService.runOnUiThread(Runnable {
                this@DSPWatcherService.onReceiveSerialData(data)
            })
        }

    }

    /**
     * Called by the system when the service is first created.  Do not call this method directly.
     */
    override fun onCreate() {
        //handler = Handler()
        handler = Handler(Looper.getMainLooper())
        super.onCreate()
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroy() {
        Log.d(TAG, "Destroing service")
        super.onDestroy()
        stopForeground(true)
    }

    private fun runOnUiThread(runnable: Runnable) {
        handler?.post(runnable)
    }

    /**
     * {@inheritDoc}
     */
    override fun onStartCommand(
        intent: Intent,
        flags: Int,
        startId: Int
    ): Int {
        return START_REDELIVER_INTENT
    }

    fun forceForeground() {
        Log.d(TAG, "Starting service")
        val notification = HandleNotifications.createNotification(this)
        startForeground(HandleNotifications.NOTIFICATION_ID, notification)
    }

    /**
     * {@inheritDoc}
     */
    override fun onBind(intent: Intent?): IBinder? {
        binder.onBind(this)
        return binder
    }


    fun registerDSPWatcher (watcher: DSPWatcher) {
        Log.d(TAG, "Watcher registered to service")
        this.dspWatcher = watcher
    }

    fun unregisterDSPWatcher() {
        Log.d(TAG, "Watcher unregistered to service")
        this.dspWatcher = null
    }

    private fun stopIoManager() {
        if (serialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..")
            serialIoManager!!.stop()
            serialIoManager = null
        }
    }

    private fun startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..")
            serialIoManager = SerialInputOutputManager(sPort, serialListener)
            executor.submit(serialIoManager)
        }
    }
    fun connectByCOM() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = usbManager.openDevice(sPort.getDriver().getDevice())
        if (connection == null) {
            Log.d(TAG, "Opening device failed")
            Toast.makeText(applicationContext, "Opening device failed", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            sPort.open(connection);
            sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            Log.d(TAG, "CD  - Carrier Detect, ${sPort.getCD()}")
            Log.d(TAG, "CTS - Clear To Send, ${sPort.getCD()}")
            Log.d(TAG, "DSR - Data Set Ready, ${sPort.getCD()}")
            Log.d(TAG, "DTR - Data Terminal Ready, ${sPort.getCD()}")
            Log.d(TAG, "RI  - Ring Indicator, ${sPort.getCD()}")
            Log.d(TAG, "RTS - Request To Send, ${sPort.getCD()}")
        } catch (e: IOException) {
            Log.e(TAG, "Error setting up device: " + e.message, e)
            Toast.makeText(applicationContext, "Opening device failed: ${e.message}", Toast.LENGTH_SHORT).show()
            closeCOMPort(sPort)
            sPort = null
            return
        }
    }

    fun closeCOMPort(port : UsbSerialPort) {
        try {
            port.close()
        } catch (e2: IOException) {
            // Ignore.
        }
        return
    }

    fun onReceiveSerialData(data: ByteArray?) {
        val lines : List<String>? = data?.let { String(it).replace("\r\n", "\n").split("\n") }
        if (lines == null || lines.isEmpty()) return
        for (line in lines) {
            val matchResult = regex.find(line)
            if (matchResult != null) {
                val vals = matchResult.groupValues
                val key = vals[2]
                val value = vals[3]
                Log.d(TAG, "Receive Key: $key value: $value")
                if (dspWatcher != null) {
                    when (key) {
                        "SYS_VOL" -> dspWatcher.onDSPChangeVolume(value.toInt())
                        "SYS_CFG" -> dspWatcher.onDSPChangePreset(value)
                        "SYS_SRC" -> dspWatcher.onDSPChangeInput(value)
                        "SYS_FREQ"-> dspWatcher.onDSPChangeFS(value.toInt())
                    }
                }
            }
        }
    }
}