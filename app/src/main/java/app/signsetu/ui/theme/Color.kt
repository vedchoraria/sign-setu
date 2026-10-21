package app.signsetu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Officer side: slate text on cool paper with an indigo brand. Fixed palette (no dynamic colour) so every phone looks
// the same in training and in the field.
internal val OfficerLight = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    inversePrimary = Color(0xFFA5B4FC),
    secondary = Color(0xFF1E3A8A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFFC2410C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF7C2D12),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFDCE3EC),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
)

internal val OfficerDark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    inversePrimary = Color(0xFF4F46E5),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF172554),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = Color(0xFFFDBA74),
    onTertiary = Color(0xFF431407),
    tertiaryContainer = Color(0xFF7C2D12),
    onTertiaryContainer = Color(0xFFFFEDD5),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF334155),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF1E293B),
    surfaceContainerLowest = Color(0xFF1E293B),
    surfaceContainerLow = Color(0xFF131C2E),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF2B3A52),
    surfaceContainerHighest = Color(0xFF3B4A63),
)

// The stage: warm paper and teal, the room the subject sees. Kept apart from the officer palette on purpose.
internal val StageScheme = lightColorScheme(
    primary = Color(0xFF1C6E74),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEDEA),
    onPrimaryContainer = Color(0xFF00201F),
    inversePrimary = Color(0xFF86D4D3),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDA8),
    onSecondaryContainer = Color(0xFF2C1800),
    tertiary = Color(0xFF9C4434),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD3),
    onTertiaryContainer = Color(0xFF3E0500),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF6EF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF6EF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE3D3),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD7C9B8),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF8EFE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F0E6),
    surfaceContainer = Color(0xFFF2EADF),
    surfaceContainerHigh = Color(0xFFECE4D8),
    surfaceContainerHighest = Color(0xFFE6DDD0),
)

/** The officer header and main buttons: the indigo brand gradient, the same in both themes. */
@Immutable
data class InkColors(
    val onBand: Color, val onBandMuted: Color,
    val brush: Brush,
)

internal val Ink = InkColors(
    onBand = Color(0xFFFFFFFF),
    onBandMuted = Color(0xBFFFFFFF),
    brush = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B4FD8), Color(0xFF6366F1))),
)

/** The subject stage: a warm, calm room for the character. Stays warm at night, only dimmer. */
@Immutable
data class StageColors(
    val skyTop: Color,
    val skyBottom: Color,
    val ground: Color,
    val glow: Color,
    val bubble: Color,
    val onBubble: Color,
    val window: Color,
    val windowFrame: Color,
    val handle: Color,
    val onHandle: Color,
    /** Wordmark and icons drawn straight on the backdrop. */
    val title: Color,
    /** Behind the word the character is signing; the bubble stays light in both themes. */
    val highlight: Color = Color(0xFFFFD98A),
    val onHighlight: Color = Color.Unspecified,
    /** Neutral stage tools (draw, write, read signs), so Stop alone carries the pause colour. */
    val tool: Color = Color(0xFFFFFFFF),
    val onTool: Color = Color(0xFF2B2118),
)

internal val StageLight = StageColors(
    skyTop = Color(0xFFFFDDBE),
    skyBottom = Color(0xFFFFF3E6),
    ground = Color(0xFFF6D2AE),
    glow = Color(0xFFFFE9A8),
    bubble = Color(0xFFFFFFFF),
    onBubble = Color(0xFF2B2118),
    window = Color(0xFF2E2A33),
    windowFrame = Color(0xFFFFFFFF),
    handle = Color(0x33000000),
    onHandle = Color(0xFF5B4C3E),
    title = Color(0xFF143C44),
)

