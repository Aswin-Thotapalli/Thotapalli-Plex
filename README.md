# Thotapalli Plex

A custom Plex client for Android phone and tablet, Android TV and Google TV, and Windows.

Plex Media Server is the backend. It holds the media, the metadata, the watch state, the user
separation and the transcoder. This repository builds a client and nothing else.

The standing brief is [CLAUDE.md](CLAUDE.md). It is authoritative: scope, fixed product
decisions, architecture, the Plex API contract, the player settings and the build plan all
live there.

## Modules

| Module | Contents |
|---|---|
| `core/model` | Domain types. No platform code, no Plex response shapes. |
| `core/api` | Ktor client for plex.tv and the server, and the mappers onto `core/model`. |
| `core/session` | Sign in, token storage, server and connection selection. |
| `core/data` | Repositories and the SQLDelight cache. |
| `core/playback` | `PlayerEngine`, the timeline and markers. |
| `core/download` | Download queue and offline reconciliation. |
| `ui/design` | Tokens, theme and motion. |
| `ui/shared` | Composables used by all targets. |
| `player/exo` | Media3 engine. Android only. |
| `player/mpv` | libmpv engine through JNA. Windows only. |
| `app/mobile` | Android phone and tablet. |
| `app/tv` | Android TV and Google TV. |
| `app/desktop` | Windows. |

`core/*` never depends on `ui/*` or `app/*`. `player/*` never depends on `ui/*` or `app/*`.
Violations of that direction are defects.

`app/mobile`, `app/tv` and `app/desktop` are separate subprojects that apply exactly one
application plugin and no multiplatform plugin, because Android Gradle Plugin 9 refuses the
multiplatform plugin alongside the application plugin.

## Building

Requirements: JDK 21, and the Android SDK with platform 37 and build tools 37.0.0 for the
Android targets.

```bash
./gradlew build
```

Point the build at your SDK with either an `ANDROID_HOME` environment variable or an
`sdk.dir` line in `local.properties`. `local.properties` is not committed.

Every dependency version is declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
and nowhere else.

### Windows desktop

`app/desktop` needs `libmpv-2.dll` in `app/desktop/native/windows-x64/` before it will run or
package. See [that directory's README](app/desktop/native/windows-x64/README.md). The DLL is
not committed.

## Progress

Against the build plan in CLAUDE.md section 16.

- [x] **Phase 1** — project skeleton
- [x] **Phase 2** — account and server access
- [x] **Phase 3** — library data
- [x] **Phase 4** — shared interface
- [x] **Phase 5** — the player
- [x] **Phase 6** — downloads and offline
- [x] **Phase 7** — television, Windows and release

Two things need a real Plex account and real hardware, and are the only steps not yet
observed end to end:

- Phase 2 steps 2 and 3 need a browser approval. Run
  `gradlew :app:desktop:harness --args="signin"`, approve the device, then `servers`.
- Phase 4 step 3 and phase 5 steps 2 to 8 need a device. The code builds and is unit
  tested; playing a real file is the remaining confirmation.

The JSON fixtures under `core/api/src/commonTest/resources/` were authored from the
documented response shapes rather than recorded from a live server. Replace them with real
recordings once the account is signed in; the mapper tests should keep passing unchanged.

## Verification

```bash
./gradlew build
```

The design gallery, which needs no server:

```bash
./gradlew :app:desktop:gallery
```

The command line harness, which needs an account:

```bash
./gradlew :app:desktop:harness --args="signin"
```
