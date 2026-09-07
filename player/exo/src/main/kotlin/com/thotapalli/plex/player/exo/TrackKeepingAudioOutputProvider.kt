package com.thotapalli.plex.player.exo

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider

/**
 * Keeps the AudioTrack alive across seeks.
 *
 * Media3's DefaultAudioSink handles a seek with flush(), and flush() ends by calling release() on
 * its AudioOutput: the AudioTrack is destroyed and a new one is created for the next write. For a
 * PCM track that costs milliseconds. For a Dolby or DTS bitstream track over HDMI it costs a fresh
 * format negotiation and the television or receiver re-locking onto the stream — several seconds of
 * picture with no sound after every seek, tunnelled or not. That is the whole of the seek-audio gap.
 *
 * The sink asks its AudioOutputProvider for an output by an OutputConfig (encoding, sample rate,
 * channel mask, session, attributes, buffer size…), which is identical before and after a seek
 * within the same item. So this provider never lets a seek destroy the track: when the sink says
 * "release", the track is paused, flushed (which also resets the position tracker) and parked; when
 * the sink next asks for an output with the same config, it gets the parked one back and simply
 * calls play(). A request for a different config — a new item with a different format — releases
 * the parked track and builds a new one, and releasing the provider releases whatever is parked.
 * Offloaded outputs are never parked; offload has its own lifecycle.
 *
 * Correctness rests on three facts read from the 1.11 bytecode, not assumed: AudioTrackAudioOutput
 * .flush() is AudioTrack.flush() plus a tracker reset; .play() restarts the tracker and the track;
 * OutputConfig implements equals. See ExoPlayerEngine.buildPlayer and CLAUDE.md sections 8 and 18.
 */
@UnstableApi
internal class TrackKeepingAudioOutputProvider(
    private val delegate: AudioOutputProvider,
) : ForwardingAudioOutputProvider(delegate) {

    private class Parked(val config: OutputConfig, val output: AudioOutput)

    private var parked: Parked? = null

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val held = parked
        parked = null
        if (held != null) {
            if (held.config == config) return Kept(held.output, config)
            // A different format: the parked track is of no use, let it go.
            held.output.release()
        }
        return Kept(delegate.getAudioOutput(config), config)
    }

    override fun release() {
        parked?.output?.release()
        parked = null
        super.release()
    }

    /** The sink's view of an output; "release" means "park" unless the track cannot be kept. */
    private inner class Kept(
        private val inner: AudioOutput,
        private val config: OutputConfig,
    ) : ForwardingAudioOutput(inner) {

        // The sink adds its listener on every initialisation. Removing what was added through this
        // wrapper when it is parked keeps the next initialisation from listening twice.
        private val listeners = ArrayList<AudioOutput.Listener>(2)

        override fun addListener(listener: AudioOutput.Listener) {
            listeners += listener
            super.addListener(listener)
        }

        override fun removeListener(listener: AudioOutput.Listener) {
            listeners -= listener
            super.removeListener(listener)
        }

        override fun release() {
            listeners.forEach { inner.removeListener(it) }
            listeners.clear()
            if (config.isOffload) {
                inner.release()
                return
            }
            val parkedOk = runCatching {
                inner.pause()
                inner.flush()
            }.isSuccess
            if (!parkedOk) {
                inner.release()
                return
            }
            // Never hold two: if something is already parked, this one is the newer and wins.
            parked?.output?.release()
            parked = Parked(config, inner)
        }
    }
}
