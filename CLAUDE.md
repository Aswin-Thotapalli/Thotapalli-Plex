# Thotapalli Plex

A custom Plex client for Android phone and tablet, Android TV and Google TV, and Windows.
This file is the standing brief. It is authoritative. Do not invent alternatives to anything stated here.

Plex Media Server is the backend. It holds the media, the metadata, the watch state, the user
separation and the transcoder. This project builds a client and nothing else. No web hosting,
no application server and no database service are required.

---

## 1. Scope

### In scope

1. Browse every library the server exposes, by library, alphabetical by title.
2. Continue watching across devices, driven by server-side resume positions.
3. Search across all libraries from one field.
4. Play video with correct frame pacing. Direct play preferred, transcode as a silent fallback.
5. Download for offline playback, with watch state reconciled on reconnection.

### Permanently out of scope

Do not add these, do not scaffold them, do not leave hooks for them.

- Recently added rows and any other discovery surface
- Ratings, reviews and user scoring
- Watchlist
- Plex Discover, Live TV, news, Plex-hosted free content
- Music libraries and photo libraries
- Casting to other devices, Watch Together

---

## 2. Fixed product decisions

| Decision | Value |
|---|---|
| Application name | Thotapalli Plex |
| Android application ID | `com.thotapalli.plex` |
| Windows application ID | `com.thotapalli.plex.desktop` |
| Account model | Separate Plex accounts with a shared library |
| Sign-in persistence | One account per device, remembered until sign-out |
| Library discovery | Read from the server at runtime, never hardcoded |
| Library sort | Title, ascending, alphabetical |
| Downloads | In scope, all three targets |
| Transcode fallback | Silent, with a non-intrusive notice |
| Auto-play next episode | 10 second countdown, cancellable |
| Intro markers | Skip button shown while the marker is active |
| Credit markers | Automatic skip into the next episode |
| Resume behaviour | Resume immediately, no prompt |
| Collections | Shown inside the library that owns them |
| Minimum Android version | Android 12, API level 31 |
| Theme | Light and dark, following the system setting |

### Account model detail

The library owner grants a separate Plex account access to selected libraries. Each account
holds its own credentials, watch history and resume positions. There is no profile picker.
Separation of watch state is entirely server-side.

The client still calls `GET https://plex.tv/api/v2/home/users` after sign-in. More than one
Home user shows a picker. One Home user skips the picker silently.

---

## 3. Architecture

### Framework

Kotlin Multiplatform with Compose Multiplatform.

The choice is driven by the player. Toolkits that render video through a platform view hand the
decoded frame to a texture and composite it inside their own engine, which adds latency and
couples video presentation to interface redraws. Compose on Android sits in the ordinary Android
view hierarchy, so a `SurfaceView` goes beneath the Compose overlay and the decoder writes
directly to a hardware layer the compositor scans out.

### Module structure

Android Gradle Plugin 9 does not permit the Kotlin Multiplatform plugin and the Android
Application plugin in the same Gradle subproject. The application modules are separate subprojects.

```
thotapalli-plex/
  gradle/libs.versions.toml        version catalogue, single source of truth
  core/
    model/                         domain types, no platform code
    api/                           Ktor client, plex.tv and server endpoints
    session/                       sign-in, token storage, server selection
    data/                          repositories, SQLDelight cache
    playback/                      PlayerEngine interface, timeline, markers
    download/                      download queue, offline reconciliation
  ui/
    design/                        tokens, theme, motion specifications
    shared/                        Composables used by all targets
  player/
    exo/                           Media3 implementation, Android only
    mpv/                           libmpv implementation, JVM desktop only
  app/
    mobile/                        Android phone and tablet
    tv/                            Android TV and Google TV
    desktop/                       Windows
```

### Dependency direction

Violations of this are defects.

```
app:*      ->  ui:shared  ->  ui:design
app:*      ->  core:data  ->  core:api  ->  core:model
app:*      ->  core:playback  ->  core:model
player:exo ->  core:playback
player:mpv ->  core:playback

core:*   never depends on ui:* or app:*
player:* never depends on ui:* or app:*
```

---

## 4. Toolchain and versions

