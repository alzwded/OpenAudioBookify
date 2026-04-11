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

import android.content.Context
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * High-level entry point for single HTML files.
 */
fun extractHtmlTextLazily(context: Context, htmlContent: String): Sequence<String> =
    ballparkHtmlChunks(context, htmlContent).chunkByPunctuation()

/**
 * Extracts text from an HTML document lazily using a generator-consumer pattern.
 */
fun ballparkHtmlChunks(context: Context, htmlContent: String): Sequence<String> = sequence {
    val document = Jsoup.parse(htmlContent)
    val body = document.body() ?: return@sequence

    // 1. The intermediate generator: ballparks chunks directly from DOM rules
    suspend fun SequenceScope<String>.traverse(node: Node) {
        when (node) {
            // free floating text
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) yield(text)
            }
            // structure elements
            is Element -> {
                val tag = node.tagName().lowercase()
                
                when {
                    // breaks break
                    tag == "br" -> {
                        yield("\n")
                    }
                    // for pre, preserve formatting, as it might be important
                    tag == "pre" -> {
                        yield(node.wholeText() + "\n\n")
                    }
                    // extract alt or title from img
                    tag == "img" || tag == "image" -> {
                        val alt = node.attr("alt").trim()
                        val title = node.attr("title").trim()
                        if (alt.isNotEmpty()) {
                            yield(" [" + context.getString(R.string.tts_image_description, alt) + "] ")
                        } else if (title.isNotEmpty()) {
                            yield(" [" + context.getString(R.string.tts_image_title, title) + "] ")
                        } else {
                            yield(" [" + context.getString(R.string.tts_image_no_description) + "] ")
                        }
                    }
                    // just grab flattened text from headings and drop a double
                    // line feed, as they probably don't have punctuation
                    tag.matches(Regex("h[1-6]")) -> {
                        yield(context.getString(R.string.tts_heading, node.text()) + "\n\n")
                    }
                    // everything else:
                    else -> {
                        // recurse into both TextNode's and Elements, in order
                        for (child in node.childNodes()) {
                            traverse(child)
                        }

                        // when done, check what we were, and issue some relevant whitespace
                        when (tag) {
                            "table", "ol", "ul", "dl", "dd", "dt", "li", "tr", "thead", "td", "th" -> yield("\n\n")
                            "p" -> yield("\n")
                            else -> if (node.isBlock) yield(" ")
                        }
                    } // else
                } // when tag == ...
            } // is Element
        } // when(node)
    } // traverse(node)
    
    traverse(body)
} // ballparkHtmlChunks

/**
 * Generic consumer that buffers ballparked strings and yields clean chunks based on punctuation.
 */
fun Sequence<String>.chunkByPunctuation(): Sequence<String> = sequence {
    // 2. The consumer: buffers the generated chunks and yields proper sentences
    val buffer = StringBuilder()
    val boundaryRegex = Regex("([.?!][ \\t\\n]|\\n\\n)")

    for (chunk in this@chunkByPunctuation) {
        buffer.append(chunk)

        var match = boundaryRegex.find(buffer)
        while (match != null) {
            // If the boundary is a double line feed, split exactly before it.
            // If it's punctuation, include the punctuation in the yielded chunk.
            val splitIndex = if (match.value == "\n\n") {
                match.range.first
            } else {
                match.range.first + 1
            }

            val chunkToYield = buffer.substring(0, splitIndex).trim()
            if (chunkToYield.isNotEmpty()) {
                yield(chunkToYield)
            }

            // Move the buffer forward past the matched boundary 
            buffer.delete(0, match.range.last + 1)
            
            // Look for the next boundary in the remaining buffer
            match = boundaryRegex.find(buffer)
        }
    }

    // 3. Fallback: yield any remaining text in the buffer once the generator is empty
    val finalChunk = buffer.toString().trim()
    if (finalChunk.isNotEmpty()) {
        yield(finalChunk)
    }
}
