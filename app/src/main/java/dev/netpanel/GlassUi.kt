package dev.netpanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Static soft light beneath translucent surfaces; no animated blur or WebView capture. */
@Composable
internal fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val lights = listOf(
                Triple(Offset(size.width * .08f, size.height * .12f), Color(0xFFFFD1A8), size.width * .85f),
                Triple(Offset(size.width * .92f, size.height * .08f), Color(0xFFA8C8FF), size.width * .95f),
                Triple(Offset(size.width * .75f, size.height * .95f), Color(0xFFC9B6FF), size.width * 1.05f),
                Triple(Offset(size.width * .15f, size.height * .95f), Color(0xFFA8F0E0), size.width * .85f)
            )
            lights.forEach { (center, color, radius) ->
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (dark) .15f else .55f), Color.Transparent), center, radius), radius, center)
            }
        }
        content()
    }
}

private fun Color.luminanceIsDark() = red + green + blue < 1.5f

@Composable
internal fun Modifier.glassPanel(radius: Int = 24, opacity: Float = 1f): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminanceIsDark()
    val shape = RoundedCornerShape(radius.dp)
    val top = if (dark) Color(0xFF26354B).copy(alpha = .88f) else Color.White.copy(alpha = .64f)
    val bottom = if (dark) Color(0xFF172235).copy(alpha = .78f) else Color.White.copy(alpha = .43f)
    return shadow(8.dp, shape, ambientColor = Color(0xFF263B66).copy(alpha = .09f), spotColor = Color(0xFF263B66).copy(alpha = .09f))
        .background(Brush.verticalGradient(listOf(top.copy(alpha = top.alpha * opacity), bottom.copy(alpha = bottom.alpha * opacity))), shape)
        .border(1.dp, Color.White.copy(alpha = (if (dark) .14f else .8f) * opacity), shape)
}
