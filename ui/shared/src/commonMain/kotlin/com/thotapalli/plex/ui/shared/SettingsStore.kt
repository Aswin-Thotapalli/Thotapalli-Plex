package com.thotapalli.plex.ui.shared

import com.thotapalli.plex.core.session.KeyValueStore

/**
 * The settings screen from CLAUDE.md section 14 item 9.
 *
 * Defaults differ per target where the brief says so, which is why [isTelevision] is a
 * constructor argument rather than something each getter works out.
 */
class SettingsStore(
    private val store: KeyValueStore,
    private val isTelevision: Boolean,
) {

    /**
     * "Match display rate to content". Defaults on for television, off for phone and
     * Windows. See CLAUDE.md section 9.
     */
    var matchDisplayRate: Boolean
        get() = store.getString(MATCH_DISPLAY_RATE)?.toBooleanStrictOrNull() ?: isTelevision
        set(value) = store.putString(MATCH_DISPLAY_RATE, value.toString())

    /**
     * "Download on unmetered networks only". Defaults on for Android and off for Windows.
     * See CLAUDE.md section 11.
     */
    var unmeteredDownloadsOnly: Boolean
        get() = store.getString(UNMETERED_ONLY)?.toBooleanStrictOrNull() ?: defaultUnmetered
        set(value) = store.putString(UNMETERED_ONLY, value.toString())

    var preferredAudioLanguage: String
        get() = store.getString(AUDIO_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = store.putString(AUDIO_LANGUAGE, value)

    var preferredSubtitleLanguage: String
        get() = store.getString(SUBTITLE_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = store.putString(SUBTITLE_LANGUAGE, value)

    var subtitlesOnByDefault: Boolean
        get() = store.getString(SUBTITLES_ON)?.toBooleanStrictOrNull() ?: false
        set(value) = store.putString(SUBTITLES_ON, value.toString())

    /** Set by the platform layer, since only it knows whether Android or Windows is running. */
    var defaultUnmetered: Boolean = true

    private companion object {
        const val MATCH_DISPLAY_RATE = "setting_match_display_rate"
        const val UNMETERED_ONLY = "setting_unmetered_downloads_only"
        const val AUDIO_LANGUAGE = "setting_audio_language"
        const val SUBTITLE_LANGUAGE = "setting_subtitle_language"
        const val SUBTITLES_ON = "setting_subtitles_on"
        const val DEFAULT_LANGUAGE = "eng"
    }
}