Verified against publishers on 15 August 2026. The version catalogue is the only place versions
are declared. No module declares a version inline.

| Component | Version |
|---|---|
| JDK | 21 |
| Gradle | 9.5.0 |
| Android Gradle Plugin | 9.3.0 |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| compileSdk / targetSdk | 37 |
| minSdk | 31 |

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.0"
kotlin = "2.4.10"
composeMultiplatform = "1.11.1"
composeBom = "2026.04.01"
media3 = "1.11.0"
ktor = "3.4.0"
sqldelight = "2.3.2"
coil = "3.5.0"
jna = "5.17.0"
okio = "3.9.1"

[libraries]
ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-java = { module = "io.ktor:ktor-client-java", version.ref = "ktor" }
ktor-contentneg = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

sqldelight-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-jvm = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }

coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }

media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-ui-compose = { module = "androidx.media3:media3-ui-compose", version.ref = "media3" }
media3-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-ds-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
media3-ffmpeg = { module = "androidx.media3:media3-decoder-ffmpeg", version.ref = "media3" }

jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### Native library for Windows

1. Source the 64 bit official Windows build of libmpv from
   `https://sourceforge.net/projects/mpv-player-windows/files/libmpv/`
2. Place `libmpv-2.dll` at `app/desktop/native/windows-x64/` and register that directory on the
   JNA library search path at start-up.
3. Bundle the DLL through the Compose Desktop `nativeDistributions` block so the user performs
   no separate install step.

---

## 5. Plex API contract

### Identity headers

Sent on every request to both hosts. The client identifier is generated once on first launch and
persisted forever. Changing it creates a duplicate device entry on the account.

```
X-Plex-Client-Identifier: <UUID v4, generated once, persisted>
X-Plex-Product:           Thotapalli Plex
X-Plex-Version:           <application version name>
X-Plex-Platform:          Android | Windows
X-Plex-Platform-Version:  <OS version string>
X-Plex-Device:            <device model>
X-Plex-Device-Name:       <user visible device name>
X-Plex-Session-Identifier: <UUID v4, per playback session>
X-Plex-Token:             <account or server access token>
Accept:                   application/json
```

### Sign-in, the PIN flow

1. `POST https://plex.tv/api/v2/pins?strong=true` with identity headers. Response holds `id` and `code`.
2. Open `https://app.plex.tv/auth#?clientID=<client identifier>&code=<code>&context%5Bdevice%5D%5Bproduct%5D=Thotapalli%20Plex`
   in the system browser.
3. `GET https://plex.tv/api/v2/pins/<id>` every 1000 ms, stopping after 300 s. `authToken` stays
   null until approval. A non-null `authToken` ends the poll.
4. Persist `authToken` encrypted. Android: `EncryptedSharedPreferences`. Windows: DPAPI through
   JNA with `CryptProtectData` scoped to the current user.
5. `GET https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1` with the account token.
   Filter to entries whose `provides` contains `server`.

A shared library appears with `owned` set to false. The `accessToken` on that entry is used for
every request to that server. The account token is never sent to the server.

### Connection selection

1. Probe every connection in parallel: `GET <uri>/identity`, 3000 ms timeout, server access token.
2. Rank successes: prefer `local` true, then `relay` false, then lowest round trip.
3. Cache the winner against the server machine identifier for 30 minutes, then re-probe.
4. Re-probe immediately on a device network change.

### Server endpoints

