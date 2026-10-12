package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.model.Span
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import java.time.LocalDateTime

/** The time now, updated each minute, and whether the phone uses a 24-hour clock. */
@Immutable
data class Clock(val now: LocalDateTime, val is24Hour: Boolean) {
    val today: java.time.LocalDate get() = now.toLocalDate()
}

val LocalClock = compositionLocalOf { Clock(LocalDateTime.now(), true) }
val LocalActions = staticCompositionLocalOf<Actions> { error("No Actions") }

/** A big screen title with an optional line under it and action icons on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** A plain top bar for pages: back, a title, and actions. */
@Composable
fun PageBar(title: String = "", onBack: () -> Unit, leading: @Composable RowScope.() -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, Modifier.semantics { contentDescription = "Back" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
        leading()
        if (title.isNotEmpty()) {
            Text(title, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

@Composable
fun SearchAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.semantics { contentDescription = "Search" }) { Icon(Icons.Rounded.Search, null) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = color)
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColorDot(color: Int, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

/** A row in Browse and Settings: an icon, a label, and a count or control on the right. */
@Composable
fun ListRow(
    label: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 12.dp)
            .height(if (detail != null) 64.dp else 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun Count(n: Int) {
    if (n > 0) Text(n.toString(), Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Inline Markdown runs as styled text. */
fun styled(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (s.strike) TextDecoration.LineThrough else null,
            ),
        ) { append(s.text) }
    }
}

/** A borderless text field with a placeholder, as in Evernote's editor. */
@Composable
fun PlainField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    description: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    transformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = singleLine || imeAction == ImeAction.Done,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.outline)
                inner()
            }
        },
    )
}
