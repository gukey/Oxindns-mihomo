package dev.netpanel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Small original vector set; avoids bundling the entire extended icon catalog. */
object PanelIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw) }.build()
    private fun PathBuilder.circle(x: Float, y: Float, r: Float) {
        moveTo(x + r, y); arcToRelative(r, r, 0f, false, true, -2 * r, 0f)
        arcToRelative(r, r, 0f, false, true, 2 * r, 0f); close()
    }
    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); horizontalLineToRelative(w); verticalLineToRelative(h); horizontalLineToRelative(-w); close()
    }
    val Dashboard = icon("Dashboard") { rect(3f, 3f, 7f, 7f); rect(14f, 3f, 7f, 7f); rect(3f, 14f, 7f, 7f); rect(14f, 14f, 7f, 7f) }
    val Language = icon("Globe") {
        circle(12f, 12f, 9f); moveTo(3f, 12f); horizontalLineTo(21f)
        moveTo(12f, 3f); curveTo(6f, 8f, 6f, 16f, 12f, 21f)
        moveTo(12f, 3f); curveTo(18f, 8f, 18f, 16f, 12f, 21f)
    }
    val Hub = icon("Network") {
        circle(12f, 5f, 2.5f); circle(5f, 18f, 2.5f); circle(19f, 18f, 2.5f)
        moveTo(11f, 7.5f); lineTo(6f, 15.5f); moveTo(13f, 7.5f); lineTo(18f, 15.5f)
        moveTo(7.5f, 18f); horizontalLineTo(16.5f)
    }
    val Settings = icon("Settings") {
        circle(12f, 12f, 7f); circle(12f, 12f, 2.5f)
        for (i in 0..7) { val angle = i * Math.PI / 4; moveTo(12 + cos(angle).toFloat() * 8, 12 + sin(angle).toFloat() * 8); lineTo(12 + cos(angle).toFloat() * 10, 12 + sin(angle).toFloat() * 10) }
    }
    val Refresh = icon("Refresh") {
        moveTo(19f, 8f); curveTo(16f, 1f, 5f, 3f, 4f, 10f); curveTo(2f, 18f, 13f, 23f, 19f, 16f)
        moveTo(19f, 3f); verticalLineTo(8f); horizontalLineTo(14f)
    }
    val ArrowBack = icon("Back") { moveTo(11f, 4f); lineTo(3f, 12f); lineTo(11f, 20f); moveTo(3f, 12f); horizontalLineTo(21f) }
    val Shield = icon("Shield") { moveTo(12f, 3f); lineTo(20f, 6f); verticalLineTo(12f); curveTo(20f, 17f, 15f, 20f, 12f, 22f); curveTo(9f, 20f, 4f, 17f, 4f, 12f); verticalLineTo(6f); close(); moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f) }
    val Tune = icon("Tune") {
        for (y in listOf(5f, 12f, 19f)) { moveTo(3f, y); horizontalLineTo(21f) }
        circle(8f, 5f, 2f); circle(16f, 12f, 2f); circle(10f, 19f, 2f)
    }
    val CloudOff = icon("CloudOff") { moveTo(6f, 18f); curveTo(0f, 17f, 2f, 9f, 7f, 10f); curveTo(6f, 2f, 18f, 2f, 18f, 10f); curveTo(24f, 10f, 23f, 18f, 19f, 18f); moveTo(3f, 3f); lineTo(21f, 21f) }
    private fun PathBuilder.eye() { moveTo(2f, 12f); curveTo(7f, 3f, 17f, 3f, 22f, 12f); curveTo(17f, 21f, 7f, 21f, 2f, 12f); close(); circle(12f, 12f, 3f) }
    val Visibility = icon("Show") { eye() }
    val VisibilityOff = icon("Hide") { eye(); moveTo(3f, 3f); lineTo(21f, 21f) }
}
