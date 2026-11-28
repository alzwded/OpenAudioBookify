package alzwded.openaudiobookify

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

class MarkdownBookTextProviderTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var uri: Uri

    @Before
    fun setup() {
        context = mock(Context::class.java)
        contentResolver = mock(ContentResolver::class.java)
        uri = mock(Uri::class.java)
        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(null)
    }

    private fun extract(markdown: String): List<String> {
        `when`(contentResolver.openInputStream(uri)).thenAnswer {
            ByteArrayInputStream(markdown.toByteArray(Charsets.UTF_8))
        }
        val provider = MarkdownBookTextProvider(context, Book("test.md", uri))
        return provider.extractText().map { it.text }.toList()
    }

    @Test
    fun testHeadings() {
        val md = """
            # Heading 1
            ## Heading 2
        """.trimIndent()
        val results = extract(md)
        assertEquals(listOf("§ Heading 1", "§ Heading 2"), results)
    }

    @Test
    fun testLists() {
        val md = """
            * First item
            - Second item
            + Third item
            1. Numbered item 1
            47. Numbered item 47
        """.trimIndent()
        val results = extract(md)
        assertEquals(
            listOf(
                "* First item",
                "- Second item",
                "+ Third item",
                "1. Numbered item 1",
                "47. Numbered item 47"
            ),
            results
        )
    }

    @Test
    fun testTables() {
        val md = """
            | Header 1 | Header 2 |
            | --- | --- |
            | Cell 1 | Cell 2 |
            | Cell 3 | Cell 4 |
        """.trimIndent()
        val results = extract(md)
        assertEquals(
            listOf(
                "🔢",
                "• Header 1\n\nHeader 2",
                "• Cell 1\n\nCell 2",
                "• Cell 3\n\nCell 4"
            ),
            results
        )
    }

    @Test
    fun testImages() {
        val md = """
            Look at this: ![A dog](dog.png) and this: ![](empty.png).
        """.trimIndent()
        val results = extract(md)
        assertEquals(
            listOf("Look at this: 🖼 A dog and this: 🖼 ☐."),
            results
        )
    }
}
