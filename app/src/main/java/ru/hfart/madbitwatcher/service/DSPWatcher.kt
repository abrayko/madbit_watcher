package ru.hfart.madbitwatcher.service

interface DSPWatcher {

    // TODO: вообще это не относится к MadbitDSP и надо бы вынести в отдельный интерфейс
    // А может просто в активити реализовать интерфейс ServiceConnection
    /** сохранение IBinder с целью последующего получения Service и обращения к его методам
     */
    //fun bindService(binder: MadbitWatherBinder)

    /** получение значение громкости */
    fun onDSPChangeVolume (volume : Int)

    // TODO: переделать на unum
    /** получение значения типа входа */
    fun onDSPChangeInput (input : String)

    /** получение значения частоты дискретизации */
    fun onDSPChangeFS (fs : Int)

    // TODO: переделать на unum
    /** получение значения пресета */
    fun onDSPChangePreset (preset : String)

    /** получение события о подключении к процу */
    fun onDSPConnect ()

    /** получение события об отключении от проца */
    fun onDSPDisconnect ()
}