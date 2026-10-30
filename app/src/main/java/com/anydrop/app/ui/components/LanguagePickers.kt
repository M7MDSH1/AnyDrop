package com.anydrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.engine.AudioTracks
import com.anydrop.app.engine.AudioVoice
import com.anydrop.app.engine.Captions
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked
import java.util.Locale

/**
 * The voice to save a video with when it has several: its own, or a translation (YouTube's
 * dubbing, Instagram's translated voices). Shown only when there's a choice.
 */
@Composable
fun AudioVoicePicker(
    voices: List<AudioVoice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (voices.isEmpty()) return
    val locale = currentLocale()
    val original = stringResource(R.string.audio_voice_original)
    val translated = stringResource(R.string.audio_voice_translated)
    ChoiceRow(
        title = stringResource(R.string.audio_voice_title),
        choices = voices.map { voice ->
            val role = when (voice.role) {
                AudioTracks.Role.ORIGINAL -> original
                AudioTracks.Role.DUBBED -> translated
                AudioTracks.Role.OTHER -> ""
            }
            voice.language to listOf(languageName(voice.language, locale), role).filter { it.isNotBlank() }.joinToString(" · ")
        },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier
    )
}

/** Subtitles to save with the video: none, or one of the languages it has. Shown when it has any. */
@Composable
fun SubtitlePicker(
    captions: List<Captions.Caption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (captions.isEmpty()) return
    val locale = currentLocale()
    val auto = stringResource(R.string.subtitles_auto)
    ChoiceRow(
        title = stringResource(R.string.subtitles_title),
        choices = listOf("" to stringResource(R.string.subtitles_off)) + captions.map { c ->
            val name = languageName(c.language.removeSuffix("-orig"), locale)
            c.language to if (c.auto) "$name · $auto" else name
        },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            color = White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            choices.forEach { (key, label) ->
                val isSelected = key == selected
                val shape = RoundedCornerShape(16.dp)
                Text(
                    label,
                    color = if (isSelected) Amber else White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isSelected) Amber.copy(alpha = 0.16f) else Black)
                        .border(1.dp, if (isSelected) Amber else Hairline, shape)
                        .clickable { onSelect(key) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** "es-US" → "Spanish (United States)", in the app's language. */
private fun languageName(code: String, locale: Locale): String {
    val name = Locale.forLanguageTag(code).getDisplayName(locale)
    return name.ifBlank { code }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
