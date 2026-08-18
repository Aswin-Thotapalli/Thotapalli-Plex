package com.thotapalli.plex.core.session

import android.os.Build

actual fun currentDeviceInfo(appVersion: String): DeviceInfo = DeviceInfo(
    platform = "Android",
    platformVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    device = Build.MODEL ?: "Android",
    // Shown on the account's device list, so it names the hardware rather than the app.
    deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" },
    appVersion = appVersion,
)