| Purpose | Method and path |
|---|---|
| Server identity | `GET /identity` |
| List libraries | `GET /library/sections` |
| Library contents | `GET /library/sections/{key}/all?type={1\|2}&sort=titleSort:asc` |
| Collections | `GET /library/sections/{key}/collections` |
| Item metadata | `GET /library/metadata/{ratingKey}?includeMarkers=1&includeChapters=1` |
| Children | `GET /library/metadata/{ratingKey}/children` |
| All episodes of a show | `GET /library/metadata/{ratingKey}/allLeaves` |
| Continue watching | `GET /hubs/continueWatching/items` |
| On deck fallback | `GET /library/onDeck` |
| Search | `GET /hubs/search?query={q}&limit=50` |
| Playback decision | `GET /video/:/transcode/universal/decision` |
| Direct file | `GET /library/parts/{partId}/{updatedAt}/file.{ext}` |
| Transcode stream | `GET /video/:/transcode/universal/start.m3u8` |
| Progress report | `GET /:/timeline` |
| Mark watched | `GET /:/scrobble?key={ratingKey}&identifier=com.plexapp.plugins.library` |
| Mark unwatched | `GET /:/unscrobble?key={ratingKey}&identifier=com.plexapp.plugins.library` |
| Trickplay thumbnail | `GET /library/parts/{partId}/indexes/sd/{offsetMs}` |
| Artwork | `GET /photo/:/transcode?width={w}&height={h}&minSize=1&upscale=1&url={encoded}` |
| Home users | `GET https://plex.tv/api/v2/home/users` |
| Switch home user | `POST https://plex.tv/api/v2/home/users/{uuid}/switch` |

Type codes: `1` movie, `2` show, `3` season, `4` episode, `18` collection.

### Progress reporting

```
GET /:/timeline
  ?ratingKey={ratingKey}
  &key=/library/metadata/{ratingKey}
  &state={playing|paused|stopped}
  &time={position ms}
  &duration={duration ms}
  &hasMDE=1
  &X-Plex-Session-Identifier={session uuid}
```

1. Every 10000 ms while `state` is `playing`.
2. Immediately on play, pause, seek completion and stop.
3. Send `state=stopped` before releasing the player. Block release for at most 2000 ms waiting.
4. Past 92 percent of duration, call scrobble once and stop sending timeline updates for that item.

---

## 6. Domain model

Lives in `core/model`, with no dependency on Plex response shapes. `core/api` maps Plex responses
onto these types so a Plex-side change touches one layer.

```kotlin
data class PlexServer(
    val machineIdentifier: String,
    val name: String,
    val accessToken: String,
    val owned: Boolean,
    val connections: List<ServerConnection>,
)

data class ServerConnection(val uri: String, val local: Boolean, val relay: Boolean)

data class Library(val key: String, val title: String, val kind: LibraryKind, val uuid: String)

sealed interface MediaItem {
    val ratingKey: String
    val title: String
    val year: Int?
    val summary: String
    val thumbPath: String?
    val artPath: String?
    val durationMs: Long
    val viewOffsetMs: Long
    val viewCount: Int
}

data class Movie(...) : MediaItem
data class Show(val childCount: Int, val leafCount: Int, ...) : MediaItem
data class Season(val showRatingKey: String, val index: Int, ...) : MediaItem
data class Episode(
    val showRatingKey: String,
    val seasonRatingKey: String,
    val showTitle: String,
    val seasonIndex: Int,
    val episodeIndex: Int,
    ...
) : MediaItem

data class MediaPart(
    val partId: String,
    val fileKey: String,
    val container: String,
    val sizeBytes: Long,
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val subtitleStreams: List<SubtitleStream>,
)

data class Marker(val type: MarkerType, val startMs: Long, val endMs: Long)
enum class MarkerType { INTRO, CREDITS }
```

---

## 7. Local database

A cache and the offline queues. Safe to delete. Never the source of truth for anything the
server also knows.

```sql
CREATE TABLE library (
  key TEXT NOT NULL PRIMARY KEY,
  title TEXT NOT NULL,
  kind TEXT NOT NULL,
  server_id TEXT NOT NULL,
  refreshed_at INTEGER NOT NULL
);

CREATE TABLE media_item (
  rating_key TEXT NOT NULL PRIMARY KEY,
  library_key TEXT NOT NULL,
  parent_key TEXT,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  title_sort TEXT NOT NULL,
  year INTEGER,
  summary TEXT NOT NULL DEFAULT "",
  thumb_path TEXT,
  art_path TEXT,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  view_offset_ms INTEGER NOT NULL DEFAULT 0,
  view_count INTEGER NOT NULL DEFAULT 0,
  season_index INTEGER,
  episode_index INTEGER,
  refreshed_at INTEGER NOT NULL
);

CREATE INDEX media_item_library ON media_item(library_key, title_sort);
CREATE INDEX media_item_parent  ON media_item(parent_key, season_index, episode_index);

CREATE TABLE download (
  rating_key TEXT NOT NULL PRIMARY KEY,
  part_id TEXT NOT NULL,
  local_path TEXT NOT NULL,
  total_bytes INTEGER NOT NULL,
  received_bytes INTEGER NOT NULL DEFAULT 0,
  state TEXT NOT NULL,
  queued_at INTEGER NOT NULL
);

CREATE TABLE download_subtitle (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rating_key TEXT NOT NULL,
  stream_id TEXT NOT NULL,
  language TEXT NOT NULL,
  local_path TEXT NOT NULL
);

CREATE TABLE pending_timeline (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rating_key TEXT NOT NULL,
  position_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL,
  state TEXT NOT NULL,
  recorded_at INTEGER NOT NULL
);
```

