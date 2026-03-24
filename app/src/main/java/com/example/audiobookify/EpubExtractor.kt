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

import java.io.File
import java.util.zip.ZipFile

/**
 * Batches sequences of text into chunks that respect the TTS engine's character limit.
 */
fun Sequence<String>.batchByLength(maxLength: Int): Sequence<String> = sequence {
    val currentBatch = StringBuilder()
    for (paragraph in this@batchByLength) {
        if (currentBatch.length + paragraph.length + 1 > maxLength) {
            if (currentBatch.isNotEmpty()) {
                yield(currentBatch.toString())
                currentBatch.clear()
            }
        }
        if (paragraph.length > maxLength) {
            paragraph.chunked(maxLength).forEach { yield(it) }
        } else {
            currentBatch.append(paragraph).append("\n")
        }
    }
    if (currentBatch.isNotEmpty()) yield(currentBatch.toString())
}

/**
 * Extracts text from an EPub file lazily by traversing its HTML spine elements.
 */
fun extractEpubTextLazily(
    epubFile: File,
    spineList: List<String>,
    manifestMap: Map<String, String>
): Sequence<String> = sequence {
    ZipFile(epubFile).use { zip ->
        for (id in spineList) {
            val filePath = manifestMap[id] ?: continue
            val zipEntry = zip.getEntry(filePath)

            if (zipEntry != null) {
                val htmlContent = zip.getInputStream(zipEntry).bufferedReader().use { it.readText() }
                yieldAll(extractHtmlTextLazily(htmlContent))
            }
        }
    }
}
