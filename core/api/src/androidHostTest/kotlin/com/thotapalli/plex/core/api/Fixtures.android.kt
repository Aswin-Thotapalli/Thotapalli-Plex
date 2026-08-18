package com.thotapalli.plex.core.api

actual fun fixture(name: String): String {
    val loader = requireNotNull(object {}.javaClass.classLoader) { "no class loader" }
    return requireNotNull(loader.getResourceAsStream(name)) {
        "fixture not found on the test classpath: $name"
    }.use { it.readBytes().decodeToString() }
}
