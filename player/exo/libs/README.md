# player/exo/libs

The Media3 FFmpeg audio decoder AAR lands here as `lib-decoder-ffmpeg-release.aar`.

It is **built and committed by CI**, not by hand:
[`.github/workflows/ffmpeg-decoder.yml`](../../../.github/workflows/ffmpeg-decoder.yml)
checks out the Media3 source at the version in `gradle/libs.versions.toml`, builds the
FFmpeg decoder with the audio codecs listed in [`../ffmpeg-codecs.txt`](../ffmpeg-codecs.txt)
using the NDK, and commits the resulting AAR into this directory.

`player/exo/build.gradle.kts` picks up any `*.aar` here. The AAR is therefore optional at
build time: a fresh checkout before the workflow has run still builds, and unsupported audio
falls back to a server transcode until the AAR is present. Once it is committed, every
build — local and CI — decodes those formats on the device instead.

Google publishes no binary of this decoder to any Maven repository, which is why it is built
rather than declared as an ordinary dependency.
