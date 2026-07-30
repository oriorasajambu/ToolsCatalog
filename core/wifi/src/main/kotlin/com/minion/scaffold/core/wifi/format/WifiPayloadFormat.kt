package com.minion.scaffold.core.wifi.format

/** The literals of the `WIFI:` payload format, shared by the writer and the reader. */
internal object WifiPayloadFormat {

    const val PREFIX = "WIFI:"

    const val KEY_SECURITY = "T"
    const val KEY_SSID = "S"
    const val KEY_PASSWORD = "P"
    const val KEY_HIDDEN = "H"

    const val KEY_VALUE_SEPARATOR = ':'
    const val FIELD_SEPARATOR = ';'

    const val HIDDEN_TRUE = "true"
}