`pending_timeline` exists for offline playback. Progress recorded while the server is unreachable
is written there and replayed on reconnection. Only the newest row per rating key is sent, since
Plex stores a position rather than a history.

---

## 8. Player

The player is the reason this project exists. Every setting below is deliberate. None of it is
default behaviour.

### Shared interface

```kotlin
interface PlayerEngine {
    val state: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>

    fun load(source: PlaybackSource, startAtMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setScrubbing(active: Boolean)
    fun selectAudioTrack(id: String)
    fun selectSubtitleTrack(id: String?)
    fun release()
}

data class PlaybackSource(
    val uri: String,
    val mode: PlaybackMode,      // DIRECT or TRANSCODE
    val headers: Map<String, String>,
    val frameRate: Float?,
)
```

### Media3 setup, Android and Android TV

```kotlin
val renderers = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    .setEnableDecoderFallback(true)

val trackSelector = DefaultTrackSelector(context).apply {
    parameters = buildUponParameters()
        .setTunnelingEnabled(isTelevision)
        .setPreferredAudioLanguages("eng")
        .build()
}

val player = ExoPlayer.Builder(context, renderers)
    .setTrackSelector(trackSelector)
    .setSeekBackIncrementMs(10_000)
    .setSeekForwardIncrementMs(30_000)
    .experimentalSetDynamicSchedulingEnabled(true)
    .build()
```

1. **Tunnelling** on television devices only. Detect with `UiModeManager` and
   `UI_MODE_TYPE_TELEVISION`. It routes decoded video around the application so hardware performs
   audio and video synchronisation.
2. **Extension renderers preferred** so the bundled FFmpeg decoder handles audio formats the
   device decoder rejects. This avoids a server transcode triggered by audio alone.
3. **Dynamic scheduling** reduces playback loop wake-ups. Experimental, added in Media3 1.10.
4. **Scrubbing mode**: call `setScrubbingModeEnabled(true)` on seek bar drag start and `false` on
   drag end. Added in Media3 1.8.

### Surface rules, absolute

1. Use `SurfaceView`. `TextureView` is never used anywhere in this codebase.
2. Never redraw the surface for interface changes. Controls are Compose content in a layer above it.
3. Never place the surface inside a scrolling or animating container.
4. Keep the surface alive across control visibility changes. Showing or hiding controls never
   recreates the player or the surface.

### libmpv setup, Windows

Set through `mpv_set_option_string` before `mpv_initialize`.

```
vo                = gpu-next
gpu-api           = d3d11
hwdec             = d3d11va
video-sync        = display-resample
interpolation     = no
hr-seek           = yes
hr-seek-framedrop = no
keep-open         = yes
cache             = yes
demuxer-max-bytes = 256MiB
demuxer-readahead-secs = 20
audio-exclusive   = yes
audio-spdif       = ac3,eac3,dts-hd,truehd
sub-auto          = no
sub-ass-override  = no
osc               = no
osd-level         = 0
input-default-bindings = no
input-vo-keyboard = no
```

1. `video-sync=display-resample` locks video presentation to the display clock and resamples
   audio by the small difference. This removes clock drift permanently. It is the most important
   option in the list.
2. `interpolation=no` keeps frames untouched. Judder is solved by section 9 instead.
3. `osc=no` and disabled input bindings because Compose draws the interface. mpv renders video only.
4. `audio-exclusive=yes` so bitstream passthrough reaches the receiver untouched.

