package com.thotapalli.plex.core.session

import java.net.InetAddress

actual fun currentDeviceInfo(appVersion: String): DeviceInfo {
    val osName = System.getProperty("os.name") ?: "Windows"
    val osVersion = System.getProperty("os.version") ?: ""
    val hostName = runCatching { InetAddress.getLocalHost().hostName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    return DeviceInfo(
        // CLAUDE.md section 5 fixes this at "Windows" for the desktop target.
        platform = "Windows",
        platformVersion = "$osName $osVersion".trim(),
        device = "Windows",
        deviceName = hostName ?: "Windows PC",
        appVersion = appVersion,
    )
}
