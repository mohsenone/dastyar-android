package app.dastyar.agent.chat

sealed interface Inline {
    data class Plain(val text: String) : Inline
    data class Bold(val text: String) : Inline
    data class Code(val text: String) : Inline
    data class Link(val label: String, val url: String) : Inline
}

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val index: String, val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
}

fun parseInline(input: String): List<Inline> {
    val out = mutableListOf<Inline>()
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            out += Inline.Plain(buf.toString())
            buf.clear()
        }
    }
    var i = 0
    while (i < input.length) {
        if (input.startsWith("**", i)) {
            val end = input.indexOf("**", i + 2)
            if (end > i) {
                flush()
                out += Inline.Bold(input.substring(i + 2, end))
                i = end + 2
                continue
            }
        }
        if (input[i] == '`') {
            val end = input.indexOf('`', i + 1)
            if (end > i) {
                flush()
                out += Inline.Code(input.substring(i + 1, end))
                i = end + 1
                continue
            }
        }
        if (input[i] == '[') {
            val close = input.indexOf(']', i + 1)
            if (close > i && close + 1 < input.length && input[close + 1] == '(') {
                val urlEnd = input.indexOf(')', close + 2)
                if (urlEnd > close) {
                    flush()
                    out += Inline.Link(input.substring(i + 1, close), input.substring(close + 2, urlEnd))
                    i = urlEnd + 1
                    continue
                }
            }
        }
        buf.append(input[i])
        i++
    }
    flush()
    return out
}

fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = line.trim()
        if (fence.startsWith("```")) {
            flushParagraph()
            val lang = fence.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            blocks += MdBlock.CodeBlock(lang.ifBlank { "" }, code.toString())
            i++
            continue
        }
        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }
        val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line.trim())
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            i++
            continue
        }
        val bullet = Regex("^[-*]\\s+(.+)$").matchEntire(line.trim())
        if (bullet != null) {
            flushParagraph()
            blocks += MdBlock.Bullet(bullet.groupValues[1])
            i++
            continue
        }
        val numbered = Regex("^(\\d+)[.)]\\s+(.+)$").matchEntire(line.trim())
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2])
            i++
            continue
        }
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line.trim())
        i++
    }
    flushParagraph()
    return blocks
}
