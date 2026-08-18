package com.thotapalli.plex.core.session

/**
 * The parts of the identity headers that come from the device rather than from storage.
 * See CLAUDE.md section 5.
 */
data class DeviceInfo(
    /** "Android" or "Windows". */
    val platform: String,
    val platformVersion: String,
    /** Device model. */
    val device: String,
    /** User visible device name, shown on the account's device list. */
    val deviceName: String,
    /** Application version name. */
    val appVersion: String,
)

/** Filled in per platform, since none of it is knowable from common code. */
expect fun currentDeviceInfo(appVersion: String): DeviceInfo
