package com.redwind.hyperorig.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redwind.hyperorig.R
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun PodStatus(batteryParams: BatteryParams, modifier: Modifier = Modifier) {
    val dividerColor = if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFEEEEEE)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BatteryColumn(
            label = stringResource(R.string.batt_left_pod),
            pod = batteryParams.left,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(56.dp)
                .background(dividerColor)
        )
        BatteryColumn(
            label = stringResource(R.string.batt_right_pod),
            pod = batteryParams.right,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(56.dp)
                .background(dividerColor)
        )
        BatteryColumn(
            label = stringResource(R.string.pod_case),
            pod = batteryParams.case,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BatteryColumn(label: String, pod: PodParams?, modifier: Modifier = Modifier) {
    val isConnected = pod != null && pod.isConnected
    val level = pod?.battery ?: 0

    var lastKnownLevel by remember { mutableIntStateOf(100) }
    if (isConnected && level > 0) {
        lastKnownLevel = level
    }

    val displayLevel = if (isConnected) "$level%" else "-"
    val iconLevel = if (isConnected) level else lastKnownLevel

    // Pad short labels (左/右) to match width of longest label (耳机盒) using ideographic spaces
    val paddedLabel = if (label.length < 3) label.padEnd(3, '\u3000') else label

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = paddedLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = displayLevel,
                fontSize = 13.sp,
                color = Color.Gray
            )
            // 绘制电池图标
            val isCharging = if (isConnected) pod?.isCharging == true else false
            val isDarkMode = isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(29.dp)
                    .height(12.5.dp)
                    .drawBehind {
                        drawBattery(iconLevel, isCharging, isDarkMode)
                    }
            )
        }
    }
}

private fun DrawScope.drawBattery(level: Int, isCharging: Boolean, isDarkMode: Boolean) {
    val batteryWidth = size.width
    val batteryHeight = size.height
    val cornerRadius = 9f
    val borderWidth = 2.5f
    val capWidth = 6f
    val capHeight = 10f
    
    // 电池颜色：低于20%红色，低于30%橙色，其他根据主题选择白色或黑色
    val batteryColor = when {
        level <= 20 -> Color(0xFFFF3B30) // 红色
        level <= 30 -> Color(0xFFFF9500) // 橙色
        else -> if (isDarkMode) Color.White else Color.Black // 深色模式白色，浅色模式黑色
    }
    
    // 电池边框颜色（更亮的灰色）
    val borderColor = Color(0xFFAAAAAA)
    
    // 电池主体区域（不含正极帽）
    val bodyWidth = batteryWidth - capWidth
    
    // 绘制电池外框
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(0f, 0f),
        size = Size(bodyWidth, batteryHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth)
    )
    
    // 绘制电池正极帽
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(batteryWidth - capWidth, (batteryHeight - capHeight) / 2),
        size = Size(capWidth, capHeight),
        cornerRadius = CornerRadius(2.5f, 2.5f)
    )
    
    // 计算电量填充区域
    val padding = borderWidth + 2f
    val fillMaxWidth = bodyWidth - padding * 2
    val fillWidth = (fillMaxWidth * level / 100f).coerceAtLeast(0f)
    val fillHeight = batteryHeight - padding * 2
    
    // 绘制电量填充（带圆角，与边框圆角比例一致）
    if (fillWidth > 0f) {
        // 计算填充的圆角半径（保持与边框相同的圆角比例）
        val fillCornerRadius = cornerRadius - 2f
        
        drawRoundRect(
            color = batteryColor,
            topLeft = Offset(padding, padding),
            size = Size(fillWidth, fillHeight),
            cornerRadius = CornerRadius(fillCornerRadius.coerceAtLeast(3f), fillCornerRadius.coerceAtLeast(3f))
        )
    }
    
    // 如果是充电状态，绘制闪电符号
    if (isCharging) {
        val lightningPath = Path().apply {
            val centerX = bodyWidth / 2
            val centerY = batteryHeight / 2
            val lightningSize = 6f
            
            moveTo(centerX - lightningSize / 3, centerY - lightningSize / 2)
            lineTo(centerX + lightningSize / 6, centerY - lightningSize / 2)
            lineTo(centerX - lightningSize / 6, centerY)
            lineTo(centerX + lightningSize / 3, centerY)
            lineTo(centerX - lightningSize / 6, centerY + lightningSize / 2)
            lineTo(centerX + lightningSize / 6, centerY + lightningSize / 2)
            close()
        }
        
        drawPath(
            path = lightningPath,
            color = Color.White
        )
    }
}
