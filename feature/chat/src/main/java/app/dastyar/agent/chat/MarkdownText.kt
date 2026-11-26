package app.dastyar.agent.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.dastyar.agent.ui.Space
import app.dastyar.agent.ui.t

@Composable
fun MarkdownMessage(text: String, color: Color, lang: String, modifier: Modifier = Modifier) {
    val blocks = parseMarkdown(text)
    if (blocks.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineText(block.text, color),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = color,
                )
                is MdBlock.Bullet -> Text(
                    inlineText("•  ${block.text}", color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
                is MdBlock.Numbered -> Text(
                    inlineText("${block.index}.  ${block.text}", color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
                is MdBlock.Paragraph -> Text(
                    inlineText(block.text, color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
                is MdBlock.CodeBlock -> CodeBlock(block, lang)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.CodeBlock, lang: String) {
    val label = block.lang.ifBlank { t(lang, "کد", "code") }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    block.code.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun inlineText(source: String, color: Color) = buildAnnotatedString {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    parseInline(source).forEach { piece ->
        when (piece) {
            is Inline.Plain -> withStyle(SpanStyle(color = color)) { append(piece.text) }
            is Inline.Bold -> withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(piece.text) }
            is Inline.Code -> withStyle(
                SpanStyle(
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            ) { append(piece.text) }
            is Inline.Link -> withLink(LinkAnnotation.Url(piece.url, linkStyle)) { append(piece.label.ifBlank { piece.url }) }
        }
    }
}
