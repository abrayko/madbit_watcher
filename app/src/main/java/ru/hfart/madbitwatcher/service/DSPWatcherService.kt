package ru.hfart.madbitwatcher.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import ru.hfart.madbitwatcher.HandleNotifications

class DSPWatcherService : Service() {

    private val TAG = "MADBIT_DSP_WATCHER_SERVICE"

    private val binder = DSPWatherBinder()

    private var dspWatcher : DSPWatcher? = null

    companion object {
        /**
         * Intent key (Cutout safe area)
         */
        //const val EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area"
    }

    /**
     * FloatingViewManager
     */
    //private var mFloatingViewManager: FloatingViewManager? = null

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
    override fun onDestroy() {
        Log.d(TAG, "Destroing service")
        super.onDestroy()
        stopForeground(true)
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
}