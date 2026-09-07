package com.thotapalli.plex.player.exo

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatConfig
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The seek-audio fix, pinned: a seek must never destroy the AudioTrack. These drive the provider
 * exactly the way DefaultAudioSink does — get an output, "release" it on flush, get one again —
 * against fake outputs, so the contract holds without a television in the loop.
 */
class TrackKeepingAudioOutputProviderTest {

    @Test
    fun aSeekHandsBackTheSameTrackPausedAndFlushedNotDestroyed() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)
        val config = config()

        val before = provider.getAudioOutput(config)
        before.release() // what the sink does on flush(), i.e. on every seek
        val after = provider.getAudioOutput(config)
        after.play()

        assertEquals(1, fake.created.size, "one AudioTrack for the whole item")
        val track = fake.created.single()
        assertFalse(track.released, "the track survived the seek")
        assertTrue(track.paused && track.flushed, "parked by pause + flush, which resets the tracker")
        assertTrue(track.played, "and simply played again afterwards")
    }

    @Test
    fun aDifferentFormatReleasesTheParkedTrackAndBuildsANewOne() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)

        provider.getAudioOutput(config(sampleRate = 48_000)).release()
        provider.getAudioOutput(config(sampleRate = 44_100))

        assertEquals(2, fake.created.size)
        assertTrue(fake.created[0].released, "a track of the wrong format is let go")
        assertFalse(fake.created[1].released)
    }

    @Test
    fun anOffloadedOutputIsNeverParked() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)

        provider.getAudioOutput(config(offload = true)).release()

        assertTrue(fake.created.single().released)
    }

    @Test
    fun theSinksListenerDoesNotAccumulateAcrossSeeks() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)
        val config = config()
        val first = listener()
        val second = listener()

        provider.getAudioOutput(config).also { it.addListener(first) }.release()
        provider.getAudioOutput(config).addListener(second)

        assertEquals(listOf(second), fake.created.single().listeners)
    }

    @Test
    fun releasingTheProviderReleasesWhateverIsParked() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)

        provider.getAudioOutput(config()).release()
        provider.release()

        assertTrue(fake.created.single().released)
        assertTrue(fake.released)
    }

    @Test
    fun aTrackThatCannotBeFlushedIsReleasedRatherThanParkedBroken() {
        val fake = FakeProvider(flushThrows = true)
        val provider = TrackKeepingAudioOutputProvider(fake)
        val config = config()

        provider.getAudioOutput(config).release()
        provider.getAudioOutput(config)

        assertTrue(fake.created[0].released, "a track that refused to flush is not reused")
        assertEquals(2, fake.created.size)
    }

    @Test
    fun theWrapperForwardsPlaybackToTheRealTrack() {
        val fake = FakeProvider()
        val provider = TrackKeepingAudioOutputProvider(fake)

        val output = provider.getAudioOutput(config())
        output.play()

        assertSame(true, fake.created.single().played)
    }

    // --- fakes ----------------------------------------------------------------------------

    private fun config(sampleRate: Int = 48_000, offload: Boolean = false): OutputConfig =
        OutputConfig.Builder()
            .setEncoding(2)
            .setSampleRate(sampleRate)
            .setChannelMask(12)
            .setBufferSize(8192)
            .setAudioAttributes(AudioAttributes.DEFAULT)
            .setAudioSessionId(7)
            .setIsOffload(offload)
            .build()

    private fun listener(): AudioOutput.Listener = object : AudioOutput.Listener {
        override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) = Unit
        override fun onOffloadDataRequest() = Unit
        override fun onOffloadPresentationEnded() = Unit
        override fun onUnderrun() = Unit
        override fun onReleased() = Unit
    }

    private class FakeProvider(private val flushThrows: Boolean = false) : AudioOutputProvider {
        val created = ArrayList<FakeOutput>()
        var released = false

        override fun getAudioOutput(config: OutputConfig): AudioOutput =
            FakeOutput(flushThrows).also { created += it }

        override fun getFormatSupport(formatConfig: FormatConfig): FormatSupport = throw UnsupportedOperationException()
        override fun getOutputConfig(formatConfig: FormatConfig): OutputConfig = throw UnsupportedOperationException()
        override fun addListener(listener: AudioOutputProvider.Listener) = Unit
        override fun removeListener(listener: AudioOutputProvider.Listener) = Unit
        override fun release() { released = true }
    }

    private class FakeOutput(private val flushThrows: Boolean) : AudioOutput {
        var released = false
        var paused = false
        var flushed = false
        var played = false
        val listeners = ArrayList<AudioOutput.Listener>()

        override fun play() { played = true }
        override fun pause() { paused = true }
        override fun write(buffer: ByteBuffer, sizeInBytes: Int, presentationTimeUs: Long): Boolean = true
        override fun flush() { if (flushThrows) throw IllegalStateException("flush"); flushed = true }
        override fun stop() = Unit
        override fun release() { released = true }
        override fun setVolume(volume: Float) = Unit
        override fun isOffloadedPlayback(): Boolean = false
        override fun getAudioSessionId(): Int = 7
        override fun getSampleRate(): Int = 48_000
        override fun getBufferSizeInFrames(): Long = 2048
        override fun getPositionUs(): Long = 0
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
        override fun isStalled(): Boolean = false
        override fun addListener(listener: AudioOutput.Listener) { listeners += listener }
        override fun removeListener(listener: AudioOutput.Listener) { listeners -= listener }
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
        override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit
        override fun setOffloadEndOfStream() = Unit
        override fun attachAuxEffect(effectId: Int) = Unit
        override fun setAuxEffectSendLevel(level: Float) = Unit
        override fun setPreferredDevice(device: AudioDeviceInfo?) = Unit
    }
}
