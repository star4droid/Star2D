package com.star4droid.star2d.unityui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Unity Dark Theme Color Palette & Typography (Zero Material 3 components).
 * Faithfully reproduces the technical, dark-slate aesthetic of Unity Hub & Unity Editor.
 */
object UnityColors {
    // Canvas & Main Backgrounds
    val Background = Color(0xFF191919)
    val DarkBackground = Color(0xFF121212)
    val SidebarBackground = Color(0xFF1E1E1E)
    val Surface = Color(0xFF222222)
    val SurfaceVariant = Color(0xFF282828)
    val Card = Color(0xFF2C2C2C)
    val CardHover = Color(0xFF333333)
    val Header = Color(0xFF303030)
    val HeaderActive = Color(0xFF383838)

    // Borders & Dividers
    val Border = Color(0xFF383838)
    val BorderLight = Color(0xFF484848)
    val BorderHighlight = Color(0xFF5A5A5A)
    val Separator = Color(0xFF2E2E2E)

    // Unity Accent Colors
    val AccentBlue = Color(0xFF2196F3)
    val AccentBlueHover = Color(0xFF42A5F5)
    val AccentBluePressed = Color(0xFF1976D2)
    val AccentGreen = Color(0xFF4CAF50)
    val AccentGreenPressed = Color(0xFF388E3C)
    val AccentRed = Color(0xFFE53935)
    val AccentRedPressed = Color(0xFFC62828)
    val AccentOrange = Color(0xFFFB8C00)
    val AccentPurple = Color(0xFF9C27B0)

    // Button Colors (Unity Matte Bevel)
    val ButtonNormal = Color(0xFF383838)
    val ButtonHover = Color(0xFF444444)
    val ButtonPressed = Color(0xFF282828)
    val ButtonDisabled = Color(0xFF242424)
    val ButtonDisabledText = Color(0xFF555555)

    // Text Hierarchy
    val TextPrimary = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFF999999)
    val TextMuted = Color(0xFF666666)
    val TextWhite = Color(0xFFFFFFFF)

    // Grid & Visual Scripting Canvas
    val CanvasGridLine = Color(0xFF242424)
    val CanvasMajorGridLine = Color(0xFF2C2C2C)
    val WireFlow = Color(0xFFE0E0E0)
    val WireTrue = Color(0xFF66BB6A)
    val WireFalse = Color(0xFFEF5350)
    val WireSelected = Color(0xFF42A5F5)

    // Node Category Header Colors
    val NodeHeaderEvent = Color(0xFF2E5A1E)
    val NodeHeaderAction = Color(0xFF1E456E)
    val NodeHeaderLogic = Color(0xFF6A2D2D)
    val NodeHeaderFlow = Color(0xFF5A3672)
    val NodeHeaderVariable = Color(0xFF7A581E)
    val NodeHeaderMath = Color(0xFF1E6E66)
}

object UnityTypography {
    val Title = TextStyle(
        color = UnityColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif
    )

    val Header = TextStyle(
        color = UnityColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.SansSerif
    )

    val Body = TextStyle(
        color = UnityColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif
    )

    val BodySmall = TextStyle(
        color = UnityColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif
    )

    val Button = TextStyle(
        color = UnityColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.SansSerif
    )

    val Code = TextStyle(
        color = UnityColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace
    )
}
