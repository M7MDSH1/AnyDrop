package com.anydrop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.ui.theme.AccentSoft
import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.TextFaint
import com.anydrop.app.ui.theme.TextMuted
import com.anydrop.app.ui.theme.White

/*
 * The redesign's shared pieces. Shape rule: controls are pills, cards 20dp, option rows 16dp,
 * media 14dp, sheets 28dp. One accent: selected things get the tonal container, an accent
 * outline and accent-colored text.
 */

/** A small caption above a group of choices: "Audio language", "Quality". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextFaint, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** A choice you can flip through: language, subtitle, collection. */
@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    outlined: Boolean = false
) {
    val shape = CircleShape
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(
                when {
                    selected -> AccentSoft
                    outlined -> Color.Transparent
                    else -> MediumGray
                }
            )
            .then(
                when {
                    selected -> Modifier.border(1.5.dp, Amber, shape)
                    outlined -> Modifier.border(1.dp, Hairline, shape)
                    else -> Modifier
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) AccentText else TextMuted, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            color = if (selected) AccentText else White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Two or three exclusive options in one pill: "This video | Whole page". */
@Composable
fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    segmentHeight: Dp = 40.dp
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MediumGray)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Row(
                Modifier
                    .weight(1f)
                    .height(segmentHeight)
                    .clip(CircleShape)
                    .background(if (on) AccentSoft else Color.Transparent)
                    .then(if (on) Modifier.border(1.5.dp, Amber, CircleShape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                icons.getOrNull(i)?.let {
                    Icon(it, null, tint = if (on) AccentText else TextMuted, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                }
                Text(
                    label,
                    color = if (on) AccentText else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

enum class PillStyle { Primary, Tonal, Ghost }

/** The app's button: a pill. Primary is the one accent fill with dark text on it. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    small: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val container = when {
        !enabled -> MediumGray
        style == PillStyle.Primary -> Amber
        style == PillStyle.Tonal -> AccentSoft
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> TextFaint
        style == PillStyle.Primary -> OnAccent
        style == PillStyle.Tonal -> AccentText
        else -> White
    }
    Row(
        modifier
            .height(if (small) 36.dp else 48.dp)
            .clip(CircleShape)
            .background(container)
            .then(if (style == PillStyle.Ghost && enabled) Modifier.border(BorderStroke(1.dp, Hairline), CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(if (small) 18.dp else 22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, color = content, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The on/off switch: accent track with a dark thumb when on, a quiet track when off. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnAccent,
            checkedTrackColor = Amber,
            checkedBorderColor = Amber,
            uncheckedThumbColor = TextFaint,
            uncheckedTrackColor = Hairline,
            uncheckedBorderColor = Hairline
        )
    )
}

/** The app icon's mark on its dark square, at any size (header, empty state). */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF111419))
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
        )
    }
}
