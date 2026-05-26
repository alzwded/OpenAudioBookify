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
import android.util.Log
import java.io.File
import java.io.InputStreamReader
import android.provider.OpenableColumns
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.min

private const val TAG = "OAB_BOOK_TEXT_PROVIDER"

/**
 * Data class to yield a text chunk and percentage of how much text
 * was read from a source.
 */
data class TextChunk(
    val text: String,
    val progress: Float? // 0.0f to 1.0f
)

/**
 * Interface for extracting text sequentially from a book source.
 */
interface BookTextProvider {
    /**
     * Lazily yields chunks of text from the book.
     */
    fun extractText(): Sequence<TextChunk>
}

/**
 * Factory to instantiate the correct provider based on the file extension.
 */
object BookTextProviderFactory {
    fun create(context: Context, book: Book): BookTextProvider {
        val mimeType = context.contentResolver.getType(book.uri)

        Log.i(TAG, "Creating provider for book ${book.name}, resolved mimeType $mimeType")
        
        return when (mimeType) {
            "application/epub+zip" -> EpubBookTextProvider(context, book)
            "text/html" -> HtmlBookTextProvider(context, book) // Stub
            "text/markdown", "text/x-markdown" -> MarkdownBookTextProvider(context, book)
            "text/plain" -> TxtBookTextProvider(context, book)
            else -> TxtBookTextProvider(context, book) // Fallback to plain text
        }
    }
}

/**
 * Wrap an InputStream to report byte consumption for progress reporting
 */
class CountingInputStream(
    inputStream: InputStream,
    private val totalBytes: Long
) : FilterInputStream(inputStream) {

    private var bytesRead: Long = 0

    val progress: Float
        get() = if (totalBytes > 0) min(1.0f, bytesRead.toFloat() / totalBytes) else 0.0f

    override fun read(): Int {
        val b = super.read()
        if (b != -1) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if (read != -1) bytesRead += read
        return read
    }
}

/**
 * Get the file size from a content uri
 */
fun getFileSize(context: Context, uri: android.net.Uri): Long {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex != -1 && cursor.moveToFirst()) {
            return cursor.getLong(sizeIndex)
        }
    }
    return 0L
}

/**
 * Batches sequences of text into chunks that respect the TTS engine's character limit.
 */
fun Sequence<TextChunk>.batchByLength(maxLength: Int): Sequence<TextChunk> = sequence {
    val currentBatch = StringBuilder()
    var latestProgress: Float? = null

    for (chunk in this@batchByLength) {
        val paragraph = chunk.text
        latestProgress = chunk.progress // Capture the most recent progress

        if (currentBatch.length + paragraph.length + 2 > maxLength) {
            if (currentBatch.isNotEmpty()) {
                Log.d(TAG, "Yielding a paragraph of length ${currentBatch.length}")
                yield(TextChunk(currentBatch.toString(), latestProgress))
                currentBatch.clear()
            }
        }
        if (currentBatch.isNotEmpty()) {
            currentBatch.append("\n\n")
        }
        if (paragraph.length + 2 < maxLength) {
            currentBatch.append(paragraph)
        } else {
            // If we have to hard-split a massive string, they all share the current progress
            Log.d(TAG, "Paragraph was ${paragraph.length} long, splitting in $maxLength - 2 chunks")

            // Try to split around spaces. This is because when TalkBack is running, we have a
            // harshly reduced window of 200 characters, which might actually be shorter than
            // one paragraph.
            val targetLimit = maxLength - 2
            var startIndex = 0
            
            while (startIndex < paragraph.length) {
                val remainingLength = paragraph.length - startIndex
                
                // Tail-end handling: if the rest fits, append to the current batch and finish
                if (remainingLength <= targetLimit) {
                    currentBatch.append(paragraph.substring(startIndex))
                    break
                }
            
                val desiredEndIndex = startIndex + targetLimit
                // Define our 8-character lookback boundary
                val lookbackBound = maxOf(startIndex, desiredEndIndex - 8)
                val spaceIndex = paragraph.lastIndexOf(' ', desiredEndIndex - 1)
            
                // If a space is found within the last 8 characters, split right after it
                val actualEndIndex = if (spaceIndex >= lookbackBound) {
                    spaceIndex + 1 
                } else {
                    desiredEndIndex // Fallback to hard split if no space is within budget
                }
            
                val subChunk = paragraph.substring(startIndex, actualEndIndex)
                yield(TextChunk(subChunk, latestProgress))
            
                startIndex = actualEndIndex
            }
        }
    }
    if (currentBatch.isNotEmpty()) {
        Log.d(TAG, "Yielding a paragraph of length ${currentBatch.length}")
        yield(TextChunk(currentBatch.toString(), latestProgress))
    }
}

