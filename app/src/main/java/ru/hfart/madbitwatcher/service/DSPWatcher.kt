package ru.hfart.madbitwatcher.service

interface DSPWatcher {

    /** получение значение громкости */
    fun onDSPChangeVolume (volume : Int)

    // TODO: переделать на enum
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

    /** TODO: временно, только для отладки */
    fun onDataRecieve (line : String)
}