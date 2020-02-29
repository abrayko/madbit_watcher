package ru.hfart.madbitwatcher.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.lang.Exception

class USBSerialHandler {

    companion object {

        private val TAG = "USB_SERIAL_HANDLER"

        private val vendorId : Int = 0x10c4
        private val productId : Int = 0xea60
        private val productName : String = "DSP8_001_V108"
        private val serialPortNum : Int = 0

        private val baudRate : Int = 921600
        //private val baudRate : Int = 9600

        /**
        @return true - использование USB устройства разрешено
         */
        fun checkUSBPermision(context: Context, device: UsbDevice): Boolean {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.hasPermission(device)
        }

        fun getDevice(context: Context) : UsbDevice? {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            var device : UsbDevice? = null
            for (v in usbManager.deviceList.values) {
                // TODO: проверка на productName = DSP8_001_V108
                if (v.vendorId == vendorId && v.productId == productId) device = v
            }
            Log.d(TAG, "USB product name: ${device?.productName}")
            return device
        }

        // TODO: на каждую ошибку генерировать exception'ы
        fun openDevice(context: Context, device: UsbDevice) : UsbSerialPort? {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver == null) {
                Log.d(TAG, "connection failed: no driver for device")
                return null
            }
            if (driver.ports == null || driver.ports.size < 1 ) {
                Log.d(TAG,"connection failed: no ports in device")
            }
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                if (!usbManager.hasPermission(driver.device)) {
                    Log.d(TAG, "connection failed: permission missed")
                    return null
                } else {
                    Log.d(TAG, "connection failed: open failed")
                    return null
                }
            }
            val serialPort = driver.ports[serialPortNum]

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

                Log.d(TAG, "CD  - Carrier Detect, ${serialPort.getCD()}")
                Log.d(TAG, "CTS - Clear To Send, ${serialPort.getCD()}")
                Log.d(TAG, "DSR - Data Set Ready, ${serialPort.getCD()}")
                Log.d(TAG, "DTR - Data Terminal Ready, ${serialPort.getCD()}")
                Log.d(TAG, "RI  - Ring Indicator, ${serialPort.getCD()}")
                Log.d(TAG, "RTS - Request To Send, ${serialPort.getCD()}")
            } catch (e: IOException) {
                Log.d("connection failed: ${e.message}", e.toString())
                closeCOMPort(serialPort)
                return null
            }
            return serialPort
        }

        fun closeCOMPort(port : UsbSerialPort) {
            try {
                port.close()
            } catch (e2: IOException) {
                // Ignore.
            }
            return
        }
    }
}