// --- Implementations ---

/**
 * Provider for Plain Text (.txt) files.
 * Yields chunks up to a sentence boundary ([.?!][ \t\n]) or a double newline (\n\n+).
 */
class TxtBookTextProvider(
    private val context: Context, 
    private val book: Book
) : BookTextProvider {

    override fun extractText(): Sequence<TextChunk> = sequence {
        val totalBytes = getFileSize(context, book.uri)

        context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
            val countingStream = CountingInputStream(inputStream, totalBytes)

            InputStreamReader(countingStream).buffered().use { reader ->
                val buffer = java.lang.StringBuilder()
                var prevChar = -1
                var charNum = reader.read()

                while (charNum != -1) {
                    val c = charNum.toChar()
                    buffer.append(c)

                    // Check for sentence end: punctuation followed by whitespace
                    val isSentenceEnd = (prevChar.toChar() in listOf('.', '?', '!')) && 
                                        (c == ' ' || c == '\t' || c == '\n')
                    
                    // Check for paragraph end: double newline
                    val isParagraphEnd = (prevChar.toChar() == '\n' && c == '\n')

                    if (isSentenceEnd || isParagraphEnd) {
                        val chunk = buffer.toString().trim()
                        if (chunk.isNotEmpty()) {
                            yield(TextChunk(chunk, countingStream.progress))
                        }
                        buffer.clear()
                    }

                    prevChar = charNum
                    charNum = reader.read()
                }

                // Yield any remaining text
                val finalChunk = buffer.toString().trim()
                if (finalChunk.isNotEmpty()) {
                    yield(TextChunk(finalChunk, 1.0f))
                }
            }
        }
    }
}

/**
 * Provider for Markdown (.md) files.
 * Yields chunks up to a sentence boundary, paragraph end, or around headings, 
 * list items, and table cells with annotations stripped out.
 */