---

## 9. Refresh rate and frame pacing

Two separate problems with different causes and different solutions.

### Drift

Display clock and media clock run at slightly different speeds. The player repeats or drops a
frame to stay in step, producing a hitch roughly once a minute.

Solved by locking playback to the display clock and correcting in audio. Windows:
`video-sync=display-resample`. Android: Media3 performs the equivalent on frame release.
Always on, every platform, no user setting.

### Judder

A refresh rate that does not divide evenly by the content frame rate forces an uneven cadence.
24 fps on 60 Hz shows frames for 2 refreshes, then 3, then 2, then 3. Syncing does not help.

| Content rate | Refreshes per frame at 120 Hz | Result |
|---|---|---|
| 23.976 | 5, with slow drift | Correct after drift handling |
| 24 | 5 | Correct |
| 25 | 4.8 | Uneven, needs 100 or 50 Hz |
| 29.97 and 30 | 4 | Correct |
| 50 | 2.4 | Uneven, needs 100 Hz |
| 59.94 and 60 | 2 | Correct |

### The rule

1. Read the content frame rate from the video stream metadata before playback starts.
2. Read the current display mode and its refresh rate.
3. Divide refresh rate by content frame rate. Within 0.5 percent of a whole number means the
   current mode is already correct. Change nothing and begin playback.
4. Otherwise search available display modes for one that divides evenly, preferring the highest
   such rate at the current resolution.
5. Apply the mode and hold the first frame until the surface reports the new rate. An HDMI mode
   change blanks the screen for one to two seconds.

```kotlin
surface.setFrameRate(
    contentFrameRate,
    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
    Surface.CHANGE_FRAME_RATE_ALWAYS,
)

val modes = display.supportedModes
    .filter { it.physicalWidth == currentMode.physicalWidth }
    .filter { divides(it.refreshRate, contentFrameRate) }
window.attributes = window.attributes.apply {
    preferredDisplayModeId = modes.maxByOrNull { it.refreshRate }?.modeId ?: 0
}
```

Windows sets the mode through `ChangeDisplaySettingsEx` with a `DEVMODE` carrying
`dmDisplayFrequency`.

**Setting**: one toggle named "Match display rate to content". Defaults on for television,
off for phone and Windows.

---

## 10. Playback decision and transcode fallback

1. Request the decision endpoint with the capability parameters below.
2. Attempt direct play when the decision permits it. Build the direct file URL from the part id.
3. Failure means a decoder initialisation error, an unsupported track error, or no rendered
   frame within 8000 ms.
4. On Android phone and tablet, a Media3 failure retries once through libmpv before any transcode.
5. Fall back to transcode silently. Request the HLS stream, seek to the same position, resume.
6. Show a small chip in the lower left reading "Transcoding". It fades after 4000 ms, never
   blocks the picture and never requires dismissal.

```
containers:  mkv, mp4, mov, avi, ts, m2ts, webm
video:       h264, hevc, av1, vp9, mpeg2video, vc1
audio:       aac, ac3, eac3, dts, truehd, flac, mp3, opus, vorbis, pcm
subtitles:   srt, ass, ssa, pgs, vobsub, dvb_subtitle
```

Narrow the audio section on Android when the device reports no passthrough support.

---

## 11. Downloads and offline

| Target | Location |
|---|---|
| Android phone and tablet | `context.getExternalFilesDir("downloads")` |
| Android TV and Google TV | `context.getExternalFilesDir("downloads")` |
| Windows | `%LOCALAPPDATA%\ThotapalliPlex\downloads` |

### Rules

1. **Download the original file.** Request the direct file URL, never a transcode.
2. **Use ranged requests.** 8 megabyte segments so an interrupted download resumes from the
   received byte count.
3. **One item at a time.** A serial queue keeps the connection responsive for concurrent playback.
4. **External subtitles download separately** into `download_subtitle`. Embedded tracks need
   no action.
5. **Verify on completion.** Compare received bytes against the server-reported size. A mismatch
   marks the row failed and deletes the partial file.
6. **Network setting**: "Download on unmetered networks only", defaulting on for Android and
   off for Windows.

### Offline watch state

