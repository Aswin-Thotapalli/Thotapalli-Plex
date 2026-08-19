# Releasing

The distribution machinery from CLAUDE.md section 17, and the expected output of section 16
phase 7 step 4: a signed APK for phone, a signed APK for television, and an MSI for Windows.

Everything is produced by `.github/workflows/release.yml`. There is no server, no store listing
and no hosting to maintain. GitHub Releases holds the artefacts and one static JSON manifest.

---

## 1. The release keystore

One keystore signs the phone artefact and the television artefact. They carry the same
application id, `com.thotapalli.plex`, so one key is what lets either build install over the
other. Two keys would make the second install fail with a signature mismatch.

**The keystore never enters this repository.** `.gitignore` covers `*.jks`, `*.keystore` and
`keystore.properties`, but the real protection is keeping the file somewhere else entirely.

Generate it once, on a machine you control, and back it up. Losing it means no future build can
upgrade an existing installation.

```sh
keytool -genkeypair \
  -alias thotapalli-release \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -keystore ~/keys/thotapalli-release.jks \
  -dname "CN=Thotapalli Plex, O=Thotapalli"
```

`keytool` asks for the store password and the key password. Use different values, record them in
a password manager, and never type them into a file inside the checkout.

### Signing locally

The build resolves signing material in this order, taking the first that answers:

1. Environment variables. This is what CI uses, so no secret is ever written to disk.
2. The properties file named by the `thotapalli.keystoreProperties` Gradle property.
3. `keystore.properties` in the project root.

| Environment variable | Properties key | Meaning |
|---|---|---|
| `THOTAPALLI_KEYSTORE_FILE` | `storeFile` | Path to the keystore. A relative path resolves against the project root. |
| `THOTAPALLI_KEYSTORE_PASSWORD` | `storePassword` | Keystore password. |
| `THOTAPALLI_KEY_ALIAS` | `keyAlias` | Key alias, `thotapalli-release` above. |
| `THOTAPALLI_KEY_PASSWORD` | `keyPassword` | Key password. |

Prefer a file outside the checkout and point at it:

```sh
./gradlew :app:mobile:assembleRelease :app:tv:assembleRelease \
  -Pthotapalli.keystoreProperties=$HOME/keys/thotapalli.keystore.properties
```

```properties
storeFile=/home/you/keys/thotapalli-release.jks
storePassword=...
keyAlias=thotapalli-release
keyPassword=...
```

### When no keystore is configured

The release signing config is declared **only** when the keystore file actually resolves and
exists. With nothing configured there is no `release` signing config at all and
`buildTypes.release.signingConfig` is null. A clean checkout therefore configures and builds
normally, `assembleDebug` works, and pull request CI, which has no access to the secrets, is
unaffected. `assembleRelease` still runs but produces an unsigned APK, which will not install.
The release workflow checks for a signature block and fails rather than shipping one.

---

## 2. GitHub secrets and variables

Set these under **Settings → Secrets and variables → Actions**.

### Repository secrets

| Secret | Contents |
|---|---|
| `KEYSTORE_BASE64` | The keystore file, base64 encoded. |
| `KEYSTORE_PASSWORD` | The keystore password. |
| `KEY_ALIAS` | The key alias, `thotapalli-release`. |
| `KEY_PASSWORD` | The key password. |

Encode the keystore with no line wrapping, then paste the result as the secret:

```sh
base64 -w0 ~/keys/thotapalli-release.jks          # Linux
base64 -i ~/keys/thotapalli-release.jks | tr -d '\n'   # macOS, which has no -w
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\keys\thotapalli-release.jks"))
```

The workflow decodes it into the runner's temporary directory, never into the checkout, and the
runner is destroyed when the job ends.

`GITHUB_TOKEN` is provided by Actions; the workflow requests `contents: write` for it and needs
no personal access token.

### Repository variables

None are required. The keystore secrets above are the only mandatory configuration.

| Variable | Contents | Required |
|---|---|---|
| `LIBMPV_DOWNLOAD_URL` | Direct URL to a 64 bit `libmpv-2.dll`, or to a `.7z` containing one. | No |

CLAUDE.md section 4 sources libmpv from SourceForge, which offers no stable direct link and
ships a `.7z` archive. Rather than have a human mirror it, the Windows job resolves the newest
official libmpv development build automatically from the `zhongfly/mpv-winbuild` release feed
and extracts the one DLL. Nothing needs to be set for an ordinary release.

Set `LIBMPV_DOWNLOAD_URL` only to override that: to pin a specific libmpv version, or to build
somewhere without access to that feed. It accepts either a direct URL to the DLL or a URL to a
`.7z` archive that carries `libmpv-2.dll` at its root.

---

## 3. Cutting a release

1. Bump both values in `gradle.properties`. They are the single source of the version; the
   workflow refuses a tag that disagrees with `thotapalli.versionName`.

   ```properties
   thotapalli.versionCode=2
   thotapalli.versionName=0.2.0
   ```

   `versionCode` is the only value the update check compares, so it must increase on every
   release. `versionName` is a display string and is never compared.

2. Commit, then tag and push:

   ```sh
   git tag v0.2.0
   git push origin v0.2.0
   ```

3. The workflow then runs three jobs:

   | Job | Runner | Produces |
   |---|---|---|
   | `android` | `ubuntu-latest` | `thotapalli-plex-mobile-<version>.apk`, `thotapalli-plex-tv-<version>.apk`, both signed |
   | `windows` | `windows-latest` | `ThotapalliPlex-<version>.msi` |
   | `release` | `ubuntu-latest` | The GitHub Release, plus `update-manifest.json` |

   The MSI job has to run on Windows: `packageMsi` drives jpackage, and jpackage can only
   produce an MSI on Windows.