internal val StageDark = StageColors(
    skyTop = Color(0xFF4A3830),
    skyBottom = Color(0xFF2C221D),
    ground = Color(0xFF5A4336),
    glow = Color(0xFF6B5134),
    bubble = Color(0xFFFFF6EC),
    onBubble = Color(0xFF2B2118),
    window = Color(0xFF1A1719),
    windowFrame = Color(0xFF6B5A4E),
    handle = Color(0x33FFFFFF),
    onHandle = Color(0xFFE7D8C8),
    title = Color(0xFFF6E7D6),
    tool = Color(0xFFFFF6EC),
)

/** Sun mode: black, white and yellow, the combination that still reads in direct sunlight. */
internal val SunStageColors = StageColors(
    skyTop = Color(0xFF111111),
    skyBottom = Color(0xFF1A1A1A),
    ground = Color(0xFF262626),
    glow = Color(0xFF1A1A1A),
    bubble = Color(0xFF000000),
    onBubble = Color(0xFFFFE14D),
    window = Color(0xFF000000),
    windowFrame = Color(0xFFFFFFFF),
    handle = Color(0x66FFFFFF),
    onHandle = Color(0xFFFFFFFF),
    title = Color(0xFFFFE14D),
    highlight = Color(0xFFFFE14D),
    onHighlight = Color(0xFF000000),
    tool = Color(0xFF000000),
    onTool = Color(0xFFFFFFFF),
)

internal val SunAnswerColors = AnswerColors(
    yes = Color(0xFF17803A),
    onYes = Color(0xFFFFFFFF),
    yesContainer = Color(0xFF17803A),
    onYesContainer = Color(0xFFFFFFFF),
    no = Color(0xFFC62828),
    onNo = Color(0xFFFFFFFF),
    noContainer = Color(0xFFC62828),
    onNoContainer = Color(0xFFFFFFFF),
    unsure = Color(0xFFFFFFFF),
    onUnsure = Color(0xFF000000),
    pause = Color(0xFFFFE14D),
    onPause = Color(0xFF000000),
    selection = Color(0xFFFFE14D),
    onSelection = Color(0xFF000000),
)

/** Behind every symbol, in both themes: the symbols have dark outlines drawn for a light ground. */
val PictureWell = Color(0xFFFFF8EF)

/** Answer colours: also told apart by icon and position, never by colour alone. */
@Immutable
data class AnswerColors(
    val yes: Color,
    val onYes: Color,
    val yesContainer: Color,
    val onYesContainer: Color,
    val no: Color,
    val onNo: Color,
    val noContainer: Color,
    val onNoContainer: Color,
    val unsure: Color,
    val onUnsure: Color,
    val pause: Color,
    val onPause: Color,
    val selection: Color,
    val onSelection: Color,
)

internal val AnswerLight = AnswerColors(
    yes = Color(0xFF1F7F54),
    onYes = Color(0xFFFFFFFF),
    yesContainer = Color(0xFFD5F2E2),
    onYesContainer = Color(0xFF0B3D25),
    no = Color(0xFFC4513F),
    onNo = Color(0xFFFFFFFF),
    noContainer = Color(0xFFFFDDD5),
    onNoContainer = Color(0xFF4A110A),
    unsure = Color(0xFFE9E4FB),
    onUnsure = Color(0xFF2C2461),
    pause = Color(0xFFFFE7BF),
    onPause = Color(0xFF3D2700),
    selection = Color(0xFF1C6E74),
    onSelection = Color(0xFFFFFFFF),
)

internal val AnswerDark = AnswerColors(
    yes = Color(0xFF3FAF7B),
    onYes = Color(0xFF00210F),
    yesContainer = Color(0xFF0E4E31),
    onYesContainer = Color(0xFFD5F2E2),
    no = Color(0xFFE07A66),
    onNo = Color(0xFF3E0500),
    noContainer = Color(0xFF6E2418),
    onNoContainer = Color(0xFFFFDDD5),
    unsure = Color(0xFF3A3170),
    onUnsure = Color(0xFFE9E4FB),
    pause = Color(0xFF5C3F00),
    onPause = Color(0xFFFFE7BF),
    selection = Color(0xFF86D4D3),
    onSelection = Color(0xFF003736),
)
