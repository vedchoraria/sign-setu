package app.signsetu.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.BodyLocation

/** A front-facing figure in a 3:4 box. Zones are in fractions of the box so hit-testing matches drawing at any
 * size. */
private val zones: List<Pair<BodyLocation, List<Rect>>> = listOf(
    BodyLocation.EAR to listOf(Rect(0.33f, 0.150f, 0.39f, 0.240f), Rect(0.61f, 0.150f, 0.67f, 0.240f)),
    BodyLocation.EYES to listOf(Rect(0.41f, 0.155f, 0.59f, 0.190f)),
    BodyLocation.NOSE to listOf(Rect(0.465f, 0.190f, 0.535f, 0.228f)),
    BodyLocation.MOUTH to listOf(Rect(0.44f, 0.228f, 0.56f, 0.262f)),
    BodyLocation.CHIN to listOf(Rect(0.44f, 0.262f, 0.56f, 0.300f)),
    BodyLocation.FACE to listOf(Rect(0.39f, 0.190f, 0.44f, 0.270f), Rect(0.56f, 0.190f, 0.61f, 0.270f)),
    BodyLocation.FOREHEAD to listOf(Rect(0.40f, 0.105f, 0.60f, 0.155f)),
    BodyLocation.HEAD to listOf(Rect(0.38f, 0.050f, 0.62f, 0.105f)),
    BodyLocation.CHEST to listOf(Rect(0.32f, 0.345f, 0.68f, 0.540f)),
    BodyLocation.BELLY to listOf(Rect(0.32f, 0.540f, 0.68f, 0.720f)),
    BodyLocation.ARM to listOf(Rect(0.17f, 0.360f, 0.31f, 0.760f), Rect(0.69f, 0.360f, 0.83f, 0.760f)),
)

/** The figure ends at 0.78 of the 3:4 box; the canvas is cropped to that height. */
private const val USED = 0.78f

private fun hit(x: Float, y: Float): BodyLocation? =
    zones.firstOrNull { (_, rects) -> rects.any { it.contains(Offset(x, y)) } }?.first

@Composable
fun BodyMap(selected: BodyLocation?, onSelect: (BodyLocation) -> Unit, selectedLabel: String?, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.gl_body_map)
    Canvas(
        modifier
            .widthIn(max = 260.dp)
            .aspectRatio(3f / (4f * USED))
            .semantics {
                contentDescription = description
                selectedLabel?.let { stateDescription = it }
            }
            .pointerInput(Unit) {
                detectTapGestures { p -> hit(p.x / size.width, p.y / size.height * USED)?.let(onSelect) }
            },
    ) {
        drawFigure(outline, fill)
        selected?.let { loc ->
            zones.firstOrNull { it.first == loc }?.second?.forEach { r ->
                drawRoundRect(
                    color = highlight.copy(alpha = 0.32f),
                    topLeft = Offset(r.left * size.width, r.top * size.height / USED),
                    size = Size(r.width * size.width, r.height * size.height / USED),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
    }
}

private fun DrawScope.drawFigure(line: Color, fill: Color) {
    val w = size.width
    val h = size.height / USED
    val stroke = Stroke(width = 2.dp.toPx())
    // Arms behind the torso.
    listOf(0.17f, 0.69f).forEach { x ->
        drawRoundRect(fill, Offset(x * w, 0.36f * h), Size(0.14f * w, 0.40f * h), CornerRadius(0.07f * w))
        drawRoundRect(line, Offset(x * w, 0.36f * h), Size(0.14f * w, 0.40f * h), CornerRadius(0.07f * w), style = stroke)
    }
    drawRoundRect(fill, Offset(0.30f * w, 0.33f * h), Size(0.40f * w, 0.42f * h), CornerRadius(0.08f * w))
    drawRoundRect(line, Offset(0.30f * w, 0.33f * h), Size(0.40f * w, 0.42f * h), CornerRadius(0.08f * w), style = stroke)
    drawRect(fill, Offset(0.45f * w, 0.29f * h), Size(0.10f * w, 0.05f * h))
    listOf(0.36f, 0.64f).forEach { x ->
        drawOval(fill, Offset((x - 0.03f) * w, 0.165f * h), Size(0.06f * w, 0.07f * h))
        drawOval(line, Offset((x - 0.03f) * w, 0.165f * h), Size(0.06f * w, 0.07f * h), style = stroke)
    }
    drawOval(fill, Offset(0.38f * w, 0.05f * h), Size(0.24f * w, 0.25f * h))
    drawOval(line, Offset(0.38f * w, 0.05f * h), Size(0.24f * w, 0.25f * h), style = stroke)
    // Face marks, kept faint so highlights stay readable.
    val faint = line.copy(alpha = 0.55f)
    drawCircle(faint, radius = 0.012f * w, center = Offset(0.45f * w, 0.175f * h))
    drawCircle(faint, radius = 0.012f * w, center = Offset(0.55f * w, 0.175f * h))
    drawLine(faint, Offset(0.50f * w, 0.195f * h), Offset(0.50f * w, 0.222f * h), strokeWidth = 2.dp.toPx())
    drawLine(faint, Offset(0.465f * w, 0.245f * h), Offset(0.535f * w, 0.245f * h), strokeWidth = 2.dp.toPx())
}
