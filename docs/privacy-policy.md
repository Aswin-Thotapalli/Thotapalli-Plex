# Privacy Policy — Thotapalli Plex

_Last updated: 19 August 2026_

Thotapalli Plex is a client application for **your own Plex Media Server**. It lets you browse
and play the media on servers you already have access to. This policy explains, plainly and
honestly, what the app does and does not do with your data.

## What the app is

Thotapalli Plex is a media player. It has **no backend of its own** — there is no Thotapalli
Plex server, account system, or database operated by the developer. The app talks only to:

1. **plex.tv** — to sign you in to your Plex account and to discover the servers you can access.
2. **Your Plex Media Server(s)** — to list libraries, fetch metadata and artwork, stream video,
   and report playback progress.

The developer operates neither of these. Plex is a third party; your use of Plex is governed by
[Plex's own privacy policy](https://www.plex.tv/about/privacy-legal/).

## Data stored on your device

- **Plex authentication token.** After you sign in, the app stores your Plex access token so you
  stay signed in. It is stored **encrypted on your device**:
  - on Android, using **EncryptedSharedPreferences**;
  - on Windows, using the OS **Data Protection API (DPAPI)**, scoped to your user account.

  The token never leaves your device except in requests to plex.tv and to your own Plex
  server(s), which is how the app authenticates you to them.

- **A local cache and download queue.** Library listings, metadata, artwork, and any media you
  choose to download for offline playback are stored on your device to make the app work. This
  cache is safe to delete and is never sent to the developer.

## Data the app does NOT collect

- **No analytics.** The app contains no analytics or telemetry SDK. Your activity is not tracked.
- **No advertising identifiers.** The app requests no advertising ID and shows no ads.
- **No data sent to the developer.** The developer has no server and receives nothing about you —
  not your account, not your media, not your usage. There is nothing for the developer to collect.
- **No third-party data sharing.** The app shares no data with any third party. Its only network
  destinations are plex.tv (sign-in and server discovery) and the Plex server(s) you choose.

## Permissions

The app requests only the permissions it needs to function — network access to reach plex.tv and
your Plex server, and storage for the local cache and offline downloads. It does not access your
contacts, location, camera, or microphone.

## Children

The app is a client for a personal media server and is not directed at children. It collects no
personal information for the developer.

## Changes to this policy

If this policy changes, the "Last updated" date above will change with it, and the revised policy
will be published at the same URL.

## Contact

Questions about this policy can be sent to:

**aswin@cbytechains.com**
