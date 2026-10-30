package com.mytvlauncher.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvlauncher.app.model.LauncherTheme

/** Translucent "glass" card used for panels and menus. */
fun Modifier.glass(theme: LauncherTheme, radius: Dp = 16.dp, alpha: Float = 0.62f): Modifier {
    val shape = RoundedCornerShape(radius)
    val edge = if (theme.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
    return this
        .clip(shape)
        .background(theme.surface.copy(alpha = alpha))
        .border(1.dp, edge, shape)
}

/** Colour of a focused element's ring: white on dark themes, accent on light ones. */
fun focusRingColor(theme: LauncherTheme): Color = if (theme.isLight) theme.accent else Color.White

fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    if (requester != null) this.focusRequester(requester) else this

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }

@Composable
fun TvButton(
    text: String,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.SECONDARY,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val base = when (style) {
        ButtonStyle.PRIMARY -> theme.accent
        ButtonStyle.DANGER -> Color(0xFFE5484D)
        ButtonStyle.SECONDARY -> theme.surface.copy(alpha = 0.75f)
    }
    val content = if (style == ButtonStyle.SECONDARY) theme.textPrimary else Color.White
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn-scale")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(base)
            .border(if (focused) 2.dp else 1.dp, if (focused) focusRingColor(theme) else Color.White.copy(alpha = 0.08f), shape)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TvChip(
    text: String,
    selected: Boolean,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            selected -> theme.accent
            focused -> theme.textPrimary.copy(alpha = 0.18f)
            else -> theme.surface.copy(alpha = 0.6f)
        },
        label = "chip-bg"
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, shape)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else theme.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Visual-only switch; the whole row it sits in is the focus target. */
@Composable
fun ToggleSwitch(checked: Boolean, theme: LauncherTheme) {
    val knobX by animateDpAsState(if (checked) 18.dp else 2.dp, label = "knob")
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) theme.accent else theme.textSecondary.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobX, y = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** A full-width focusable settings row. Put a trailing control (switch, value, chevron) in [trailing]. */
@Composable
fun SettingsRow(
    title: String,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
    },
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) theme.textPrimary.copy(alpha = 0.14f) else theme.surface.copy(alpha = 0.55f))
            .border(if (focused) 2.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, shape)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) theme.textPrimary else theme.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = theme.textSecondary, fontSize = 11.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    onToggle: (Boolean) -> Unit
) {
    SettingsRow(
        title = title,
        theme = theme,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle,
        focusRequester = focusRequester,
        onClick = { onToggle(!checked) },
        trailing = { ToggleSwitch(checked, theme) }
    )
}

/** − value + control. Left/right on the D-pad moves between the two buttons. */
@Composable
fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    theme: LauncherTheme,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(theme, radius = 12.dp, alpha = 0.55f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = theme.textSecondary, fontSize = 11.sp)
        }
        RoundIconButton(Icons.Filled.Remove, theme, enabled = value > range.first, focusRequester = focusRequester) {
            onChange(value - 1)
        }
        Text(
            "$value",
            color = theme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        RoundIconButton(Icons.Filled.Add, theme, enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (focused) theme.textPrimary.copy(alpha = 0.2f) else theme.surface.copy(alpha = 0.6f))
            .border(if (focused) 2.dp else 0.dp, if (focused) focusRingColor(theme) else Color.Transparent, CircleShape)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) theme.textPrimary else theme.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun SectionTitle(text: String, theme: LauncherTheme, modifier: Modifier = Modifier) {
    Text(
        text,
        color = theme.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, theme: LauncherTheme, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = theme.textSecondary, fontSize = 12.sp)
        }
    }
}

/** The little gold crown from the mockup's logo, drawn rather than shipped as an image. */
@Composable
fun CrownMark(modifier: Modifier = Modifier, color: Color = Color(0xFFF5B83D)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.86f)
            lineTo(w * 0.02f, h * 0.30f)
            lineTo(w * 0.30f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.70f, h * 0.55f)
            lineTo(w * 0.98f, h * 0.30f)
            lineTo(w * 0.94f, h * 0.86f)
            close()
        }
        drawPath(path, color)
        val r = w * 0.07f
        drawCircle(color, r, Offset(w * 0.02f, h * 0.26f))
        drawCircle(color, r, Offset(w * 0.50f, h * 0.10f))
        drawCircle(color, r, Offset(w * 0.98f, h * 0.26f))
    }
}
