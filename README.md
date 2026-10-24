# AnyDrop

**AnyDrop** is an Android media downloader for videos, audio, and images from
platforms like YouTube, TikTok, Instagram, Facebook, X, Reddit, and more. It's
built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) with an analog-tape-inspired UI.

> Created and maintained by **M7MDSH**. © 2026. Licensed under **GPL-3.0**. **v1.5.2**

---

## Features

- **Download videos** in any available quality (picks video + audio and merges them).
- **No login needed** for public posts — Instagram reels, posts and carousels (and any
  site yt-dlp can't handle) are read straight from the web page, like a browser would.
- **Audio-only** extraction (.m4a) at selectable bitrates.
- **Images** — single photos and photo posts.
- **Whole posts** — share a carousel that mixes photos and videos and AnyDrop fetches
  every item, shows them in a grid (videos marked ▶) and downloads the ones you pick,
  or all of them in one tap.
- **This video or the whole page** — a link can fetch just its video, or everything on
  its page: the page's own video in every quality, the other clips it plays and the
  videos it links to (like the suggestions under a player), to pick from.
- **Playlists** — a YouTube playlist or mix lists all its videos (up to 200) with
  covers and lengths; pick some or download them all, as **video or audio only**
  (.m4a). Opening one video of a playlist offers the whole list in one tap.
- **Torrents** — open a magnet link or a .torrent file, pick the files you want and
  download them; AnyDrop shares pieces only while downloading and stops when it's done.
  Download only what you have the right to download.
- **Cover, length and quality for every video** — videos read off a page are checked
  for their real length and resolution and get a cover frame, so the real video stands
  out from ads and previews.
- **Download tracker** — run several downloads at once and follow each one's real
  progress, speed and time left, with STOP / RETRY / OPEN.
- **Share to download** — share a link from any app into AnyDrop; a compact bottom
  sheet fetches the formats, you pick quality/images and hit **RECORD**, and it
  downloads in the background without leaving the app you were in.
- **Built-in browser** — browse any site and download the videos it plays. The start
  page is yours: add, edit, reorder or remove sites. Desktop mode is remembered per
  site and fits desktop pages to the screen; TikTok opens as its desktop site so its
  feed scrolls without the app. Tap the address bar to open a link you just copied or
  a page from your **history** (by day, removable, kept on the device only).
- **Library** — browse all device videos & images, **search** (title, creator, site,
  file name — Arabic spelling variants included), filter (All / Videos / Images), sort
  by date or length, multi-select delete, and a per-item menu (Share / Add to collection /
  **Rename** / Delete / Info). Files other apps made ask for Android's permission first.
- **Favorites & collections** — tap ♡ on any photo or video (in the library or the
  player) to keep it in **Favorites**, and make your own collections (e.g. "Family"):
  select items, tap the folder button, and the collection shows only what you added.
  Rename or delete a collection with a long-press — the files themselves stay put.
- **Audio tab** — plays device audio with background playback + media notification;
  rename or delete songs from their menu.
- **Built-in player** — tap the video to show or hide the controls; a smooth seek bar
  with live frame preview while you drag, stacking double-tap seek, gestures (volume,
  brightness), playback speed, PiP shaped like the video, aspect-ratio toggle, resume
  where you left off, **share the video or the link it came from**, a ♡ favorite button,
  swipe left/right between items, and a zoomable image viewer.
- **Movie mode** — one tap on the 🎬 icon in the player: swiping left/right moves through
  the video instead of switching videos, previous/next disappear, and Back leaves the
  player. It stays on until you turn it off.
- **Default video player** — AnyDrop appears in "Open with" and can be set as the
  phone's video player for files from any app.
- **Update notifications** — AnyDrop checks this repo's releases (no account, no paid
  service) and tells you when a new version is out, then downloads and installs it in one tap.
  Said "later" or "skip"? **Settings → Updates** keeps an **Update** button until you
  install it, and a **Check for updates** button once you're on the latest version.
- **English & Arabic**, with a **dark ↔ light** theme, both switchable from Settings.
- **Optional login (cookies)** — only for private content: sign in inside the app, and
  disconnect or remove a site's session any time.
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
