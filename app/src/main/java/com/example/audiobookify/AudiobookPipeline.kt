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


package com.example.audiobookify

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Codec
import java.io.File

@UnstableApi
class AudiobookPipeline(
    private val context: Context,
    private val tts: TextToSpeech,
    private val provider: BookTextProvider,
    private val bookName: String,
    private val outputDirUri: Uri?,
    private val onPipelineComplete: () -> Unit
) {
    private val textChunks: Iterator<String> = provider.extractText().batchByLength(3900).iterator()
    private var chunkIndex = 0
    @Volatile private var isCancelled = false

    // Keep track of our encoded intermediate m4a chunks
    private val encodedChunkFiles = mutableListOf<File>()

    /**
     * Creates a Transformer configured for Mono, 48kbps AAC.
     */
    private fun createAudioTransformer(listener: Transformer.Listener): Transformer {
        val defaultEncoderFactory = DefaultEncoderFactory.Builder(context).build()
        
        val customEncoderFactory = object : Codec.EncoderFactory {
            override fun createForAudioEncoding(
                format: androidx.media3.common.Format,
                logSessionId: android.media.metrics.LogSessionId?
            ): Codec {
                val customFormat = format.buildUpon()
                    .setChannelCount(1)
                    .setAverageBitrate(48000)
                    .build()
                return defaultEncoderFactory.createForAudioEncoding(customFormat, logSessionId)
            }

            override fun createForVideoEncoding(
                format: androidx.media3.common.Format,
                logSessionId: android.media.metrics.LogSessionId?
            ): Codec {
                return defaultEncoderFactory.createForVideoEncoding(format, logSessionId)
            }
        }

        return Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(customEncoderFactory)
            .addListener(listener)
            .build()
    }

    // Transformer for encoding WAV -> M4A
    private val chunkTransformer: Transformer by lazy {
        createAudioTransformer(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (isCancelled) return

                val wavFile = getWavFile(chunkIndex)
                if (wavFile.exists()) wavFile.delete()

                val tempM4a = getTempM4aFile(chunkIndex)
                if (tempM4a.exists()) {
                    encodedChunkFiles.add(tempM4a)
                }

                chunkIndex++
                processNextChunk()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                println("Chunk MediaCodec Error on chunk $chunkIndex: ${exportException.message}")
                cleanup()
                onPipelineComplete()
            }
        })
    }

    fun start() {
        setupTtsListener()
        processNextChunk()
    }

    fun cancel() {
        isCancelled = true
        tts.stop()
        chunkTransformer.cancel()
        cleanup()
    }

    private fun processNextChunk() {
        if (isCancelled) return

        if (!textChunks.hasNext()) {
            mergeChunksAndExport()
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
                Handler(context.mainLooper).post {
                    encodeWavToM4a(getWavFile(chunkIndex), getTempM4aFile(chunkIndex))
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                println("TTS Error on chunk $chunkIndex")
            }
        })
    }

    private fun encodeWavToM4a(wavFile: File, m4aFile: File) {
        if (isCancelled) return
        if (m4aFile.exists()) m4aFile.delete()

        val mediaItem = MediaItem.fromUri(Uri.fromFile(wavFile))
        chunkTransformer.start(mediaItem, m4aFile.absolutePath)
    }

    private fun mergeChunksAndExport() {
        if (encodedChunkFiles.isEmpty() || outputDirUri == null) {
            cleanup()
            onPipelineComplete()
            return
        }

        val editedMediaItems = encodedChunkFiles.map { file ->
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            EditedMediaItem.Builder(mediaItem).build()
        }

        val sequence = EditedMediaItemSequence.withAudioFrom(editedMediaItems)
        val composition = Composition.Builder(sequence).build()

        val finalTempFile = File(context.cacheDir, "final_merged_audiobook.m4a")
        if (finalTempFile.exists()) finalTempFile.delete()

        val mergeTransformer = createAudioTransformer(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!isCancelled) writeToSaf(finalTempFile)
                cleanup(finalTempFile)
                onPipelineComplete()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                println("Error merging final audiobook: ${exportException.message}")
                cleanup(finalTempFile)
                onPipelineComplete()
            }
        })

        Handler(context.mainLooper).post {
            if (!isCancelled) {
                mergeTransformer.start(composition, finalTempFile.absolutePath)
            }
        }
    }

    private fun writeToSaf(finalTempFile: File) {
        try {
            val tree = DocumentFile.fromTreeUri(context, outputDirUri!!)
            val safeName = bookName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeName.m4a"

            val docFile = tree?.createFile("audio/mp4", fileName)
            docFile?.uri?.let { destUri ->
                context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                    finalTempFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
        } catch (e: Exception) {
            println("SAF Write Error during final export: ${e.message}")
        }
    }

    private fun cleanup(finalTempFile: File? = null) {
        val wavFile = getWavFile(chunkIndex)
        if (wavFile.exists()) wavFile.delete()

        val tempM4a = getTempM4aFile(chunkIndex)
        if (tempM4a.exists()) tempM4a.delete()

        encodedChunkFiles.forEach { file ->
            if (file.exists()) file.delete()
        }
        encodedChunkFiles.clear()

        if (finalTempFile?.exists() == true) {
            finalTempFile.delete()
        }
    }

    private fun getWavFile(index: Int) = File(context.cacheDir, "temp_audiobook_chunk_$index.wav")
    private fun getTempM4aFile(index: Int) = File(context.cacheDir, "temp_audiobook_chunk_$index.m4a")
}
