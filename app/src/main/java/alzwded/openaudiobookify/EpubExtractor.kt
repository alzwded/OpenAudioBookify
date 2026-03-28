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

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "EPUB_EXTRACTOR"

/**
 * Extracts text from an EPub file lazily, ensuring sentences remain coherent
 * across multiple internal HTML spine files.
 */
fun extractEpubTextLazily(
    epubFile: File,
    spineList: List<String>,
    manifestMap: Map<String, String>
): Sequence<String> {
    // 1. Create a raw stream of all ballparked chunks from all spine items
    val ballparkStream = sequence {
        ZipFile(epubFile).use { zip ->
            Log.i(TAG, "Starting EPUB")
            for (id in spineList) {
                Log.i(TAG, "Next spine entry")
                val filePath = manifestMap[id] ?: continue
                val zipEntry = zip.getEntry(filePath)

                if (zipEntry != null) {
                    Log.i(TAG, "Next zip entry")
                    val htmlContent = zip.getInputStream(zipEntry).bufferedReader().use { it.readText() }
                    // Yield raw ballpark chunks to the aggregate stream
                    yieldAll(ballparkHtmlChunks(htmlContent))
                }
            }
        }
    }

    // 2. Apply the punctuation-based chunking logic to the combined stream
    return ballparkStream.chunkByPunctuation()
}
