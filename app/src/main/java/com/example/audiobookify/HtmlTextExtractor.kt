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

package com.example.audiobookify

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Performs a robust, depth-first DOM traversal of an HTML document to extract text.
 * Yields clean text blocks lazily.
 */
fun extractHtmlTextLazily(htmlContent: String): Sequence<String> = sequence {
    val document = Jsoup.parse(htmlContent)
    val body = document.body() ?: return@sequence

    val currentBlock = StringBuilder()

    suspend fun SequenceScope<String>.yieldCurrentBlock() {
        val text = currentBlock.toString().replace(Regex("\\s+"), " ").trim()
        if (text.isNotEmpty()) {
            yield(text)
        }
        currentBlock.clear()
    }

    suspend fun SequenceScope<String>.traverse(node: Node) {
        when (node) {
            is TextNode -> {
                currentBlock.append(node.text())
            }
            is Element -> {
                val isBlock = node.isBlock
                if (isBlock) yieldCurrentBlock()

                val tagName = node.tagName()
                if (tagName == "img" || tagName == "image") {
                    val alt = node.attr("alt").trim()
                    if (alt.isNotEmpty()) {
                        currentBlock.append(" [Image description: $alt] ")
                    }
                } else if (tagName == "br") {
                    currentBlock.append(" ")
                } else {
                    for (child in node.childNodes()) {
                        traverse(child)
                    }
                }

                if (isBlock) yieldCurrentBlock()
            }
        }
    }

    traverse(body)
    yieldCurrentBlock()
}
