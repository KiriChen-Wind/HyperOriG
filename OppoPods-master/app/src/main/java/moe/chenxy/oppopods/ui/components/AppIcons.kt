package moe.chenxy.oppopods.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Home: ImageVector = ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 11f)
            lineTo(12f, 4.5f)
            lineTo(20f, 11f)
            moveTo(6.5f, 10f)
            verticalLineTo(19f)
            horizontalLineTo(10f)
            verticalLineTo(14.5f)
            horizontalLineTo(14f)
            verticalLineTo(19f)
            horizontalLineTo(17.5f)
            verticalLineTo(10f)
        }
    }.build()

    val Headphones: ImageVector = ImageVector.Builder(
        name = "Headphones",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 13f)
            curveTo(4f, 7.5f, 7.8f, 4f, 12f, 4f)
            curveTo(16.2f, 4f, 20f, 7.5f, 20f, 13f)
            moveTo(7.2f, 13f)
            curveTo(5.8f, 13f, 5f, 14f, 5f, 15.4f)
            verticalLineTo(17f)
            curveTo(5f, 18.4f, 5.9f, 19.5f, 7.2f, 19.5f)
            horizontalLineTo(8.6f)
            verticalLineTo(13f)
            horizontalLineTo(7.2f)
            moveTo(16.8f, 13f)
            curveTo(18.2f, 13f, 19f, 14f, 19f, 15.4f)
            verticalLineTo(17f)
            curveTo(19f, 18.4f, 18.1f, 19.5f, 16.8f, 19.5f)
            horizontalLineTo(15.4f)
            verticalLineTo(13f)
            horizontalLineTo(16.8f)
        }
    }.build()
}
