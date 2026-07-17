# AnyDrop — Attribution & Licensing Notice

**AnyDrop** is created and maintained by **M7MDSH**.
Copyright © 2026 M7MDSH. All rights reserved to the original AnyDrop code.

## Ownership
The original source code of AnyDrop (everything under `com.anydrop.app` that is
not a third-party dependency) is authored by M7MDSH and protected by copyright.

## Why AnyDrop is licensed under GPL-3.0
AnyDrop depends on **youtubedl-android**, which is licensed under **GPL-3.0**, and
bundles **FFmpeg**. Under the terms of the GPL, any application that links against
a GPL-3.0 library and is distributed to others must itself be released under a
GPL-compatible license and make its source available to recipients.

This means:
- You (M7MDSH) remain the copyright holder and author of the original code.
- You MAY share/distribute the app, but you must offer the source code to anyone
  you distribute the compiled app to (GPL-3.0 requirement).
- You may NOT distribute it as fully closed-source while it links yt-dlp/FFmpeg.

## Third-party components
| Component | Purpose | License |
|-----------|---------|---------|
| youtubedl-android (yt-dlp) | Media extraction/downloading | GPL-3.0 |
| AndroidX Media3 / ExoPlayer | Playback | Apache-2.0 |
| Coil | Image loading | Apache-2.0 |
| Jetpack Compose / AndroidX | UI toolkit | Apache-2.0 |
| Hilt / Dagger | Dependency injection | Apache-2.0 |
| Apache Commons Compress | Unzip yt-dlp payload | Apache-2.0 |
| FFmpeg (bundled) | Media processing | LGPL/GPL |

## Trademark / naming
"AnyDrop" and the M7MDSH attribution identify the origin of this software.
Forks and redistributions must retain this NOTICE and the LICENSE file.
