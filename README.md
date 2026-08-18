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
- [ ] **Phase 2** — account and server access
- [ ] **Phase 3** — library data
- [ ] **Phase 4** — shared interface
- [ ] **Phase 5** — the player
- [ ] **Phase 6** — downloads and offline
- [ ] **Phase 7** — television, Windows and release
