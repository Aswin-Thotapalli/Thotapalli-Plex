package com.thotapalli.plex.core.session

/**
 * An in-memory store whose contents survive the object being thrown away, which is what
 * makes a restart simulable: build a new store over the same backing map.
 */
class FakeKeyValueStore(
    private val backing: MutableMap<String, String> = mutableMapOf(),
) : KeyValueStore {
    var writes: Int = 0
        private set

    override fun getString(key: String): String? = backing[key]

    override fun putString(key: String, value: String) {
        writes++
        backing[key] = value
    }

    override fun remove(key: String) {
        backing.remove(key)
    }

    override fun clear() = backing.clear()

    /** A new store instance over the same persisted contents. */
    fun reopen(): FakeKeyValueStore = FakeKeyValueStore(backing)
}

class FakeSecureStore(
    private val backing: MutableMap<String, String> = mutableMapOf(),
) : SecureStore {
    override fun getSecret(key: String): String? = backing[key]
    override fun putSecret(key: String, value: String) { backing[key] = value }
    override fun removeSecret(key: String) { backing.remove(key) }
    override fun clear() = backing.clear()
    fun reopen(): FakeSecureStore = FakeSecureStore(backing)
}

val testDevice = DeviceInfo(
    platform = "Android",
    platformVersion = "16 (API 37)",
    device = "Pixel Test",
    deviceName = "Google Pixel Test",
    appVersion = "0.1.0",
)
