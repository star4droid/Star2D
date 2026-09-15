package com.star4droid.star2d.unityui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class UnityButtonVariant {
    Normal,
    Primary,
    Success,
    Danger,
    Subtle
}

/**
 * Pure Unity-styled button with 3dp corners, 1px technical border, and pressed darkening.
 * Does NOT use any Material components or ripple effects.
 */
@Composable
fun UnityButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: UnityButtonVariant = UnityButtonVariant.Normal,
    enabled: Boolean = true,
    paddingHorizontal: Dp = 14.dp,
    paddingVertical: Dp = 7.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor = when {
        !enabled -> UnityColors.ButtonDisabled
        isPressed -> when (variant) {
            UnityButtonVariant.Primary -> UnityColors.AccentBluePressed
            UnityButtonVariant.Success -> UnityColors.AccentGreenPressed
            UnityButtonVariant.Danger -> UnityColors.AccentRedPressed
            UnityButtonVariant.Normal -> UnityColors.ButtonPressed
            UnityButtonVariant.Subtle -> UnityColors.SurfaceVariant
        }
        else -> when (variant) {
            UnityButtonVariant.Primary -> UnityColors.AccentBlue
            UnityButtonVariant.Success -> UnityColors.AccentGreen
            UnityButtonVariant.Danger -> UnityColors.AccentRed
            UnityButtonVariant.Normal -> UnityColors.ButtonNormal
            UnityButtonVariant.Subtle -> Color.Transparent
        }
    }

    val borderColor = when {
        !enabled -> UnityColors.Border
        variant == UnityButtonVariant.Primary -> UnityColors.AccentBlueHover
        variant == UnityButtonVariant.Subtle -> Color.Transparent
        else -> UnityColors.BorderLight
    }

    val textColor = when {
        !enabled -> UnityColors.ButtonDisabledText
        variant == UnityButtonVariant.Primary || variant == UnityButtonVariant.Success || variant == UnityButtonVariant.Danger -> UnityColors.TextWhite
        else -> UnityColors.TextPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            BasicText(
                text = text,
                style = UnityTypography.Button.copy(
                    color = textColor,
                    fontWeight = if (variant == UnityButtonVariant.Primary) FontWeight.Bold else FontWeight.Medium
                )
            )
        }
    }
}

/**
 * Small square icon button matching Unity UI (e.g. for close ✕, menu ⋮, add +).
 */
@Composable
fun UnityIconButton(
    iconText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    textColor: Color = UnityColors.TextSecondary,
    backgroundColor: Color = Color.Transparent,
    borderColor: Color = Color.Transparent
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentBg = if (isPressed) UnityColors.ButtonPressed else backgroundColor

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(currentBg)
            .border(if (borderColor != Color.Transparent) 1.dp else 0.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = iconText,
            style = TextStyle(
                color = if (isPressed) UnityColors.TextWhite else textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

/**
 * Pure Unity-styled Text Field with dark background, technical border, and placeholder.
 */
@Composable
fun UnityTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = UnityTypography.Body
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = if (isFocused) UnityColors.AccentBlue else UnityColors.Border

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(UnityColors.DarkBackground)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                text = placeholder,
                style = textStyle.copy(color = UnityColors.TextMuted)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = UnityColors.TextPrimary),
            cursorBrush = SolidColor(UnityColors.AccentBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Unity Draggable Window.
 * A freely draggable floating window with technical header bar, title, close button,
 * and custom styled body. Zero Material components.
 */
@Composable
fun UnityDraggableWindow(
    title: String,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialX: Float = 16f,
    initialY: Float = 24f,
    headerColor: Color = UnityColors.Header,
    width: Dp = 340.dp,
    content: @Composable BoxScope.() -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(initialX) }
    var offsetY by remember { mutableFloatStateOf(initialY) }
    var isCollapsed by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val targetWidth = if (maxWidth < width + 32.dp) (maxWidth - 24.dp).coerceAtLeast(260.dp) else width
        val targetMaxHeight = maxHeight - 48.dp

        // Keep inside screen bounds
        val clampedOffsetX = offsetX.coerceIn(4f, (screenWidthPx - 140f).coerceAtLeast(4f))
        val clampedOffsetY = offsetY.coerceIn(4f, (screenHeightPx - 100f).coerceAtLeast(4f))

        Box(
            modifier = modifier
                .offset { IntOffset(clampedOffsetX.roundToInt(), clampedOffsetY.roundToInt()) }
                .width(targetWidth)
                .heightIn(max = targetMaxHeight)
                .shadow(14.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(UnityColors.Surface)
                .border(1.dp, UnityColors.BorderLight, RoundedCornerShape(4.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Draggable Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(headerColor)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, (screenWidthPx - 120f).coerceAtLeast(0f))
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, (screenHeightPx - 80f).coerceAtLeast(0f))
                            }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small Unity drag grip indicator
                        BasicText(
                            text = "⋮⋮",
                            style = TextStyle(color = UnityColors.TextMuted, fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicText(
                            text = title,
                            style = UnityTypography.Header.copy(
                                color = UnityColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Minimize/Collapse toggle
                        UnityIconButton(
                            iconText = if (isCollapsed) "□" else "_",
                            onClick = { isCollapsed = !isCollapsed },
                            textColor = UnityColors.TextSecondary,
                            size = 24.dp
                        )
                        if (onClose != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            UnityIconButton(
                                iconText = "✕",
                                onClick = onClose,
                                textColor = UnityColors.TextSecondary,
                                size = 24.dp
                            )
                        }
                    }
                }

                // Window Body
                if (!isCollapsed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UnityColors.SurfaceVariant)
                            .padding(10.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Unity Modal Backdrop & Container.
 */
@Composable
fun UnityModal(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) {
            content()
        }
    }
}

/**
 * Small badge tag matching Unity UI.
 */
@Composable
fun UnityBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UnityColors.AccentBlue
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * Unity style Toggle Switch (Checkbox alternative, Zero Material 3).
 */
@Composable
fun UnityToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = label,
            style = UnityTypography.Body.copy(color = UnityColors.TextPrimary)
        )

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) UnityColors.AccentBlue else UnityColors.DarkBackground)
                .border(1.dp, if (checked) UnityColors.AccentBlue else UnityColors.BorderLight, RoundedCornerShape(11.dp))
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(UnityColors.TextWhite)
            )
        }
    }
}

/**
 * Unity style Dropdown Selector.
 */
@Composable
fun UnityDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentText = options.getOrNull(selectedIndex) ?: ""

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        BasicText(
            text = label,
            style = UnityTypography.BodySmall.copy(color = UnityColors.TextSecondary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(UnityColors.DarkBackground)
                .border(1.dp, if (expanded) UnityColors.AccentBlue else UnityColors.BorderLight, RoundedCornerShape(3.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = currentText,
                    style = UnityTypography.Body.copy(color = UnityColors.TextWhite)
                )
                BasicText(
                    text = if (expanded) "▲" else "▼",
                    style = TextStyle(color = UnityColors.TextSecondary, fontSize = 10.sp)
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UnityColors.Surface)
                    .border(1.dp, UnityColors.BorderLight, RoundedCornerShape(3.dp))
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) UnityColors.SurfaceVariant else Color.Transparent)
                            .clickable {
                                onSelectIndex(index)
                                expanded = false
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = option,
                            style = UnityTypography.Body.copy(
                                color = if (isSelected) UnityColors.AccentBlue else UnityColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}
