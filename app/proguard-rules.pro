-keep class com.ytdlp.** { *; }
-keep class com.anydrop.app.engine.** { *; }

# --- youtube-dl-android / yt-dlp engine ---
# The real library packages (NOT com.ytdlp). These init Python by unzipping a
# payload with commons-compress and rely heavily on reflection, so keep them all.
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-dontwarn com.yausername.**

# commons-compress (used by youtubedl_common ZipUtils to unzip the yt-dlp payload).
# R8 was stripping internal fields these classes reflect on -> ExceptionInInitializerError.
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Some commons-compress paths reference optional compressors (xz/brotli/zstd) that
# aren't on the classpath; silence those so R8 doesn't choke.
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**

# --- App data models (kept for reflection / Compose stability) ---
-keep class com.anydrop.app.data.model.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class androidx.media3.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- libtorrent4j (torrents) ---
# The native library calls back into its SWIG-generated Java classes by name (alerts,
# directors); renaming or removing any of them crashes torrents in release builds only.
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
