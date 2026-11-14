package com.nuvio.app.features.streams

/**
 * The salmaan25 fork ships desktop builds only, so on Android the quality order is kept for the
 * running app only (it starts from the default each launch) instead of adding startup wiring.
 */
internal actual object StreamQualityOrderStorage {
    private var value: String? = null

    actual fun load(): String? = value

    actual fun save(value: String) {
        this.value = value
    }
}