4. `workflow_dispatch` runs the two build jobs and keeps their output as workflow artefacts, but
   publishes nothing. Only a `v*` tag creates a release, so the manifest can never advertise an
   unversioned build.

### The release must not be a draft or a prerelease

The client reads `/releases/latest/download/update-manifest.json`. GitHub resolves that to the
newest release that is neither a draft nor a prerelease. Marking a release either way leaves
every installed client reading the previous manifest.

---

## 4. The update manifest

One static JSON file, published as a release asset, listing version code, version name and
artefact URL per target. CLAUDE.md section 17 point 3.

The fixed URL is:

```
https://github.com/OWNER/REPO/releases/latest/download/update-manifest.json
```

An example lives at `docs/update-manifest.example.json`. The shape:

| Field | Meaning |
|---|---|
| `schema` | Manifest schema version. `1`. A client refuses a schema it does not know. |
| `versionCode` | The release's version code, repeated per target. |
| `versionName` | Display string. Never compared. |
| `releasedAt` | ISO 8601 UTC timestamp, informational. |
| `targets.mobile` \| `targets.tv` \| `targets.desktop` | One entry per artefact. |
| `targets.*.versionCode` | Integer. **The only value the client compares.** |
| `targets.*.versionName` | Display string for the notice. |
| `targets.*.url` | Direct download URL for that artefact. |
| `targets.*.sizeBytes` | Optional. Shown beside the download action. |
| `targets.*.notesUrl` | Optional. Link to the release notes. |

The workflow generates this from the artefacts it is about to upload, measuring their sizes on
disk, so the manifest cannot describe something other than what shipped.

### How a client consumes it

`core/session/UpdateChecker` does the whole of section 17 point 4. It holds no HTTP client of
its own: `core/api` is the only module that knows about Ktor, so the fetch arrives as a
`ManifestSource`, which is a single suspending `(String) -> String`.

```kotlin
val checker = UpdateChecker(
    store = keyValueStore,
    source = ManifestSource { url -> httpClient.get(url).bodyAsText() },
    target = UpdateTarget.MOBILE,          // TELEVISION on app/tv, DESKTOP on app/desktop
    currentVersionCode = BuildConfig.VERSION_CODE,
    manifestUrl = githubLatestManifestUrl("OWNER/REPO"),
    nowMs = System::currentTimeMillis,
)

when (val result = checker.check()) {
    is UpdateCheckResult.Available -> showUpdateNotice(result.update)
    else -> Unit                            // UpToDate, Throttled and Unavailable say nothing
}
```

Rules it enforces, all covered by `UpdateCheckerTest`:

- **At most once per 24 hours.** The last check timestamp is persisted through `KeyValueStore`,
  so the throttle survives a restart. `check(force = true)` ignores it, for a manual check from
  Settings.
- **Version codes, never names.** `0.10.0` sorts below `0.9.0` as text; the integer does not lie.
- **A failure is silent.** `check` never throws. A dropped request or a malformed manifest
  returns `Unavailable` and, deliberately, does *not* start the 24 hour throttle, so the next
  launch retries instead of sitting out a day over one lost packet.
- **A newer schema is refused** rather than read optimistically, since guessing wrong would
  offer a download that does not apply.

Only `UpdateCheckResult.Available` shows anything: one non-blocking notice with a download
action, per section 17 point 4.

`UpdateChecker` is wired through `AppContainer` and driven from `AppViewModel` at launch: on
each platform the container is given its `UpdateTarget`, its `currentVersionCode` and the
manifest URL, and an `Available` result raises the non-blocking notice whose download action
opens the artefact in the browser.

---

## 5. The FFmpeg audio decoder

The Media3 FFmpeg decoder lets the phone and television apps decode audio formats their
hardware rejects — DTS, TrueHD and the like — on the device, instead of asking the server to
transcode the whole file. See CLAUDE.md sections 8 and 10.

Google publishes no binary of it to any Maven repository and ships it as source that needs an
NDK build. So `.github/workflows/ffmpeg-decoder.yml` builds it in CI and commits the resulting
AAR into `player/exo/libs/`, from where `player/exo/build.gradle.kts` picks it up. **No NDK is
needed to build the app**, and nothing is required of a release author: the AAR is already in
the repository.

- The enabled codecs are listed, one per line, in `player/exo/ffmpeg-codecs.txt`. Edit that
  file and the workflow rebuilds the AAR and commits the new one.
- The build is skipped when the codec list and the Media3 version already match the committed
  AAR, so an ordinary push does not trigger a twenty minute native build.
- The decoder is **additive**. A checkout without the AAR still builds; unsupported audio simply
  falls back to a server transcode until the AAR is present. Playback is never blocked on it.

To rebuild by hand — after changing the FFmpeg branch, say — run the **FFmpeg decoder** workflow
from the Actions tab (it has a `workflow_dispatch` trigger).

---

## 6. Known limits

1. **The MSI is not code signed.** Windows SmartScreen warns on first run. Authenticode signing
   needs a certificate from a commercial authority, which section 17 does not call for.
2. **The APKs are not distributed through Play.** Installation needs "install unknown apps"
   granted to the browser or file manager doing the install. This is expected for a client of a
   private server.
3. **`versionCode` is shared by all three targets.** `gradle.properties` holds one value, so a
   release publishes the same code everywhere. The manifest still carries it per target, which
   leaves room for a target to lag a release without a schema change.