class MarkdownBookTextProvider(
    private val context: Context,
    private val book: Book
) : BookTextProvider {

    override fun extractText(): Sequence<TextChunk> = sequence {
        val totalBytes = getFileSize(context, book.uri)

        context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
            val countingStream = CountingInputStream(inputStream, totalBytes)

            InputStreamReader(countingStream).buffered().use { reader ->
                var inList = false
                var listItemCount = 0
                var inTable = false
                var tableRowCount = 0
                val paragraphBuffer = StringBuilder()

                suspend fun SequenceScope<TextChunk>.flushParagraph() {
                    if (paragraphBuffer.isBlank()) return
                    val text = cleanMarkdownFormatting(paragraphBuffer.toString())
                    
                    // Chunk the paragraph exactly like the TxtBookTextProvider (by sentence)
                    val chunkBuffer = java.lang.StringBuilder()
                    var prevChar = (-1).toChar()
                    
                    for (c in text) {
                        chunkBuffer.append(c)
                        val isSentenceEnd = (prevChar in listOf('.', '?', '!')) && 
                                            (c == ' ' || c == '\t' || c == '\n')
                        
                        if (isSentenceEnd) {
                            val chunk = chunkBuffer.toString().trim()
                            if (chunk.isNotEmpty()) {
                                yield(TextChunk(chunk, countingStream.progress))
                            }
                            chunkBuffer.clear()
                        }
                        prevChar = c
                    }
                    
                    val finalChunk = chunkBuffer.toString().trim()
                    if (finalChunk.isNotEmpty()) {
                        yield(TextChunk(finalChunk, 1.0f))
                    }
                    
                    paragraphBuffer.clear()
                }

                for (rawLine in reader.lineSequence()) {
                    val line = rawLine.trim()

                    // Strip blockquotes to process text cleanly
                    val cleanLine = if (line.startsWith("> ")) line.substring(2).trim() else line

                    // 1. Tables (Identified by starting and ending with the pipe char)
                    if (cleanLine.startsWith("|") && cleanLine.endsWith("|")) {
                        if (!inTable) {
                            flushParagraph()
                            inTable = true
                            tableRowCount = 1
                            yield(TextChunk(context.getString(R.string.tts_table), countingStream.progress))
                        }
                        // Skip the markdown table structure separator line (e.g. |---|---|)
                        if (cleanLine.matches(Regex("^\\|[\\s\\-:]+\\|.*"))) {
                            continue
                        }

                        val columns = cleanLine.trim('|').split("|").map { 
                            cleanMarkdownFormatting(it.trim()) 
                        }
                        // Columns are separated by two new lines to force TTS to pause
                        val rowText = context.getString(R.string.tts_table_row, tableRowCount) + "\n\n" + columns.joinToString("\n\n")
                        yield(TextChunk(rowText, countingStream.progress))
                        tableRowCount++
                        
                        inList = false
                        continue
                    } else if (inTable) {
                        inTable = false
                    }

                    // 2. Headings
                    val headingMatch = Regex("^#{1,6}\\s+(.*)").find(cleanLine)
                    if (headingMatch != null) {
                        flushParagraph()
                        inList = false
                        val headingText = cleanMarkdownFormatting(headingMatch.groupValues[1])
                        yield(TextChunk(context.getString(R.string.tts_heading, headingText), countingStream.progress))
                        continue
                    }

                    // 3. Lists
                    val listMatch = Regex("^([*\\-+]|\\d+\\.)\\s+(.*)").find(cleanLine)
                    if (listMatch != null) {
                        flushParagraph()
                        inTable = false
                        if (!inList) {
                            inList = true
                            listItemCount = 1
                            yield(TextChunk(context.getString(R.string.tts_list), countingStream.progress))
                        } else {
                            listItemCount++
                        }
                        
                        val itemText = cleanMarkdownFormatting(listMatch.groupValues[2])
                        yield(TextChunk(context.getString(R.string.tts_list_item, listItemCount, itemText), countingStream.progress))
                        continue
                    }

                    // 4. Paragraph separation (empty line)
                    if (cleanLine.isEmpty()) {
                        inList = false
                        flushParagraph()
                        continue
                    }

                    // 5. Normal text
                    inList = false
                    inTable = false
                    if (paragraphBuffer.isNotEmpty()) {
                        paragraphBuffer.append(" ")
                    }
                    paragraphBuffer.append(cleanLine)
                }

                // Flush any remaining text at the end of the file
                flushParagraph()
            }
        }
    }

    private fun cleanMarkdownFormatting(text: String): String {
        var cleanText = text
        
        // Images: ![alt](url) -> Image description: alt
        cleanText = cleanText.replace(Regex("!\\[(.*?)\\]\\((.*?)\\)")) {
            context.getString(R.string.tts_image_description, it.groupValues[1])
        }
        
        // Links: [text](url) -> text url
        cleanText = cleanText.replace(Regex("\\[(.*?)\\]\\((.*?)\\)")) {
            "${it.groupValues[1]} ${it.groupValues[2]}"
        }
        
        // Bold / Italic / Strikethrough / Inline Code
        cleanText = cleanText.replace(Regex("(\\*\\*|__)(.*?)\\1")) { it.groupValues[2] }
        cleanText = cleanText.replace(Regex("(\\*|_)(.*?)\\1")) { it.groupValues[2] }
        cleanText = cleanText.replace(Regex("(~~)(.*?)\\1")) { it.groupValues[2] }
        cleanText = cleanText.replace(Regex("(`+)(.*?)\\1")) { it.groupValues[2] }
        
        return cleanText.trim()
    }
}

/**
 * Provider for EPub (.epub) files.
 * Uses the existing EpubMetadataParser and EpubExtractor.
 * Because ZipFile requires a java.io.File, we copy the URI content to a temporary file.
 */
class EpubBookTextProvider(
    private val context: Context,
    private val book: Book
) : BookTextProvider {

    override fun extractText(): Sequence<TextChunk> = sequence {
        // Copy the URI content to a temporary File so ZipFile can process it
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.epub")
        try {
            context.contentResolver.openInputStream(book.uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Use the existing Epub parsing logic
            val metadata = EpubMetadataParser.parse(tempFile)
            
            yieldAll(extractEpubTextLazily(context, tempFile, metadata.spineList, metadata.manifestMap))
        } finally {
            if (tempFile.exists()) {
                Log.i("EPUB_PROVIDER", "Deleting temp file ${tempFile.absolutePath}")
                tempFile.delete()
            }
        }
    }
}

// --- HTML Implementation ---

class HtmlBookTextProvider(private val context: Context, private val book: Book) : BookTextProvider {
    override fun extractText(): Sequence<TextChunk> = sequence {
        context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
            val htmlContent = inputStream.bufferedReader().use { it.readText() }
            yieldAll(extractHtmlTextLazily(context, htmlContent))
        }
    }
}
