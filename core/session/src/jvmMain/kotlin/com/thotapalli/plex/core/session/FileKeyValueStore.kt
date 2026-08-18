package com.thotapalli.plex.core.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Plain persistent storage for Windows. Client identifier and selected server, no secrets.
 * Secrets go to [DpapiSecureStore].
 */
class FileKeyValueStore(
    private val file: Path = defaultFile(),
) : KeyValueStore {

    private val properties = Properties()

    init {
        Files.createDirectories(file.parent)
        if (Files.exists(file)) {
            Files.newInputStream(file).use(properties::load)
        }
    }

    @Synchronized
    override fun getString(key: String): String? = properties.getProperty(key)

    @Synchronized
    override fun putString(key: String, value: String) {
        properties.setProperty(key, value)
        flush()
    }

    @Synchronized
    override fun remove(key: String) {
        properties.remove(key)
        flush()
    }

    @Synchronized
    override fun clear() {
        properties.clear()
        flush()
    }

    private fun flush() {
        val temp = Files.createTempFile(file.parent, "tmp", ".properties")
        Files.newOutputStream(temp).use { properties.store(it, "Thotapalli Plex") }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        fun defaultFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return Path.of(localAppData, "ThotapalliPlex", "settings.properties")
        }
    }
}
