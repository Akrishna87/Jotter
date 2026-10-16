package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.jotter.R

/*
 * The look shared by Doable, Dayline and Jotter: Plus Jakarta Sans, a softly tinted background
 * with white cards on it, round icon buttons, coloured icon tiles, tinted chips and a floating
 * pill-shaped tab bar.
 */

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Surfaces beyond Material's scheme: the page, cards, chips, hairlines and shadows. */
@Immutable
data class Kit(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val chip: Color,
    val line: Color,
    val text: Color,
    val sub: Color,
    val shadow: Color,
)

val LightKit = Kit(
    dark = false,
    bg = Color(0xFFF6F3EE),
    card = Color.White,
    chip = Color(0xFFF0EBE3),
    line = Color(0xFFEDE7DE),
    text = Color(0xFF1F1B16),
    sub = Color(0xFF625A4E),
    shadow = Color(0xFF3C280A),
)

val DarkKit = Kit(
    dark = true,
    bg = Color(0xFF11100E),
    card = Color(0xFF1D1B18),
    chip = Color(0xFF2A2621),
    line = Color(0xFF2C2925),
    text = Color(0xFFF3EEE7),
    sub = Color(0xFFB0A796),
    shadow = Color.Black,
)

val LocalKit = staticCompositionLocalOf { LightKit }

fun kitTypography(): Typography {
    val t = Typography()
    fun TextStyle.j(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) =
        copy(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)
    return t.copy(
        displayLarge = t.displayLarge.j(48, 56, FontWeight.ExtraBold, -1.0),
        displayMedium = t.displayMedium.j(40, 48, FontWeight.ExtraBold, -1.0),
        displaySmall = t.displaySmall.j(34, 42, FontWeight.ExtraBold, -0.8),
        headlineLarge = t.headlineLarge.j(34, 40, FontWeight.ExtraBold, -0.8),
        headlineMedium = t.headlineMedium.j(30, 36, FontWeight.ExtraBold, -0.6),
        headlineSmall = t.headlineSmall.j(24, 31, FontWeight.ExtraBold, -0.4),
        titleLarge = t.titleLarge.j(20, 26, FontWeight.Bold, -0.2),
        titleMedium = t.titleMedium.j(16, 22, FontWeight.Bold),
        titleSmall = t.titleSmall.j(14, 20, FontWeight.Bold),
        bodyLarge = t.bodyLarge.j(16, 23, FontWeight.Medium),
        bodyMedium = t.bodyMedium.j(14, 20, FontWeight.Medium),
        bodySmall = t.bodySmall.j(12, 17, FontWeight.Medium),
        labelLarge = t.labelLarge.j(14, 20, FontWeight.Bold),
        labelMedium = t.labelMedium.j(12, 16, FontWeight.SemiBold),
        labelSmall = t.labelSmall.j(11, 14, FontWeight.SemiBold),
    )
}

val KitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** A colour blended toward another: `soft(accent, card, 0.88)` is a light tint of the accent. */
fun blend(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

/** A soft drop shadow for cards and floating things. */
@Composable
fun Modifier.softShadow(shape: Shape, elevation: Dp = 6.dp): Modifier {
    val k = LocalKit.current
    val c = k.shadow.copy(alpha = if (k.dark) 0.5f else 0.12f)
    return this.shadow(elevation, shape, clip = false, ambientColor = c, spotColor = c)
}

/** A white (or dark) rounded card. */
@Composable
fun KitCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    color: Color = LocalKit.current.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.softShadow(shape, 3.dp).clip(shape).background(color).padding(padding), content = content)
}

private val Top = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val Bottom = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
private val Whole = RoundedCornerShape(20.dp)

/**
 * One row of a card made of list items: the first rounds the top, the last the bottom, and rows
 * after the first get a hairline above them, inset by [inset].
 */
