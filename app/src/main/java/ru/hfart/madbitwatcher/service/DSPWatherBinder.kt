package ru.hfart.madbitwatcher.service

import android.os.Binder
import java.lang.ref.WeakReference

class DSPWatherBinder : Binder() {
    private lateinit var weakService : WeakReference<DSPWatcherService>

    fun onBind(service : DSPWatcherService) {
        this.weakService = WeakReference(service)
    }

    fun getService() : DSPWatcherService? {
        return if (weakService == null) null else weakService.get()!!
    }
}