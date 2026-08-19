# Google Play Console pre-launch checklist

Practical, hard-won steps for publishing Thotapalli Plex (phone and TV) to the Play Console.
The owner uploads the AAB files by hand; nothing here is automated into Play.

These notes exist because a prior app hit every one of these walls. Do them preventively.

---

## 1. Build the bundles

Play accepts an **`.aab` (Android App Bundle), not an `.apk`.** The self-hosted sideload channel
(`.github/workflows/release.yml`) ships installable `.apk` files and cannot feed Play.

- Run the **Build Android AAB** workflow (`.github/workflows/build-android-aab.yml`): trigger it
  manually from the Actions tab (`workflow_dispatch`) or by pushing a `v*` tag.
- It produces `thotapalli-plex-mobile.aab` and `thotapalli-plex-tv.aab` as workflow artefacts.
- Download the artefacts and upload each `.aab` to its Play app / track.

## 2. versionCode: epoch timestamps, never sequential

Google **permanently burns a versionCode** the instant an `.aab` is uploaded to any track, even
if that upload is later discarded. Sequential codes therefore collide on any retry, and Play
rejects both a reused code and any code that is not strictly greater than the highest ever seen.

The AAB workflow computes `EPOCH=$(date +%s)` **once** per run and passes it as
`-Pthotapalli.versionCode=$EPOCH`. Epoch seconds:

- **never collide** — a second is only consumed once;
- **always increase** — a later build always carries a larger number;
- **avoid the shadow-prone code 1** — the value `gradle.properties` ships for the sideload
  channel, which a fresh Play app can shadow-block; epoch codes are ~1.7 billion and up.

`gradle.properties` keeps `thotapalli.versionCode=1` untouched — that stays the source of truth
for the separate self-hosted sideload channel. The `-P` flag overrides it for Play bundles only,
because `app/mobile` and `app/tv` read the code via `providers.gradleProperty(...)`.

## 3. targetSdk

`app/mobile` and `app/tv` both use **targetSdk 37** (from `gradle.properties`
`thotapalli.targetSdk`). This is current and above Play's minimum target-API requirement, so no
action is needed today. No change required.

## 4. Signing secrets and the keystore

The AAB workflow signs with the same **upload key** as the sideload channel. Four repository
secrets must be set under **Settings → Secrets and variables → Actions** (see `docs/RELEASING.md`
for how to generate and encode them):

| Secret | Contents |
|---|---|
| `KEYSTORE_BASE64` | The keystore file, base64 encoded, no line wrapping. |
| `KEYSTORE_PASSWORD` | The keystore password. |
| `KEY_ALIAS` | The key alias (`thotapalli-release`). |
| `KEY_PASSWORD` | The key password. |

**Back up the keystore off-repository.** It is the app's permanent upload identity. With Play App
Signing enabled, losing the upload key is recoverable (Google can reset it), but keep it safe
regardless — it is what CI uses to sign every bundle.

## 5. Play App Signing implications

When you create the Play app, opt into **Play App Signing** (the default). Google holds the
*app-signing key* and re-signs each delivered APK; your keystore above is the *upload key*.

Two consequences to plan for:

- **(a) A sideloaded build cannot be updated over by the Play build.** The sideload `.apk` is
  signed with the upload key; the Play-delivered APK is signed with Google's app-signing key.
  Android refuses an update across a signature change. **Testers must uninstall the sideloaded
  version first**, then install from Play. This is the single most common tester confusion.

- **(b) Any Google API keyed to a certificate fingerprint needs the Play App Signing SHA-1.**
  Google Sign-In, Play Integrity, Maps SDK and the like validate the *app-signing* certificate,
  not the upload certificate — so their console configuration must list the SHA-1 that Play shows
  under **Setup → App integrity → App signing key certificate**.

  **This app uses none of those today.** Sign-in is Plex OAuth through the system browser (the PIN
  flow in CLAUDE.md section 5), which is not keyed to a cert fingerprint. So (b) does not affect
  the app right now — but if a fingerprint-keyed Google API is ever added, register the Play App
  Signing SHA-1 or it will fail only in Play builds and work when sideloaded, which is a
  maddening thing to debug.

## 6. applicationId

The permanent `applicationId` is **`com.thotapalli.plex`** (already set in both app modules,
reverse-domain form). A Play application id is permanent and **cannot be reused across Play apps** —
once this id is claimed by a Play app it is bound to it forever. It is already correct; do not
change it.

## 7. Operational notes for testing tracks

- **Testers must be real Google accounts.** Add each tester's Google account email to the track's
  tester list (or a Google Group). A non-Google address silently never sees the app.
- **The device's Play Store must be signed into a tester account.** Installing from Play uses the
  account the Play Store app is signed into, not the account that opened the opt-in link.
- **Allow ~15 minutes of propagation.** Right after uploading or adding a tester, the store shows
  **"Item not found"** or the opt-in link 404s. This clears on its own; it is not a broken build.

## 8. Minification / deobfuscation warnings

R8 / minification is **OFF** in this project, so Play's warnings about a missing deobfuscation
file (`mapping.txt`) and missing native debug symbols are **harmless** — there is nothing to
deobfuscate. Ignore them. Only if minification is later enabled should you upload the
`mapping.txt` that the release build then produces.

## 9. Privacy policy URL

Play requires a **public URL** to a privacy policy for the store listing (and for the Data safety
form). `docs/privacy-policy.md` is the policy for this app. Host it at a public `/privacy` URL —
e.g. GitHub Pages serving this repo's `docs/` — and paste that URL into the Play Console listing.

The Data safety form itself: this app collects no analytics, no advertising id, and sends nothing
to the developer (it talks only to plex.tv and the user's own Plex server). Declare "no data
collected / no data shared" accordingly, consistent with `docs/privacy-policy.md`.
