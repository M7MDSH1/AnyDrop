# WebP libraries for ffmpeg on 16 KB-page phones

youtubedl-android 0.18.1 ships ffmpeg's plugin libraries inside `libffmpeg.zip.so`, and five of
them (`libsharpyuv`, `libwebp`, `libwebpdecoder`, `libwebpdemux`, `libwebpmux`) are still linked
for 4 KB pages, so ffmpeg can't start on phones with 16 KB pages
([youtubedl-android#365](https://github.com/yausername/youtubedl-android/issues/365)).

The app ships them rebuilt from libwebp 1.6.0 with `-Wl,-z,max-page-size=16384` as
`app/src/main/jniLibs/<abi>/libffmpeg16k_<name>.so` (64-bit ABIs only; 32-bit phones use 4 KB
pages), and `YtDlpEngine.patchFfmpegFor16kPages()` copies them over the unpacked ones.

To rebuild (Windows, NDK 28):

```
curl -LO https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0.tar.gz
tar xzf v1.6.0.tar.gz
ndk-build.cmd NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk ^
  WEBP_SRC=<path>/libwebp-1.6.0 BUILD_DIR=<path to this folder>
```

then copy `libs/<abi>/lib<name>.so` to `app/src/main/jniLibs/<abi>/libffmpeg16k_<name>.so`.
`sources.mk` is the source list from libwebp 1.6.0's own `Android.mk`.
Drop all of this once youtubedl-android ships 16 KB-aligned WebP libraries.
