package com.minion.scaffold.feature.ocr.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.minion.scaffold.core.ocr.model.RecognizedBlock
import com.minion.scaffold.core.ocr.model.RecognizedText
import kotlin.math.min

/**
 * The captured still with its recognised blocks drawn over it, each one tappable.
 *
 * Selection at block granularity rather than a crop rectangle: text is what the user is choosing,
 * and a rectangle cannot exclude a column running alongside the paragraph they want. The boxes
 * already exist as a by-product of recognition, so this costs no extra pass.
 *
 * The boxes arrive in *source-image* pixels, so everything here is scaled by the same factor
 * `ContentScale.Fit` applies to the image — computed once and used for both drawing and hit
 * testing, so what is highlighted is exactly what a tap selects.
 */
@Composable
internal fun BlockOverlay(
    image: ImageBitmap,
    text: RecognizedText,
    selectedIds: Set<String>,
    onToggleBlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()

        // Matches ContentScale.Fit: the image is scaled uniformly to fit, then centred, so the
        // letterbox offset has to be applied to every box as well.
        val scale = min(viewWidth / image.width, viewHeight / image.height)
        val offsetX = (viewWidth - image.width * scale) / 2f
        val offsetY = (viewHeight - image.height * scale) / 2f

        val strokeWidth = with(LocalDensity.current) { STROKE.toPx() }
        val corner = with(LocalDensity.current) { CORNER.toPx() }
        val dashLength = with(LocalDensity.current) { DASH.toPx() }
        val dashed = remember(dashLength) {
            PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
        }

        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(text, scale, offsetX, offsetY) {
                    detectTapGestures { tap ->
                        // Reverse-mapped into image space rather than mapping every box forward on
                        // each tap — one transform instead of N, and it cannot drift from the
                        // drawing above because both use the same three numbers.
                        val imageX = (tap.x - offsetX) / scale
                        val imageY = (tap.y - offsetY) / scale

                        text.blocks
                            .firstOrNull { block ->
                                imageX >= block.box.left && imageX <= block.box.right &&
                                    imageY >= block.box.top && imageY <= block.box.bottom
                            }
                            ?.let { onToggleBlock(it.id) }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (block in text.blocks) {
                    val selected = block.id in selectedIds
                    val topLeft = Offset(
                        x = offsetX + block.box.left * scale,
                        y = offsetY + block.box.top * scale,
                    )
                    val size = Size(block.box.width * scale, block.box.height * scale)

                    if (selected) {
                        drawRoundRect(
                            color = SELECTED_FILL,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(corner, corner),
                        )
                    }

                    // Two independent things to say — "you kept this" and "this reading is
                    // shaky" — so they get two independent channels. Encoding both in the stroke
                    // colour made a deselected low-confidence block keep its amber outline and
                    // read as still selected.
                    drawRoundRect(
                        color = if (selected) SELECTED_STROKE else UNSELECTED_STROKE,
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = if (block.isLowConfidence) dashed else null,
                        ),
                    )
                }
            }
        }
    }
}

private val STROKE = 2.dp
private val CORNER = 4.dp

/** Dashed outline marks a shaky reading, independently of whether the block is selected. */
private val DASH = 4.dp

// Fixed colours: these sit over an arbitrary photograph, so a theme-derived palette has nothing
// predictable to contrast against.
private val SELECTED_FILL = Color(0x3348C774)
private val SELECTED_STROKE = Color(0xFF4CAF50)
private val UNSELECTED_STROKE = Color.White.copy(alpha = 0.5f)