1. Collapse the queue: keep the newest row per rating key, discard the rest.
2. Replay oldest first as ordinary timeline requests.
3. Resolve conflicts by recency, comparing `recorded_at` against server `lastViewedAt`.
4. Delete rows only after the server accepts them.

---

## 12. Design system

Poster artwork carries the colour. The interface stays quiet around it. One accent colour marks
selection and focus, and nothing else.

| Token | Dark | Light |
|---|---|---|
| background | `#0E0F12` | `#FAFAFA` |
| surface | `#16181C` | `#FFFFFF` |
| surfaceElevated | `#1E2126` | `#FFFFFF` |
| border | `#2A2E35` | `#E2E4E8` |
| textPrimary | `#F2F3F5` | `#16181C` |
| textSecondary | `#A8AEB8` | `#5A616B` |
| accent | `#F5A623` | `#C97C05` |
| focusRing | `#F5A623` | `#C97C05` |
| scrim | `#000000` 60% | `#000000` 45% |
| error | `#E5534B` | `#C0392B` |

The player screen ignores the light theme and always renders on the dark tokens.

| Role | Phone and Windows | Television |
|---|---|---|
| display | 28sp semibold | 40sp semibold |
| title | 20sp semibold | 28sp semibold |
| body | 16sp regular | 22sp regular |
| label | 14sp medium | 18sp medium |
| caption | 12sp regular | 16sp regular |

1. **Spacing scale**: 4, 8, 12, 16, 24, 32, 48 dp. No other value.
2. **Corner radius**: 8 poster tiles, 12 cards and dialogues, 16 sheets, 999 pills.
3. **Enter motion**: 150 ms standard easing. **Exit**: 100 ms accelerate easing.
4. **Player control fade**: 200 ms in, 200 ms out, after 3000 ms of no input.
5. **Television focus**: scale 1.08 over 120 ms with a 3 dp accent focus ring.

### Player overlay

1. **Idle**: nothing on screen. No bar, no clock, no logo, no title.
2. **Active**: a bottom gradient scrim, the title, a progress bar, transport controls. Nothing else.
3. **Trigger**: any pointer movement, touch, or remote key press.
4. **Dismissal**: automatic after 3000 ms of no input, immediate on back press.
5. **Seek preview**: dragging shows a trickplay thumbnail above the handle in a 12 radius card
   with no border.

---

## 13. Responsive layout

Layout adapts to available space rather than to a list of known devices.

```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = posterMinWidth),
    contentPadding = screenPadding,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
)
```

`posterMinWidth`: Compact 132.dp, Medium 152.dp, Expanded 176.dp, Television 200.dp.

| Window width | Class | Navigation | Detail screen |
|---|---|---|---|
| Below 600dp | Compact | Bottom bar | Single column |
| 600dp to 839dp | Medium | Side rail | Single column |
| 840dp and above | Expanded | Side rail | Two pane |
| Television | Television | Top row | Two pane |

1. Every dimension in dp. No raw pixel value anywhere in the codebase.
2. On Windows the window drives the size class, recomputed during the resize drag.
3. Content width capped at 1600dp. Beyond that the grid adds columns and the cap keeps content
   centred. Posters never stretch.
4. Two pane detail: poster and metadata left at 380dp fixed, season and episode list filling
   the remainder.

### Television input

1. Every focusable element carries an explicit focus order.
2. Overscan margin 5 percent on all four edges of every television screen.
3. Request first focus on entry to every screen. No screen opens with nothing focused.
4. Back moves up one level and never exits the application from below the home screen.
5. Long press on the directional pad seeks at 30 seconds per 400 ms while held.

---

## 14. Screens

1. **Sign in.** Application mark, one sentence, one button reading "Sign in with Plex". Then a
   progress indicator and a cancel action. Timeout returns to the initial state with a short message.
2. **Home.** Continue Watching as a horizontal row of wide progress tiles, then one card per
   library. Nothing else. An empty Continue Watching row is hidden and the library cards move up.
3. **Library.** Poster grid sorted alphabetically. Collections first with a stacked poster
   treatment, then individual titles. One filter: "Unwatched only". Sort is fixed with no control.
