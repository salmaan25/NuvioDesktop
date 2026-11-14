package com.nuvio.app.features.streams

import platform.Foundation.NSUserDefaults

internal actual object StreamQualityOrderStorage {
    private const val orderKey = "nuvio_stream_quality_order"

    actual fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(orderKey)

    actual fun save(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = orderKey)
    }
}
