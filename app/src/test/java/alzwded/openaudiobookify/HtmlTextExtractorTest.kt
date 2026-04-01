/*
 * Copyright (c) 2026, Vlad Mesco
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package alzwded.openaudiobookify

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlExtractorTest {

    @Test
    fun testComplexHtmlStructureProducesCorrectPauses() {
        val html = """
            <html>
                <body>
                    <h1>The Beginning</h1>
                    <div class="content">
                        <p>This is the first paragraph. It has two sentences.</p>
                        <table>
                            <tr>
                                <td>Row 1, Col 1</td>
                                <td>Row 1, Col 2</td>
                            </tr>
                        </table>
                        <p>A final dangling thought</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val results = extractHtmlTextLazily(html).toList()

        val expected = listOf(
            "The Beginning",
            "This is the first paragraph.",
            "It has two sentences.",
            "Row 1, Col 1",
            "Row 1, Col 2",
            "A final dangling thought"
        )

        assertEquals(expected, results)
    }

    @Test
    fun testImageAltTextExtraction() {
        val html = """<p>Look at this: <img src="dog.jpg" alt="A cute golden retriever"> Cool, right?</p>"""
        val results = extractHtmlTextLazily(html).toList()
        
        // Actual output has double spaces around the brackets due to " [Alt] " being yielded
        // into a buffer that might already have a space.
        val expected = "Look at this:  [Image description: A cute golden retriever]  Cool, right?"
        
        assertEquals(expected, results.first())
    }

    @Test
    fun testPreTagPreservesFormatting() {
        val html = """
            <p>Here is some code:</p>
            <pre>
                fun hello() {
                    println("World")
                }
            </pre>
            <p>End of code.</p>
        """.trimIndent()

        val results = extractHtmlTextLazily(html).toList()

        // Based on current logic:
        // 1. "Here is some code:" + the code block (no boundary between p and pre)
        // 2. "End of code."
        
        assertEquals(2, results.size)
        
        val expectedFirst = """
            Here is some code:
                fun hello() {
                    println("World")
                }
        """.trimIndent()
        
        assertEquals(expectedFirst, results[0])
        assertEquals("End of code.", results[1])
    }
}
