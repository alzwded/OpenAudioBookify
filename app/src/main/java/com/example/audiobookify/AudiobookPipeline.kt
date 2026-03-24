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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

// Pipeline setup:
// 1. Initialize Media3.Transformer, which has a Listener
// 2. Initialize tts, which has a Listener
// 3. Trigger first processNextChunk
//
// Pipelining:
// o-> processNextChunk
//     isCancelled -> End
//     textChunks
//         None -> End
//         Some -> synthesizeToFile -o
//             o-> onDone
//                 isCancelled -> End
//                 transformer.start -o
//                     o-> onCompleted
//                         -> isCancelled -> End
//                         -> processNextChunk -o
//
// In other words:
//    TTS synthesizes text -> WAV file ->
//    onDone() -> Transformer encodes WAV to m4a ->
//    onCompleted() -> Delete WAV -> (repeat)
//
// Chunk for TTS is determined by Sequence<String>.batchByLength(reasonableChunk)
// using BookTextExtractor as a starting point
//
// In the end, merge all m4a files into consolidated one.
// Then, perhaps populate metadata?

@UnstableApi
class AudiobookPipeline(
    private val context: Context,
    private val tts: TextToSpeech,
    private val provider: BookTextProvider,
    private val onPipelineComplete: () -> Unit
) {
    private val textChunks: Iterator<String> = provider.extractText().batchByLength(3900).iterator()
    private var chunkIndex = 0
    @Volatile private var isCancelled = false

    // Initialize the Transformer to use MediaCodec for AAC encoding
    private val transformer: Transformer = Transformer.Builder(context)
        .setAudioMimeType(MimeTypes.AUDIO_AAC) // This forces encoding to standard AAC (M4A)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (isCancelled) return

                // Clean up massive WAV file after successful encoding
                val wavFile = getWavFile(chunkIndex)
                if (wavFile.exists()) wavFile.delete()

                chunkIndex++
                processNextChunk()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                println("MediaCodec/Transformer Error on chunk $chunkIndex: ${exportException.message}")
            }
        })
        .build()

    fun start() {
        setupTtsListener()
        processNextChunk()
    }

    fun cancel() {
        isCancelled = true
        tts.stop()
        transformer.cancel() // Safely aborts the hardware encoding

        val wavFile = getWavFile(chunkIndex)
        if (wavFile.exists()) wavFile.delete()
    }

    private fun processNextChunk() {
        if (isCancelled) return

        if (!textChunks.hasNext()) {
            onPipelineComplete()
            return
        }

        val text = textChunks.next()
        val wavFile = getWavFile(chunkIndex)
        val utteranceId = "chunk_$chunkIndex"

        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        tts.synthesizeToFile(text, params, wavFile, utteranceId)
    }

    private fun setupTtsListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (isCancelled) return
                encodeWavToM4a(getWavFile(chunkIndex), getM4aFile(chunkIndex))
            }

            override fun onError(utteranceId: String?) {
                println("TTS Error on chunk $chunkIndex")
            }
        })
    }

    private fun encodeWavToM4a(wavFile: File, m4aFile: File) {
        if (isCancelled) return

        // Ensure the output file is deleted if it exists, as Transformer won't overwrite
        if (m4aFile.exists()) m4aFile.delete()

        // Feed the WAV file into the Transformer
        val mediaItem = MediaItem.fromUri(Uri.fromFile(wavFile))
        transformer.start(mediaItem, m4aFile.absolutePath)
    }

    private fun getWavFile(index: Int) = File(context.cacheDir, "temp_audiobook_chunk_$index.wav")

    private fun getM4aFile(index: Int): File {
        val outputDir = File(context.getExternalFilesDir(null), "MyAudiobook")
        if (!outputDir.exists()) outputDir.mkdirs()
        return File(outputDir, "chapter_part_$index.m4a")
    }
}
