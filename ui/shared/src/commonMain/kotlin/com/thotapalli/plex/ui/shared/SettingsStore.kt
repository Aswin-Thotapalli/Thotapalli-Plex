package com.thotapalli.plex.ui.shared

import com.thotapalli.plex.core.session.KeyValueStore
import com.thotapalli.plex.ui.design.ThemeMode

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
     * "Auto play next episode" (CLAUDE.md section 2). On by default. Read by the playback
     * controller when an item ends: on, a ten second countdown then the next episode; off, the
     * next episode is offered and nothing starts on its own.
     */
    var autoPlayNext: Boolean
        get() = store.getString(AUTO_PLAY_NEXT)?.toBooleanStrictOrNull() ?: true
        set(value) = store.putString(AUTO_PLAY_NEXT, value.toString())

    /**
     * Tunnelled video on a television. Off by default: on real televisions a seek left the picture
     * running with several seconds of silence while the hardware-synced audio track was rebuilt.
     * See ExoPlayerEngine.
     */
    var tunnelledPlayback: Boolean
        get() = store.getString(TUNNELLED_PLAYBACK)?.toBooleanStrictOrNull() ?: false
        set(value) = store.putString(TUNNELLED_PLAYBACK, value.toString())

    /**
     * Dolby and DTS as a bitstream to the audio output where the device reports it can. On by
     * default so a receiver gets the original stream; off decodes to PCM, which resumes instantly
     * after a seek on outputs that are slow to re-lock. See CLAUDE.md section 18 item 3.
     */
    var audioPassthrough: Boolean
        get() = store.getString(AUDIO_PASSTHROUGH)?.toBooleanStrictOrNull() ?: true
        set(value) = store.putString(AUDIO_PASSTHROUGH, value.toString())

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

    /**
     * Default remote streaming quality as a maximum bitrate in kilobits per second, or null for
     * "Original" (no client-imposed cap — direct play / maximum). Persisted as a decimal string;
     * a null is stored as an empty string, which reads back as null. See CLAUDE.md section 10.
     */
    var streamingMaxBitrateKbps: Int?
        get() = store.getString(STREAMING_BITRATE)?.toIntOrNull()
        set(value) = store.putString(STREAMING_BITRATE, value?.toString() ?: "")

    /**
     * Subtitle text size as a percentage of the base size. 100 is the untouched size; the UI offers
     * 75 (small), 100 (normal) and 150 (large). Feeds core/playback SubtitleStyle.scalePercent.
     */
    var subtitleScalePercent: Int
        get() = store.getString(SUBTITLE_SCALE)?.toIntOrNull() ?: 100
        set(value) = store.putString(SUBTITLE_SCALE, value.toString())

    /**
     * Subtitle text colour as a packed ARGB value. Defaults to opaque white; the UI offers white
     * and amber-yellow. Feeds core/playback SubtitleStyle.foregroundArgb.
     */
    var subtitleForegroundArgb: Long
        get() = store.getString(SUBTITLE_FOREGROUND)?.toLongOrNull() ?: DEFAULT_SUBTITLE_FOREGROUND
        set(value) = store.putString(SUBTITLE_FOREGROUND, value.toString())

    /**
     * Opacity of the box drawn behind subtitle text, as a percentage. 0 is no background; the UI
     * offers none and a semi-opaque 60. Feeds core/playback SubtitleStyle.backgroundOpacityPercent.
     */
    var subtitleBackgroundOpacityPercent: Int
        get() = store.getString(SUBTITLE_BACKGROUND_OPACITY)?.toIntOrNull() ?: 0
        set(value) = store.putString(SUBTITLE_BACKGROUND_OPACITY, value.toString())

    /**
     * Light, dark or follow the system. Persisted by enum name and defaulting to
     * [ThemeMode.SYSTEM]; an unrecognised stored value falls back to SYSTEM rather than
     * crashing. PlexApp reads this to pick the theme. See CLAUDE.md sections 12 and 14.
     */
    var themeMode: ThemeMode
        get() = store.getString(THEME_MODE)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) = store.putString(THEME_MODE, value.name)

    /**
     * The viewer's preferred order of the library cards, as a list of library keys.
     *
     * Plex has no reliable client reorder API, so the order is kept locally. Persisted as a
     * newline-delimited string of keys; empty by default, which leaves libraries in the
     * server's own order. Keys no longer on the server are harmless — the sort simply ignores
     * them. See CLAUDE.md section 3 (Library screen) and section 14.
     */
    var libraryOrder: List<String>
        get() = store.getString(LIBRARY_ORDER)
            ?.split(ORDER_DELIMITER)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = store.putString(LIBRARY_ORDER, value.joinToString(ORDER_DELIMITER))

    /** Set by the platform layer, since only it knows whether Android or Windows is running. */
    var defaultUnmetered: Boolean = true

    private companion object {
        const val MATCH_DISPLAY_RATE = "setting_match_display_rate"
        const val UNMETERED_ONLY = "setting_unmetered_downloads_only"
        const val TUNNELLED_PLAYBACK = "setting_tunnelled_playback"
        const val AUTO_PLAY_NEXT = "setting_auto_play_next"
        const val AUDIO_PASSTHROUGH = "setting_audio_passthrough"
        const val AUDIO_LANGUAGE = "setting_audio_language"
        const val SUBTITLE_LANGUAGE = "setting_subtitle_language"
        const val SUBTITLES_ON = "setting_subtitles_on"
        const val STREAMING_BITRATE = "setting_streaming_max_bitrate_kbps"
        const val SUBTITLE_SCALE = "setting_subtitle_scale_percent"
        const val SUBTITLE_FOREGROUND = "setting_subtitle_foreground_argb"
        const val SUBTITLE_BACKGROUND_OPACITY = "setting_subtitle_background_opacity_percent"
        const val THEME_MODE = "setting_theme_mode"
        const val LIBRARY_ORDER = "setting_library_order"
        const val DEFAULT_LANGUAGE = "eng"
        const val DEFAULT_SUBTITLE_FOREGROUND = 0xFFFFFFFF
        const val ORDER_DELIMITER = "\n"
    }
}
