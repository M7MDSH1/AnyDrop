package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo

/** One voice a video can be saved with: its own, or a translation (dubbing). */
data class AudioVoice(val language: String, val role: AudioTracks.Role) {

    companion object {
        /** The voices among [formats]' audio entries (empty when there's only one, nothing to choose). */
        fun of(formats: List<FormatInfo>): List<AudioVoice> {
            val voices = formats
                .filter { it.isAudioOnly && it.language.isNotBlank() }
                .groupBy { it.language }
                .map { (language, entries) ->
                    // A language counts as original if any of its entries says so.
                    val role = when {
                        entries.any { it.audioRole == AudioTracks.Role.ORIGINAL } -> AudioTracks.Role.ORIGINAL
                        entries.any { it.audioRole == AudioTracks.Role.DUBBED } -> AudioTracks.Role.DUBBED
                        else -> AudioTracks.Role.OTHER
                    }
                    AudioVoice(language, role)
                }
            if (voices.size < 2) return emptyList()
            // Next to the video's own voice, the other languages are translations of it.
            val hasOriginal = voices.any { it.role == AudioTracks.Role.ORIGINAL }
            return voices
                .map { if (hasOriginal && it.role == AudioTracks.Role.OTHER) it.copy(role = AudioTracks.Role.DUBBED) else it }
                // The video's own voice first, then the rest by language.
                .sortedWith(compareBy<AudioVoice> { it.role != AudioTracks.Role.ORIGINAL }.thenBy { it.language })
        }

        /** The voice to start with: the one picked last time if this video has it, else the original. */
        fun pick(voices: List<AudioVoice>, preferred: String): String =
            (voices.firstOrNull { it.language == preferred }
                ?: voices.firstOrNull { it.role == AudioTracks.Role.ORIGINAL }
                ?: voices.first()).language

        /**
         * The language a download asks for: only when the video has several voices and the chosen
         * one isn't the video's own (its usual audio already is), or when saving sound only.
         */
        fun toRequest(voices: List<AudioVoice>, chosen: String, format: FormatInfo): String {
            val voice = voices.firstOrNull { it.language == chosen } ?: return ""
            if (format.isImage || format.isFromPage || format.isLink || format.isTorrent) return ""
            return if (format.isAudioOnly || voice.role != AudioTracks.Role.ORIGINAL) voice.language else ""
        }
    }
}
