package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amir.filamenttracker.domain.ChipBackground
import com.amir.filamenttracker.domain.ColorSwatch
import com.amir.filamenttracker.domain.chipBackgroundFor
import com.amir.filamenttracker.domain.resolveSwatch

private fun ChipBackground.color() = parseHexOrNull(hex) ?: Color.Gray

private val RainbowColors = listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA").map { parseHexOrNull(it)!! }

private val HaloWidth = 2.5.dp

/**
 * A bold color name written in its own color with a thin halo around the letters. The halo is cream or slate,
 * never black or white, so black and white names stay readable and do not melt into the screen.
 * With a null halo the name is drawn plain.
 */
@Composable
private fun HaloText(
    name: String,
    modifier: Modifier,
    style: TextStyle,
    halo: Color?,
    color: Color = Color.Unspecified,
    brush: Brush? = null,
) {
    val fill = if (brush != null) style.copy(brush = brush) else style.copy(color = color)
    if (halo == null) {
        Text(name, modifier, style = fill, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val strokePx = with(LocalDensity.current) { HaloWidth.toPx() }
    Box(modifier) {
        Text(
            name,
            style = style.copy(
                color = halo.copy(alpha = 0.85f),
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(name, style = fill, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ColorNameChip(
    name: String,
    hex: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val swatch = resolveSwatch(name, hex)
    val textStyle = style.copy(
        fontWeight = FontWeight.Bold,
        fontSize = if (style.fontSize.isSp) (style.fontSize.value + 1f).sp else style.fontSize,
    )
    when (swatch) {
        is ColorSwatch.Hex -> HaloText(
            name, modifier, textStyle,
            halo = chipBackgroundFor(swatch.hex).color(),
            color = parseHexOrNull(swatch.hex) ?: Color.Gray,
        )
        ColorSwatch.Clear -> {
            val dash = Color(0xFF9E9E9E)
            HaloText(
                name,
                modifier.drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val y = size.height - stroke / 2
                    drawLine(
                        color = dash,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    )
                },
                textStyle,
                halo = ChipBackground.DARK.color(),
                color = Color.White,
            )
        }
        ColorSwatch.Multi -> HaloText(
            name, modifier, textStyle,
            halo = ChipBackground.LIGHT.color(),
            brush = Brush.linearGradient(RainbowColors),
        )
        ColorSwatch.None -> HaloText(
            name, modifier, textStyle,
            halo = null,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
