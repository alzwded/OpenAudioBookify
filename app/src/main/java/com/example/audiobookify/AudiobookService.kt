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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class AudiobookService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "AudiobookGenerationChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_BOOK_URIS = "EXTRA_BOOK_URIS" // Replaced URI and NAME with an ArrayList of URIs
    }

    private var tts: TextToSpeech? = null
    private var pipeline: AudiobookPipeline? = null
    
    // A queue to hold the pending URIs to process sequentially
    private val bookQueue = ArrayDeque<Uri>()

    // Service-bound Coroutine Scope
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Initializing engine..."))

                // Retrieve the ArrayList of URIs depending on the Android version
                val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_BOOK_URIS, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_BOOK_URIS)
                }

                if (!uris.isNullOrEmpty()) {
                    bookQueue.addAll(uris)
                    
                    if (tts == null) {
                        // Initialize TTS. This will trigger onInit() asynchronously.
                        tts = TextToSpeech(this, this)
                    } else if (pipeline == null) {
                        // If TTS is already running and pipeline is idle, start processing newly queued items
                        processNextBook()
                    } else {
                        // If pipeline is running, update the notification to reflect the newly appended queue size
                        updateNotification("Queued additional books. Total pending: ${bookQueue.size}")
                    }
                } else if (bookQueue.isEmpty() && pipeline == null) {
                    // Nothing to process, nothing currently BEING processed, safety fallback
                    shutdownService()
                    // if pipeline != null, then it will eventually finish and cause shutdownService
                }
            }
            ACTION_CANCEL -> shutdownService()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            processNextBook()
        } else {
            updateNotification("Failed to initialize TTS.")
            shutdownService()
        }
    }

    /**
     * Pops the next URI from the queue and starts processing. 
     * If the queue is empty, shuts down the service.
     */
    private fun processNextBook() {
        val nextUri = bookQueue.removeFirstOrNull()
        
        if (nextUri == null) {
            // Queue exhausted
            updateNotification("All audiobooks generated successfully.")
            shutdownService()
            return
        }

        // Fetch actual file name using ContentResolver
        var bookName = "Unknown_Book"
        contentResolver.query(nextUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    bookName = cursor.getString(nameIndex)
                }
            }
        }
        val book = Book(bookName, nextUri)

        updateNotification("Generating Audiobook: $bookName...")

        serviceScope.launch(Dispatchers.IO) {
            try {
                // General provider handles extracting text from any supported format
                val provider = BookTextProviderFactory.create(this@AudiobookService, book)

                withContext(Dispatchers.Main) {
                    pipeline = AudiobookPipeline(this@AudiobookService, tts!!, provider) {
                        // On completion of this pipeline, clear reference and process the next book
                        pipeline = null
                        processNextBook()
                    }
                    pipeline?.start()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateNotification("Error reading format for: $bookName")
                    // If one book fails, skip to the next instead of stopping the whole service
                    pipeline = null
                    processNextBook()
                }
            }
        }
    }

    private fun shutdownService() {
        bookQueue.clear()
        pipeline?.cancel()
        pipeline = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(statusText: String): Notification {
        val viewIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val viewPendingIntent = PendingIntent.getActivity(
            this, 0, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, AudiobookService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audiobook Generator")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View Queue", viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Audiobook Processing", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
