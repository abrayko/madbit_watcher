package ru.hfart.madbitwatcher.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import ru.hfart.madbitwatcher.HandleNotifications
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

    private var serialDataBuffer : String? = null

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
        HandleService.startService(applicationContext)
        val notification = HandleNotifications.createNotification(this)
        startForeground(HandleNotifications.NOTIFICATION_ID, notification)
        connectToDSP()
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

    fun connectToDSP() {
        val device = USBSerialHandler.getDevice(this)
        if (device == null) {
            logError("connection failed: device not found")
            return
        }

        if (!USBSerialHandler.checkUSBPermision(this, device)) {
            logError("connection failed: permission missed")
            return
        }

        val serialPort = USBSerialHandler.openDevice(this, device)
        if (serialPort != null) {
            startIoManager(serialPort)
        }
    }

    private fun stopIoManager() {
        if (serialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..")
            serialIoManager!!.stop()
            serialIoManager = null
        }
    }

    private fun startIoManager(port: UsbSerialPort) {
        Log.d(TAG, "Starting io manager ..")
        serialIoManager = SerialInputOutputManager(port, serialListener)
        executor.submit(serialIoManager)
    }


    fun onReceiveSerialData(data: ByteArray?) {
        if (data == null) return
        synchronized (this) {
            val sb = StringBuilder()
            sb.append(serialDataBuffer).append(String(data).replace("\r\n", "\n"))
            val lines = sb.toString().split("\n")
            if (lines.isEmpty()) return

            // Проверяем каждую строку на искомый шаблон регулярки.
            // Если последняя строка не прошла, то оставляем ее на будущее - будем приклеивать новые данные и опать проверять
            for (i in lines.indices) {
                //DEBUG:
                /*if (dspWatcher != null && i != lines.size-1) {
                    dspWatcher?.onDataRecieve("data: ${lines[i]}\n")
                }*/
                if (dspWatcher != null ) {
                    dspWatcher?.onDataRecieve("data: ${lines[i]}\n")
                }

                val matchResult = regex.find(lines[i])
                if (matchResult != null) {
                    val vals = matchResult.groupValues
                    val key = vals[2]
                    val value = vals[3]
                    Log.d(TAG, "Receive Key: $key value: $value")
                    if (dspWatcher != null) {
                        when (key) {
                            "SYS_VOL" -> dspWatcher?.onDSPChangeVolume(value.toInt())
                            "SYS_CFG" -> dspWatcher?.onDSPChangePreset(value)
                            "SYS_SRC" -> dspWatcher?.onDSPChangeInput(value)
                            "SYS_FREQ" -> dspWatcher?.onDSPChangeFS(value.toInt())
                        }
                    }
                }
                if (i == lines.size-1 && matchResult == null) {
                    serialDataBuffer = lines[i]
                }
            }
        }
    }

    fun logError(msg: String) {
        logError(msg, null)
    }

    fun logError(msg: String, err: Exception?) {
        if (err == null) {
            Log.e(TAG, msg)
        } else {
            Log.e(TAG, msg, err)
        }
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}