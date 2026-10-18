package com.amir.filamenttracker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.filamenttracker.domain.ChipBackground
import com.amir.filamenttracker.domain.ColorSwatch
import com.amir.filamenttracker.domain.chipBackgroundFor
import com.amir.filamenttracker.domain.resolveSwatch

private fun ChipBackground.color() = parseHexOrNull(hex) ?: Color.Gray

private val RainbowColors = listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA").map { parseHexOrNull(it)!! }

/**
 * A color name written in its own color on a pill. The pill is cream or slate, never black or white,
 * so black and white names stay readable and do not melt into the screen.
 */
@Composable
fun ColorNameChip(
    name: String,
    hex: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val swatch = resolveSwatch(name, hex)
    val shape = CircleShape
    val padding = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
    val textStyle = style.copy(fontWeight = FontWeight.SemiBold)
    when (swatch) {
        is ColorSwatch.Hex -> {
            val color = parseHexOrNull(swatch.hex) ?: Color.Gray
            Text(
                name,
                modifier.clip(shape).background(chipBackgroundFor(swatch.hex).color()).then(padding),
                style = textStyle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ColorSwatch.Clear -> {
            val dash = Color(0xFF9E9E9E)
            Text(
                name,
                modifier
                    .clip(shape)
                    .background(ChipBackground.DARK.color())
                    .drawBehind {
                        val stroke = 1.5.dp.toPx()
                        val inset = stroke / 2
                        drawRoundRect(
                            color = dash,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(size.height / 2),
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                        )
                    }
                    .then(padding),
                style = textStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ColorSwatch.Multi -> Text(
            name,
            modifier.clip(shape).background(ChipBackground.LIGHT.color()).then(padding),
            style = textStyle.copy(brush = Brush.linearGradient(RainbowColors)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ColorSwatch.None -> Text(
            name,
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).then(padding),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
