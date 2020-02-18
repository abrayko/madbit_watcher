package ru.hfart.madbitwatcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
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

    //TODO: задать конкретное значение
    private val vendorId : Int = 0x10c4
    private val productId : Int = 0xea60
    private val productName : String = "DSP8_001_V108"
    private val serialPortNum : Int = 1
    private val baudRate : Int = 921600

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
        connectByCOM()
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

    fun connectByCOM() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        // find Madbit DSP HD8
        var device : UsbDevice? = null
        for (v in usbManager.deviceList.values) {
            // TODO: проверка на productName = DSP8_001_V108
            if (v.vendorId == vendorId && v.productId == productId) device = v
        }
        if (device == null) {
            logError("connection failed: device not found")
            return
        }

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            logError("connection failed: no driver for device")
            return
        }
        /*if (driver.ports.size < serialPortNum) {
            logError("connection failed: not enough ports at device")
            return
        }*/
        Log.d(TAG, "USB product name: ${driver.device.productName}")

        val serialPort = driver.ports[serialPortNum]
        val connection = usbManager.openDevice(driver.device)

        if (connection == null) {
            if (!usbManager.hasPermission(driver.device)) logError("connection failed: permission denied")
            else logError("connection failed: open failed")
            return
        }

        try {
            serialPort.open(connection)
            serialPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            serialPort.dtr = true // for arduino, ...
            serialPort.rts = true
            startIoManager(serialPort)

            Log.d(TAG, "CD  - Carrier Detect, ${serialPort.getCD()}")
            Log.d(TAG, "CTS - Clear To Send, ${serialPort.getCD()}")
            Log.d(TAG, "DSR - Data Set Ready, ${serialPort.getCD()}")
            Log.d(TAG, "DTR - Data Terminal Ready, ${serialPort.getCD()}")
            Log.d(TAG, "RI  - Ring Indicator, ${serialPort.getCD()}")
            Log.d(TAG, "RTS - Request To Send, ${serialPort.getCD()}")
        } catch (e: IOException) {
            logError("connection failed: ${e.message}", e)
            closeCOMPort(serialPort)
            return
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
                        "SYS_VOL" -> dspWatcher?.onDSPChangeVolume(value.toInt())
                        "SYS_CFG" -> dspWatcher?.onDSPChangePreset(value)
                        "SYS_SRC" -> dspWatcher?.onDSPChangeInput(value)
                        "SYS_FREQ"-> dspWatcher?.onDSPChangeFS(value.toInt())
                    }
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