4. **Movie detail.** Backdrop behind a scrim, poster, title, year, duration, summary. Primary
   action Play or Resume. Secondary actions Download and Mark as watched. Audio and subtitle
   tracks listed for reference.
5. **Show detail.** Same header. Season selector, then the episode list. Each row carries
   thumbnail, number, title, duration and a progress bar when partially watched. Primary action
   plays the next unwatched episode.
6. **Search.** One field. Requests after 300 ms of no typing, minimum two characters. Results
   grouped under Movies, Shows and Episodes, at most 20 per group.
7. **Player.** Full bleed video with the overlay above. Transport row left to right: play and
   pause, seek back 10 s, seek forward 30 s, position, progress bar, duration, audio selector,
   subtitle selector, full screen toggle on Windows. Next episode prompt appears lower right
   during the credits marker or the final 30 seconds, with a 10 second countdown cancelled by
   any input.
8. **Downloads.** List of downloaded and queued items with title, size and state. Active rows
   show progress and a pause action. Completed rows show delete. Total space used at the top.
9. **Settings.** Match display rate to content. Download on unmetered networks only. Preferred
   audio language. Preferred subtitle language. Subtitles on by default. Server selection when
   more than one server exists. Sign out.

---

## 15. Branding

The mark is a rounded plate carrying a horizontal bar above a play triangle. The two forms read
as the letter T and as a play control. Original artwork, no relationship to the Plex mark.

| Asset | Specification |
|---|---|
| Source file | `logo.svg`, 512 by 512 viewBox |
| Plate colour | Gradient `#262A30` to `#101215` |
| Mark colour | Gradient `#FFCE63` through `#F5A623` to `#D4820C` |
| Android launcher | Adaptive icon, foreground and background layers, 108dp |
| Android TV banner | 320 by 180 px, mark left, wordmark right |
| Windows icon | ICO with 16, 32, 48, 64, 128, 256 px sizes |
| Wordmark typeface | Inter SemiBold, letter spacing -1% |

---

## 16. Build plan

Phases run in order. Each step states its expected output. A step is complete when that output
exists and is verified. No step is skipped and no step is combined with another.

### Phase 1, project skeleton

1. Create the Gradle project with the module structure in section 3.
   *Expected output*: `settings.gradle.kts` listing every module, `./gradlew build` succeeding
   with no source files.
2. Write the version catalogue from section 4 verbatim.
   *Expected output*: `./gradlew dependencies` resolving every declared coordinate.
3. Set the Gradle wrapper to 9.5.0 and the toolchain to JDK 21.
   *Expected output*: `./gradlew --version` reporting Gradle 9.5.0.
4. Confirm the Android application modules are separate subprojects from the KMP modules.
   *Expected output*: `app/mobile`, `app/tv`, `app/desktop` each applying one application plugin
   and no multiplatform plugin.

### Phase 2, account and server access

1. Implement the identity header provider in `core/session`.
   *Expected output*: a unit test asserting the client identifier survives a simulated restart.
2. Implement the PIN sign-in flow from section 5.
   *Expected output*: a command line harness printing an account token after browser approval.
3. Implement server discovery and connection selection.
   *Expected output*: the harness printing selected server name, chosen connection URI and
   whether it is local.
4. Implement encrypted token storage for both platforms.
   *Expected output*: the harness reading back a stored token after restart on Android and Windows.

### Phase 3, library data

1. Implement the Plex response mappers in `core/api`.
   *Expected output*: unit tests mapping recorded JSON fixtures onto the section 6 domain model.
2. Create the SQLDelight schema from section 7.
   *Expected output*: generated query classes compiling on Android and JVM.
3. Implement the repositories with cache-first read and background refresh.
   *Expected output*: a test asserting a second read returns from the database with no network call.
4. Implement search and continue watching.
   *Expected output*: the harness printing continue watching entries with resume positions.

### Phase 4, shared interface

1. Build the design token layer in `ui/design` with light and dark bound to the system setting.
   *Expected output*: a preview rendering every token in both schemes.
2. Build the shared components: poster tile, wide progress tile, episode row, library card,
   section header.
   *Expected output*: previews of each at Compact, Medium, Expanded and Television sizes.
