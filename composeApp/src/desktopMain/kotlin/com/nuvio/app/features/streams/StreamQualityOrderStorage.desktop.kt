package com.nuvio.app.features.streams

import com.nuvio.app.core.storage.DesktopStorage

internal actual object StreamQualityOrderStorage {
    private val store by lazy { DesktopStorage.store("nuvio_stream_quality_order") }
    private const val orderKey = "order"

    actual fun load(): String? = store.getString(orderKey)

    actual fun save(value: String) = store.putString(orderKey, value)
}
