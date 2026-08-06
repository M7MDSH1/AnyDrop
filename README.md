# AnyDrop

**AnyDrop** is an Android media downloader for videos, audio, and images from
platforms like YouTube, TikTok, Instagram, Facebook, X, Reddit, and more. It's
built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) with an analog-tape-inspired UI.

> Created and maintained by **M7MDSH**. © 2026. Licensed under **GPL-3.0**. **v1.1.0**

---

## Features

- **Download videos** in any available quality (picks video + audio and merges via ffmpeg).
- **Audio-only** extraction (.m4a) at selectable bitrates.
- **Images** — including multi-image / carousel posts with a select-what-you-want grid.
- **Share to download** — share a link from any app into AnyDrop; a compact bottom
  sheet fetches the formats, you pick quality/images and hit **RECORD**, and it
  downloads in the background without leaving the app you were in.
- **Library** — browse all device videos & images, filter (All / Videos / Images),
  sort by date or length, multi-select delete, and a per-item menu (Share / Delete / Info).
- **Audio tab** — plays device audio with background playback + media notification.
- **Built-in player** — gestures (seek, volume, brightness), playback speed, PiP with
  controls, aspect-ratio toggle, **swipe left/right to jump between images & videos**,
  and a zoomable image viewer.
- **Appearance** — instant dark ↔ light "tape" theme toggle from Settings.
- **In-app login (cookies)** — sign in to sites (e.g. Instagram) inside the app to
  download content that requires authentication. Add custom platforms too.
- Downloads are saved to your device and exported to the public gallery (Movies /
  Pictures / Music → `AnyDrop/`) so other apps (WhatsApp, Google Photos) can see them.

## Requirements

- Android 8.0 (API 26) or newer.
- **arm64-v8a** device (virtually all modern phones). An `armeabi-v7a` build is also produced for older devices.

## Install

Grab the latest APK from the [Releases](../../releases) page and install the
**`app-arm64-v8a-release.apk`** on your phone (you may need to allow "install from
unknown sources").

> Not on the Play Store — general-purpose downloaders violate Play policy, so
> AnyDrop is distributed as a direct APK.

## Build from source

1. Install **Android Studio** (bundled JDK 17/21).
2. Clone this repo and open it in Android Studio.
3. Let Gradle sync, then **Build → Generate Signed Bundle / APK** (or run
   `./gradlew assembleRelease`).

> If building from the command line, ensure `JAVA_HOME` points at a 64-bit JDK 17+,
> e.g. Android Studio's bundled JBR:
> `C:\Program Files\Android\Android Studio\jbr`

## Notes & disclaimer

- **For personal use.** Respect the terms of service of the platforms you download
  from and the copyright of content creators. Don't redistribute copyrighted media.
- AnyDrop stores any login cookies **locally on your device only** — nothing is uploaded.
- Some sites that use TLS-fingerprint blocking (browser impersonation) are not
  supported, due to a limitation of the underlying Android yt-dlp library.

## License

GPL-3.0 — see [LICENSE](LICENSE). AnyDrop links yt-dlp (GPL-3.0) and bundles FFmpeg,
so the combined work is distributed under GPL-3.0. See [NOTICE.md](NOTICE.md) for
third-party attributions.
