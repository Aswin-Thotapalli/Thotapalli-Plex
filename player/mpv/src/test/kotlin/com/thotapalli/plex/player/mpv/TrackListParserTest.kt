package com.thotapalli.plex.player.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * mpv reports its tracks as JSON. player/mpv carries no dependency beyond JNA, so the
 * property is read with a hand written scan; these tests are what make that safe.
 */
class TrackListParserTest {

    private val trackList = """
        [{"id":1,"type":"video","src-id":0,"codec":"hevc","default":true,"selected":true},
         {"id":1,"type":"audio","src-id":1,"title":"Surround","lang":"eng","codec":"eac3",
          "default":true,"selected":true,"audio-channels":6},
         {"id":2,"type":"audio","src-id":2,"title":"Commentary","lang":"eng","codec":"aac",
          "default":false,"selected":false},
         {"id":1,"type":"sub","src-id":3,"lang":"eng","codec":"subrip","default":false,
          "selected":false,"external":true},
         {"id":2,"type":"sub","src-id":4,"lang":"fra","codec":"hdmv_pgs_subtitle",
          "default":false,"selected":false}]
    """.trimIndent()

    @Test
    fun splitsAudioAndSubtitleTracksAndIgnoresVideo() {
        val tracks = parseTrackList(trackList)

        assertEquals(2, tracks.audio.size)
        assertEquals(2, tracks.subtitle.size)
    }

    @Test
    fun readsTheSelectedFlag() {
        val tracks = parseTrackList(trackList)

        assertTrue(tracks.audio.single { it.id == "1" }.selected)
        assertFalse(tracks.audio.single { it.id == "2" }.selected)
        assertTrue(tracks.subtitle.none { it.selected })
    }

    @Test
    fun buildsALabelFromTitleLanguageAndCodec() {
        val tracks = parseTrackList(trackList)

        assertEquals("Surround eng EAC3", tracks.audio.single { it.id == "1" }.label)
        assertEquals("Commentary eng AAC", tracks.audio.single { it.id == "2" }.label)
    }

    @Test
    fun aTrackWithNoTitleStillGetsAUsableLabel() {
        val tracks = parseTrackList(trackList)

        // No title on either subtitle, so the label falls back to language and codec.
        assertEquals("eng SUBRIP", tracks.subtitle.single { it.id == "1" }.label)
        assertEquals("fra HDMV_PGS_SUBTITLE", tracks.subtitle.single { it.id == "2" }.label)
    }

    @Test
    fun carriesTheLanguageThroughForThePreferredLanguageSetting() {
        val tracks = parseTrackList(trackList)

        assertEquals("eng", tracks.audio.first().language)
        assertEquals("fra", tracks.subtitle.last().language)
    }

    @Test
    fun anEmptyListYieldsNoTracks() {
        val tracks = parseTrackList("[]")

        assertTrue(tracks.audio.isEmpty())
        assertTrue(tracks.subtitle.isEmpty())
    }

    @Test
    fun aTrackWithNoTitleOrLanguageFallsBackToItsId() {
        val tracks = parseTrackList("""[{"id":7,"type":"audio","selected":false}]""")

        assertEquals("Track 7", tracks.audio.single().label)
    }
}

class MpvOptionsTest {

    @Test
    fun theSection8OptionsAreSetVerbatim() {
        val options = MPV_OPTIONS.toMap()

        assertEquals("gpu-next", options["vo"])
        assertEquals("d3d11", options["gpu-api"])
        assertEquals("d3d11va", options["hwdec"])
        assertEquals("yes", options["hr-seek"])
        assertEquals("no", options["hr-seek-framedrop"])
        assertEquals("yes", options["keep-open"])
        assertEquals("yes", options["cache"])
        assertEquals("256MiB", options["demuxer-max-bytes"])
        assertEquals("20", options["demuxer-readahead-secs"])
        assertEquals("no", options["sub-auto"])
        assertEquals("no", options["sub-ass-override"])
        assertEquals("0", options["osd-level"])
    }

    @Test
    fun videoSyncIsDisplayResample() {
        // The most important option in section 8. It locks video presentation to the
        // display clock and resamples audio by the small difference, which removes clock
        // drift permanently.
        assertEquals("display-resample", MPV_OPTIONS.toMap()["video-sync"])
    }

    @Test
    fun interpolationIsOff() {
        // Frames stay untouched. Judder is solved by the section 9 mode change instead.
        assertEquals("no", MPV_OPTIONS.toMap()["interpolation"])
    }

    @Test
    fun mpvDrawsNoInterfaceOfItsOwn() {
        val options = MPV_OPTIONS.toMap()

        // Compose draws every control. mpv renders video only.
        assertEquals("no", options["osc"])
        assertEquals("no", options["input-default-bindings"])
        assertEquals("no", options["input-vo-keyboard"])
    }

    @Test
    fun passthroughReachesTheReceiverUntouched() {
        val options = MPV_OPTIONS.toMap()

        assertEquals("yes", options["audio-exclusive"])
        assertEquals("ac3,eac3,dts-hd,truehd", options["audio-spdif"])
    }
}
