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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

private const val TAG = "AUDIOBOOK_SERVICE"

class AudiobookService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "AudiobookGenerationChannel"
        const val NOTIFICATION_ID = 1
        const val COMPLETED_NOTIFICATION_ID = 2
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_BOOK_URIS = "EXTRA_BOOK_URIS" 
    }

    private var tts: TextToSpeech? = null
    private var pipeline: AudiobookPipeline? = null
    private var outputDirUri: Uri? = null
    
    // A queue to hold the pending URIs to process sequentially
    private val bookQueue = ArrayDeque<Uri>()

    // Service-bound Coroutine Scope
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        Log.i(TAG, "Creating service")
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting")
                startForeground(NOTIFICATION_ID, buildNotification("Initializing engine..."))

                // Extract output directory
                val outUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("EXTRA_OUTPUT_URI", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("EXTRA_OUTPUT_URI")
                }
                
                if (outUri != null) {
                    outputDirUri = outUri
                }

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
                        tts = TextToSpeech(this, this)
                    } else if (pipeline == null) {
                        Log.i(TAG, "Pipeline does not exist, starting")
                        processNextBook()
                    } else {
                        Log.i(TAG, "Pipeline exists, enqueuing")
                        updateNotification("Queued additional books. Total pending: ${bookQueue.size}")
                    }
                } else if (bookQueue.isEmpty() && pipeline == null) {
                    Log.w(TAG, "Nothing to do, shuting down")
                    shutdownService()
                }
            }
            ACTION_CANCEL -> shutdownService()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        Log.i(TAG, "onInit $status")
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            processNextBook()
        } else {
            updateNotification("Failed to initialize TTS.")
            shutdownService()
        }
    }

    private fun processNextBook() {
        val nextUri = bookQueue.removeFirstOrNull()
        
        if (nextUri == null) {
            Log.i(TAG, "No more books in queue, shutting down")
            showCompletionNotification()
            shutdownService()
            return
        }

        Log.i(TAG, "Next book")

        var bookName = "Unknown_Book"
        contentResolver.query(nextUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    bookName = cursor.getString(nameIndex)
                }
            }
        }
        
        // Strip out the extension for cleaner file names later
        val cleanBookName = bookName.substringBeforeLast(".")
        val book = Book(cleanBookName, nextUri)

        updateNotification("Generating Audiobook: $cleanBookName...")

        serviceScope.launch(Dispatchers.IO) {
            try {
                val provider = BookTextProviderFactory.create(this@AudiobookService, book)

                withContext(Dispatchers.Main) {
                    // Pass the bookname and the destination URI to the pipeline
                    pipeline = AudiobookPipeline(
                        context = this@AudiobookService,
                        tts = tts!!,
                        provider = provider,
                        bookName = book.name,
                        outputDirUri = outputDirUri
                    ) {
                        // TODO this isn't a race condition with enqueuing new books, is it?
                        pipeline = null
                        processNextBook()
                    }
                    pipeline?.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start pipeline, ${e.message}")
                withContext(Dispatchers.Main) {
                    updateNotification("Error reading format for: $cleanBookName")
                    pipeline = null
                    processNextBook()
                }
            }
        }
    }

    private fun shutdownService() {
        Log.i(TAG, "Shutting down")
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
        Log.i(TAG, "Destroying")
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showCompletionNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audiobook Generator")
            .setContentText("All audiobooks generated successfully.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        manager?.notify(COMPLETED_NOTIFICATION_ID, notification)
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