@Composable
fun Modifier.cardSegment(index: Int, count: Int, inset: Dp = 52.dp, horizontal: Dp = 16.dp): Modifier {
    val k = LocalKit.current
    val shape = when {
        count <= 1 -> Whole
        index == 0 -> Top
        index == count - 1 -> Bottom
        else -> RectangleShape
    }
    return this
        .padding(horizontal = horizontal)
        .clip(shape)
        .background(k.card)
        .drawBehind {
            if (index > 0) drawLine(k.line, Offset(inset.toPx(), 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
}

/** Items shown as one card, each row a [cardSegment]. */
fun <T> LazyListScope.cardItems(
    list: List<T>,
    key: (T) -> Any,
    content: @Composable LazyItemScope.(item: T, segment: Modifier) -> Unit,
) {
    val n = list.size
    itemsIndexed(list, key = { _, t -> key(t) }) { i, t -> content(t, Modifier.cardSegment(i, n)) }
}

/** A round icon button that floats on the page, for headers. */
@Composable
fun CircleAction(icon: ImageVector, description: String, modifier: Modifier = Modifier, tint: Color = LocalKit.current.text, onClick: () -> Unit) {
    val k = LocalKit.current
    Box(
        modifier
            .padding(4.dp)
            .size(44.dp)
            .softShadow(CircleShape, 3.dp)
            .clip(CircleShape)
            .background(k.card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(21.dp), tint = tint) }
}

/** Tile colours for a hue: a light wash behind, and the hue (lightened in the dark) in front. */
@Composable
fun tileColors(hue: Color): Pair<Color, Color> {
    val k = LocalKit.current
    return if (k.dark) hue.copy(alpha = 0.22f) to lerp(hue, Color.White, 0.3f) else hue.copy(alpha = 0.12f) to hue
}

/** A small rounded square with an icon in a colour of its own, for rows in lists and settings. */
@Composable
fun IconTile(icon: ImageVector, hue: Color, size: Dp = 34.dp) {
    val (bg, fg) = tileColors(hue)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.53f), tint = fg)
    }
}

/** A tinted pill: labels, dates, counts. */
@Composable
fun TintChip(text: String, hue: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (bg, fg) = tileColors(hue)
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = fg)
        if (icon != null) Box(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** A grey pill for quieter facts. */
@Composable
fun QuietChip(text: String, modifier: Modifier = Modifier) {
    val k = LocalKit.current
    Text(
        text,
        modifier.clip(CircleShape).background(k.chip).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = k.sub,
        maxLines = 1,
    )
}

/** One tab in the floating bar. */
data class NavItem(val label: String, val icon: ImageVector)

/** The tab bar: a white pill floating over the page, the current tab in a tinted capsule. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val k = LocalKit.current
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).softShadow(shape, 12.dp).clip(shape).background(k.card).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val color = if (on) MaterialTheme.colorScheme.primary else k.sub
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = item.label; this.selected = on }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 52.dp, height = 30.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.icon, null, Modifier.size(21.dp), tint = color) }
                    Text(item.label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
                }
            }
        }
    }
}

/** The big add button: a rounded square in the accent, with a coloured glow. */
@Composable
fun KitFab(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(60.dp)
            .shadow(14.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
}

/** A ring that fills as [progress] goes from 0 to 1, with [content] in the middle. */
@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (progress > 0f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A row of choices in a grey capsule, the chosen one raised in white. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val k = LocalKit.current
    Row(
        modifier.height(40.dp).clip(CircleShape).background(k.chip).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val pill = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .then(if (on) Modifier.softShadow(pill, 2.dp) else Modifier)
                    .clip(pill)
                    .background(if (on) (if (k.dark) Color(0xFF34353F) else Color.White) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { contentDescription = label; this.selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) k.text else k.sub, maxLines = 1) }
        }
    }
}

/** Rows collected by [card]. */
class CardGroup {
    internal val rows = ArrayList<@Composable (Modifier) -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += { m -> Box(m) { content() } }
    }
}

/** A card of rows in a lazy list, written as `card { row { … }; row { … } }`. */
fun LazyListScope.card(build: CardGroup.() -> Unit) {
    val g = CardGroup().apply(build)
    val n = g.rows.size
    g.rows.forEachIndexed { i, r -> item { r(Modifier.cardSegment(i, n)) } }
}
