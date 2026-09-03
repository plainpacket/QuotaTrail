package app.quotatrail.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.quotatrail.presentation.theme.QuotaTrailShapes
import app.quotatrail.presentation.theme.QuotaTrailTheme

enum class InstrumentSurfaceLevel { Focal, Panel, Dock }

/** Material 3 surface hierarchy for the Field Instrument visual language. */
@Composable
fun InstrumentSurface(
    modifier: Modifier = Modifier,
    level: InstrumentSurfaceLevel,
    shape: Shape? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val resolvedShape = shape ?: when (level) {
        InstrumentSurfaceLevel.Focal -> QuotaTrailShapes.instrument
        InstrumentSurfaceLevel.Panel -> QuotaTrailShapes.lg
        InstrumentSurfaceLevel.Dock -> QuotaTrailShapes.dock
    }
    val containerColor = when (level) {
        InstrumentSurfaceLevel.Focal -> colors.surfaceContainerHigh
        InstrumentSurfaceLevel.Panel -> colors.surfaceContainerLow
        InstrumentSurfaceLevel.Dock -> QuotaTrailTheme.colors.surfaceRaised
    }
    val borderColor = when (level) {
        InstrumentSurfaceLevel.Focal -> colors.primary.copy(alpha = 0.28f)
        InstrumentSurfaceLevel.Panel -> colors.outlineVariant.copy(alpha = 0.72f)
        InstrumentSurfaceLevel.Dock -> colors.outline.copy(alpha = 0.55f)
    }
    Surface(
        modifier = modifier,
        shape = resolvedShape,
        color = containerColor,
        contentColor = colors.onSurface,
        tonalElevation = if (level == InstrumentSurfaceLevel.Dock) 6.dp else 0.dp,
        shadowElevation = if (level == InstrumentSurfaceLevel.Dock) 10.dp else 0.dp,
        border = BorderStroke(if (level == InstrumentSurfaceLevel.Focal) 2.dp else 1.dp, borderColor),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/** Warm edge-to-edge field with quiet route contours instead of translucent glass blobs. */
@Composable
fun QuotaTrailBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val routeColor = QuotaTrailTheme.colors.routeLine
    val comparisonColor = QuotaTrailTheme.colors.signalAmber
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    colors.background,
                    colors.surfaceContainerLow,
                    colors.background,
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val upperRoute = Path().apply {
                moveTo(-size.width * 0.08f, size.height * 0.15f)
                cubicTo(
                    size.width * 0.20f,
                    size.height * 0.03f,
                    size.width * 0.48f,
                    size.height * 0.30f,
                    size.width * 1.08f,
                    size.height * 0.10f,
                )
            }
            val lowerRoute = Path().apply {
                moveTo(-size.width * 0.12f, size.height * 0.80f)
                cubicTo(
                    size.width * 0.30f,
                    size.height * 0.64f,
                    size.width * 0.58f,
                    size.height * 0.98f,
                    size.width * 1.12f,
                    size.height * 0.72f,
                )
            }
            drawPath(upperRoute, routeColor.copy(alpha = 0.18f), style = Stroke(width = 1.25.dp.toPx()))
            drawPath(lowerRoute, routeColor.copy(alpha = 0.12f), style = Stroke(width = 1.dp.toPx()))
            drawCircle(
                color = colors.primary.copy(alpha = 0.10f),
                radius = 4.dp.toPx(),
                center = Offset(size.width * 0.82f, size.height * 0.14f),
            )
            drawCircle(
                color = comparisonColor.copy(alpha = 0.16f),
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.22f, size.height * 0.73f),
            )
        }
    }
}
