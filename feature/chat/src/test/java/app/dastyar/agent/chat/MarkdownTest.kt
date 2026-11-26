package app.dastyar.agent.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun fencedBlockKeepsLanguageAndBody() {
        val blocks = parseMarkdown("Hello\n\n```kotlin\nval x = 1\n```\n")
        assertEquals(MdBlock.Paragraph("Hello"), blocks[0])
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun headingsListsAndInline() {
        val blocks = parseMarkdown("# Title\n- one\n2. two\nSee [docs](https://example.com) and `id` **now**")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertTrue(blocks[1] is MdBlock.Bullet)
        assertTrue(blocks[2] is MdBlock.Numbered)
        val inline = parseInline((blocks[3] as MdBlock.Paragraph).text)
        assertTrue(inline.any { it is Inline.Link && it.url == "https://example.com" })
        assertTrue(inline.any { it is Inline.Code && it.text == "id" })
        assertTrue(inline.any { it is Inline.Bold && it.text == "now" })
    }
}