3. Build Home, Library, Movie detail, Show detail and Search against the repositories.
   *Expected output*: all five screens driven by real server data on Android phone.
4. Apply the responsive rules from section 13.
   *Expected output*: the library grid changing column count on window resize on Windows and on
   tablet rotation.

### Phase 5, the player

1. Define `PlayerEngine` in `core/playback`.
   *Expected output*: the interface compiling with no platform dependency.
2. Implement the Media3 engine in `player/exo` with the section 8 setup and surface rules.
   *Expected output*: direct play of a file from the server on Android phone.
3. Implement frame rate matching from section 9.
   *Expected output*: a log line reporting content rate, display rate before and display rate
   after, on a television device.
4. Implement the libmpv engine in `player/mpv`.
   *Expected output*: direct play of the same file on Windows.
5. Implement the overlay including trickplay seek previews.
   *Expected output*: the overlay appearing and disappearing on the correct triggers with the
   video surface never redrawn.
6. Implement timeline reporting.
   *Expected output*: a position set on one device appearing as the resume point on another.
7. Implement markers and auto-play.
   *Expected output*: an intro skip button, an automatic credit skip, a cancellable next episode
   countdown.
8. Implement the transcode fallback from section 10.
   *Expected output*: a forced decoder failure producing continuous playback with the transcoding
   chip shown once.

### Phase 6, downloads

1. Implement the download queue.
   *Expected output*: an interrupted download resuming from its received byte count.
2. Implement offline playback resolution.
   *Expected output*: full playback in aeroplane mode.
3. Implement offline watch state.
   *Expected output*: a position recorded offline appearing on the server after reconnection.

### Phase 7, television and Windows

1. Build the television application module with the section 13 focus rules.
   *Expected output*: every screen navigable using a directional pad alone with no dead ends.
2. Build the Windows application module with keyboard and pointer input.
   *Expected output*: every screen operable with keyboard alone.
3. Produce the branding assets from section 15.
   *Expected output*: adaptive icon, television banner and Windows ICO in the correct resource
   directories.
4. Produce release artefacts.
   *Expected output*: a signed APK for phone, a signed APK for television, an MSI for Windows.

---

## 17. Distribution

1. **Signing.** One release keystore generated and stored outside the repository. The same key
   signs phone and television builds so upgrades install over the previous version.
2. **Hosting.** GitHub Releases. No server required.
3. **Update manifest.** A static JSON file at a fixed release URL listing latest version code,
   version name and artefact URL per target.
4. **Update check.** Fetched at launch, at most once per 24 hours. A newer version code shows a
   single non-blocking notice with a download action.
5. **Windows installation.** The Compose Desktop `packageMsi` task. The libmpv DLL is bundled inside.

---

## 18. Constraints and known limits

1. **Remote playback requires a Plex subscription.** Plex requires a Plex Pass on the server owner
   account or a Remote Watch Pass on the viewing account for playback outside the home network,
   and this applies to third party clients using the API. Both accounts on this deployment hold a
   subscription, so this is a recorded constraint rather than a task.
2. **The Plex API carries no compatibility guarantee.** Response mapping is isolated inside
   `core/api` so a Plex-side change touches one module.
3. **Passthrough depends on the device.** Bitstream output for Dolby and DTS formats requires the
   device to report the capability. Where absent, decode locally and output PCM.
4. **Display mode changes blank the screen briefly.** A property of the HDMI link. The section 9
   setting exists so the behaviour can be switched off.
5. **Television and phone are separate artefacts.** They share every module below the application
   layer and differ only in navigation, focus and type scale.

---

## Working rules for this repository

1. Read this file before every task. It overrides assumptions from general Android or Plex knowledge.
2. Never introduce a dependency without adding it to `gradle/libs.versions.toml` first.
3. Never use `TextureView`, `localStorage`-style ad hoc persistence, or raw pixel dimensions.
4. Never add a screen, row or feature listed as out of scope in section 1.
5. When a Plex response shape is unclear, record a real fixture from the server into
   `core/api/src/commonTest/resources/` and write the mapper against it.
6. Report progress against the numbered steps in section 16 by their phase and step number.
