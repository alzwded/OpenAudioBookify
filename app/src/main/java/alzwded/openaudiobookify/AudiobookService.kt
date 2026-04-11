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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "OAB_AUDIOBOOK_SERVICE"

enum class BookStatus { QUEUED, PROCESSING, FINISHED }
data class BookState(
    val uri: Uri,
    val name: String,
    val status: BookStatus,
    val currentChunk: Int = 0)

@UnstableApi
class AudiobookService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "AudiobookGenerationChannel"
        const val NOTIFICATION_ID = 1
        const val COMPLETED_NOTIFICATION_ID = 2
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_BOOK_URIS = "EXTRA_BOOK_URIS"
    }

    // --- Service Binding & State ---
    inner class LocalBinder : Binder() {
        fun getService(): AudiobookService = this@AudiobookService
    }
    private val binder = LocalBinder()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _queueState = MutableStateFlow<List<BookState>>(emptyList())
    val queueState = _queueState.asStateFlow()
    // -------------------------------

    private var tts: TextToSpeech? = null
    private var isInitializingTts = false
    private var pipeline: AudiobookPipeline? = null
    private var outputDirUri: Uri? = null
    private lateinit var settingsHelper: SettingsHelper
    private var wakeLock: PowerManager.WakeLock? = null

    // A queue to hold the pending URIs and names to process sequentially
    private val bookQueue = ArrayDeque<Book>()
    private val failedBooks = mutableListOf<String>()

    // Service-bound Coroutine Scope
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private fun updateBookState(uri: Uri, status: BookStatus, chunk: Int = 0) {
        _queueState.value = _queueState.value.map {
            if (it.uri == uri) it.copy(status = status, currentChunk = chunk) else it
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenAudioBookify::AudiobookGenerationWakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            Log.i(TAG, "Acquiring WakeLock")
            // Acquiring with a 12-hour timeout as a safety net against zombie locks
            wakeLock?.acquire(12 * 60 * 60 * 1000L) 
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            Log.i(TAG, "Releasing WakeLock")
            wakeLock?.release()
        }
    }

    override fun onCreate() {
        Log.i(TAG, "Creating service")
        super.onCreate()
        settingsHelper = SettingsHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting")
                _isProcessing.value = true
                acquireWakeLock()

                // Android 14+ Requires specifying foreground service type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(getString(R.string.initializing_engine)),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.initializing_engine)))
                }

                // Extract output directory
                val outUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("EXTRA_OUTPUT_URI", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("EXTRA_OUTPUT_URI")
                }

                if (outUri != null) {
                    outputDirUri = outUri
                } else {
                    Log.e(TAG, "No output directory URI provided. Aborting.")
                    updateNotification(getString(R.string.error_no_output_dir_service))
                    shutdownService()
                    return START_NOT_STICKY
                }

                // Retrieve the ArrayList of URIs depending on the Android version
                val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_BOOK_URIS, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_BOOK_URIS)
                }

                if (!uris.isNullOrEmpty()) {
                    val newBooks = uris.map { uri ->
                        var bookName = "Unknown_Book"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    bookName = cursor.getString(nameIndex)
                                }
                            }
                        }
                        Book(bookName, uri)
                    }

                    bookQueue.addAll(newBooks)
                    _queueState.value += newBooks.map { BookState(it.uri, it.name, BookStatus.QUEUED, 0) }

                    if (tts == null) {
                        try {
                            isInitializingTts = true
                            tts = TextToSpeech(this, this, settingsHelper.ttsEngine)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to instantiate TextToSpeech engine", e)
                            updateNotification(getString(R.string.failed_to_init_tts))
                            shutdownService()
                            return START_NOT_STICKY
                        }
                    } else if (!isInitializingTts && pipeline == null) {
                        Log.i(TAG, "Pipeline does not exist, starting")
                        processNextBook()
                    } else {
                        Log.i(TAG, "Pipeline exists or TTS initializing, enqueuing only")
                        updateNotification(getString(R.string.queued_additional_books, bookQueue.size))
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
        isInitializingTts = false
        if (status == TextToSpeech.SUCCESS) {
            settingsHelper.ttsLanguage?.let {
                tts?.language = Locale.forLanguageTag(it)
            } ?: run {
                tts?.language = Locale.getDefault()
            }

            settingsHelper.ttsVoice?.let { voiceName: String ->
                val voices = tts?.voices ?: emptySet<Voice>()
                val targetVoice = voices.find { it.name == voiceName }
                if (targetVoice != null) {
                    tts?.voice = targetVoice
                } else {
                    val systemLocale = Locale.getDefault()

                    val bestVoice = voices.find { it.locale == systemLocale } // Exact match
                        ?: voices.find { it.locale.language == systemLocale.language } // Language match
                        ?: voices.firstOrNull() // Absolute first fallback

                    bestVoice?.let {
                        tts?.voice = it
                    }
                }
            }

            tts?.setSpeechRate(settingsHelper.speechRate)
            tts?.setPitch(settingsHelper.pitch)
            processNextBook()
        } else {
            updateNotification(getString(R.string.failed_to_init_tts))
            shutdownService()
        }
    }

    private fun processNextBook() {
        val nextBook = bookQueue.removeFirstOrNull()

        if (nextBook == null) {
            Log.i(TAG, "No more books in queue, shutting down")
            showCompletionNotification()
            shutdownService()
            return
        }

        if (pipeline != null) {
            Log.w(TAG, "processNextBook: pipeline already active, ignoring")
            return
        }

        _isProcessing.value = true
        Log.i(TAG, "Next book: ${nextBook.name}")

        // Strip out the extension for cleaner file names later
        val cleanBookName = nextBook.name.substringBeforeLast(".")
        val book = Book(cleanBookName, nextBook.uri)

        updateBookState(nextBook.uri, BookStatus.PROCESSING, 0)
        updateNotification(getString(R.string.generating_audiobook, cleanBookName))

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
                        outputDirUri = outputDirUri,
                        targetBitrate = settingsHelper.encoderBitrate,
                        onProgress = { chunk ->
                            updateBookState(nextBook.uri, BookStatus.PROCESSING, chunk)
                        },
                        onError = { errorMsg ->
                            serviceScope.launch(Dispatchers.Main) {
                                failedBooks.add(cleanBookName)
                                updateNotification(getString(R.string.failed_with_error, errorMsg))
                                pipeline = null
                                updateBookState(nextBook.uri, BookStatus.FINISHED)
                                processNextBook() // Move on to the next book or finish
                            }
                        },
                        onPipelineComplete = {
                            pipeline = null
                            updateBookState(nextBook.uri, BookStatus.FINISHED)
                            processNextBook()
                        }
                    )
                    pipeline?.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start pipeline, ${e.message}")
                withContext(Dispatchers.Main) {
                    failedBooks.add(cleanBookName)
                    updateNotification(getString(R.string.error_reading_format, cleanBookName))
                    pipeline = null
                    processNextBook()
                }
            }
        }
    }

    private fun shutdownService() {
        Log.i(TAG, "Shutting down")
        bookQueue.clear()
        failedBooks.clear()
        _queueState.value = emptyList()
        pipeline?.cancel()
        pipeline = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isProcessing.value = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fsiKind: Int) {
        Log.w(TAG, "Service timed out, cleaning up...")
        shutdownService()
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
        
        val contentText = if (failedBooks.isEmpty()) {
            getString(R.string.all_success)
        } else {
            val names = failedBooks.joinToString(", ")
            getString(R.string.completed_with_errors, failedBooks.size, names)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenAudioBookify")
            .setContentText(contentText)
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
            .setContentTitle("OpenAudioBookify")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.view_queue), viewPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), cancelPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    // Now returning the binder instead of null
    override fun onBind(intent: Intent?): IBinder = binder
}
