package com.redwind.hyperorig.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwind.hyperorig.R
import com.redwind.hyperorig.pods.NoiseControlMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlin.math.abs

@Composable
fun AncSwitch(ancStatus: NoiseControlMode, onAncModeChange: (NoiseControlMode) -> Unit) {
    val isDarkMode = isSystemInDarkTheme()

    val isAncMode = ancStatus == NoiseControlMode.NORMAL ||
                    ancStatus == NoiseControlMode.DEEP ||
                    ancStatus == NoiseControlMode.EXPERIMENT
    val isTransparentMode = ancStatus == NoiseControlMode.TRANSPARENT
    val isWindMode = ancStatus == NoiseControlMode.WIND_SUPPRESSION

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AncButton(
                iconRes = R.drawable.ic_transparency,
                label = stringResource(R.string.transparency_title),
                isSelected = isTransparentMode,
                isDarkMode = isDarkMode,
                onClick = {
                    if (isTransparentMode) onAncModeChange(NoiseControlMode.OFF)
                    else onAncModeChange(NoiseControlMode.TRANSPARENT)
                },
                modifier = Modifier.weight(1f)
            )
            AncButton(
                iconRes = R.drawable.ic_anc,
                label = stringResource(R.string.noise_cancellation_title),
                isSelected = isAncMode,
                isDarkMode = isDarkMode,
                onClick = {
                    if (isAncMode) onAncModeChange(NoiseControlMode.OFF)
                    else onAncModeChange(NoiseControlMode.NORMAL)
                },
                modifier = Modifier.weight(1f)
            )
            AncButton(
                iconRes = R.drawable.ic_wind,
                label = stringResource(R.string.wind_suppression_mode),
                isSelected = isWindMode,
                isDarkMode = isDarkMode,
                onClick = {
                    if (isWindMode) onAncModeChange(NoiseControlMode.OFF)
                    else onAncModeChange(NoiseControlMode.WIND_SUPPRESSION)
                },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = isAncMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SimpleAncSlider(
                    currentMode = ancStatus,
                    onModeChange = onAncModeChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp - 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.noise_cancellation_normal),
                        fontSize = 12.sp,
                        modifier = Modifier.width(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        color = if (ancStatus == NoiseControlMode.NORMAL)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = stringResource(R.string.noise_cancellation_deep),
                        fontSize = 12.sp,
                        modifier = Modifier.width(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (ancStatus == NoiseControlMode.DEEP)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = stringResource(R.string.noise_cancellation_experiment),
                        fontSize = 12.sp,
                        modifier = Modifier.width(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = if (ancStatus == NoiseControlMode.EXPERIMENT)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleAncSlider(
    currentMode: NoiseControlMode,
    onModeChange: (NoiseControlMode) -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()
    val trackColor = MiuixTheme.colorScheme.surfaceContainerHigh
    val selectedColor = MiuixTheme.colorScheme.primary
    val unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
    val sliderBackgroundColor = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF2F2F2)
    val density = LocalDensity.current
    val view = LocalView.current

    val targetIndex = when (currentMode) {
        NoiseControlMode.NORMAL -> 0
        NoiseControlMode.DEEP -> 1
        NoiseControlMode.EXPERIMENT -> 2
        else -> 0
    }

    var sliderWidth by remember { mutableIntStateOf(0) }
    var thumbPosition by remember { mutableFloatStateOf(targetIndex.toFloat()) }
    var isDragging by remember { mutableIntStateOf(-1) }
    var lastSnappedIndex by remember { mutableIntStateOf(targetIndex) }

    // 边距
    val padding = 24.dp
    val paddingPx = with(density) { padding.toPx() }

    // 可用宽度
    val availableWidth = (sliderWidth - 2 * paddingPx).coerceAtLeast(1f)

    // 三个档位的位置 (0, 1, 2)
    val positions = listOf(0f, 1f, 2f)

    // 更新 thumbPosition 当外部状态改变时
    if (isDragging < 0) {
        thumbPosition = targetIndex.toFloat()
        lastSnappedIndex = targetIndex
    }

    // 计算滑块中心 x 坐标
    val thumbCenterX = paddingPx + (thumbPosition / 2f) * availableWidth

    // 计算三个档位的 x 坐标
    val pos0 = paddingPx + (positions[0] / 2f) * availableWidth
    val pos1 = paddingPx + (positions[1] / 2f) * availableWidth
    val pos2 = paddingPx + (positions[2] / 2f) * availableWidth

    // 动画缩放值
    val scale by animateFloatAsState(
        targetValue = if (isDragging >= 0) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "thumbScale"
    )

    // 可拖动状态
    val draggableState = rememberDraggableState { delta ->
        if (isDragging >= 0 && availableWidth > 0) {
            // 将像素增量转换为位置增量
            val positionDelta = delta / availableWidth * 2f
            val newPosition = (thumbPosition + positionDelta).coerceIn(0f, 2f)
            thumbPosition = newPosition

            // 检测是否经过档位，触发触感反馈
            val currentNearestIndex = when {
                thumbPosition < 0.5f -> 0
                thumbPosition < 1.5f -> 1
                else -> 2
            }
            if (currentNearestIndex != lastSnappedIndex) {
                lastSnappedIndex = currentNearestIndex
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { sliderWidth = it.width }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    isDragging = targetIndex
                    // 触感反馈
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onDragStopped = {
                    // 吸附到最近的档位
                    val finalIndex = when {
                        thumbPosition < 0.5f -> 0
                        thumbPosition < 1.5f -> 1
                        else -> 2
                    }
                    thumbPosition = finalIndex.toFloat()
                    val newMode = when (finalIndex) {
                        0 -> NoiseControlMode.NORMAL
                        1 -> NoiseControlMode.DEEP
                        else -> NoiseControlMode.EXPERIMENT
                    }
                    if (newMode != currentMode) {
                        onModeChange(newMode)
                    }
                    // 释放时的触感反馈
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                    isDragging = -1
                }
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // 点击空白区域也能跳转
                    if (availableWidth <= 0) return@detectTapGestures
                    
                    val x = offset.x
                    val p0 = paddingPx + (0f / 2f) * availableWidth
                    val p1 = paddingPx + (1f / 2f) * availableWidth
                    val p2 = paddingPx + (2f / 2f) * availableWidth
                    
                    val dist0 = abs(x - p0)
                    val dist1 = abs(x - p1)
                    val dist2 = abs(x - p2)
                    val nearestIndex = when {
                        dist0 <= dist1 && dist0 <= dist2 -> 0
                        dist1 <= dist2 -> 1
                        else -> 2
                    }
                    thumbPosition = nearestIndex.toFloat()
                    val newMode = when (nearestIndex) {
                        0 -> NoiseControlMode.NORMAL
                        1 -> NoiseControlMode.DEEP
                        else -> NoiseControlMode.EXPERIMENT
                    }
                    if (newMode != currentMode) {
                        onModeChange(newMode)
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 滑条背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding - 17.dp)
                .height(31.5.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(sliderBackgroundColor)
        )

        // 背景三个点（可点击）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding - 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 普通降噪点
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        thumbPosition = 0f
                        if (currentMode != NoiseControlMode.NORMAL) {
                            onModeChange(NoiseControlMode.NORMAL)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFAAAAAA))
                )
            }
            // 深度降噪点
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        thumbPosition = 1f
                        if (currentMode != NoiseControlMode.DEEP) {
                            onModeChange(NoiseControlMode.DEEP)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFAAAAAA))
                )
            }
            // 试验性降噪点
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        thumbPosition = 2f
                        if (currentMode != NoiseControlMode.EXPERIMENT) {
                            onModeChange(NoiseControlMode.EXPERIMENT)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFAAAAAA))
                )
            }
        }

        // 滑块
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { thumbCenterX.toDp() } - 12.dp,
                        y = 0.dp
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(selectedColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun AncButton(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                    onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MiuixTheme.colorScheme.primary
                        isDarkMode -> Color(0xFF3C3C3C)
                        else -> Color(0xFFE8E8E8)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                colorFilter = ColorFilter.tint(
                    when {
                        isSelected -> Color.White
                        isDarkMode -> Color.LightGray
                        else -> Color(0xFF5E5E5E)
                    }
                ),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground
        )
    }
}
