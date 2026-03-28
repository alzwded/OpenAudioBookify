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

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Book(val name: String, val uri: Uri)

// --- ViewModel to Bridge Service & Compose ---
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var audiobookService: AudiobookService? = null

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive = _isServiceActive.asStateFlow()

    private val _queueState = MutableStateFlow<List<BookState>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudiobookService.LocalBinder
            audiobookService = binder.getService()

            // Listen to the service's state flow
            viewModelScope.launch {
                audiobookService?.isProcessing?.collect { processing ->
                    _isServiceActive.value = processing
                }
            }
            viewModelScope.launch {
                audiobookService?.queueState?.collect { state ->
                    _queueState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audiobookService = null
            _isServiceActive.value = false
        }
    }

    init {
        // Bind to service immediately upon ViewModel creation
        val intent = Intent(application, AudiobookService::class.java)
        application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun cancelWork() {
        val intent = Intent(getApplication(), AudiobookService::class.java).apply {
            action = AudiobookService.ACTION_CANCEL
        }
        // Starting the service with the cancel intent fires onStartCommand
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(connection)
    }
}
// -------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                val viewModel: MainViewModel = viewModel()
                AudioBookifyApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBookifyApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var outputDirUri by remember { mutableStateOf<Uri?>(null) }

    // Track the service state from the ViewModel
    val isProcessing by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Notification permission required for background processing", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                selectedBooks = selectedBooks + uris.map { uri ->
                    var displayName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                displayName = cursor.getString(nameIndex)
                            }
                        }
                    }
                    Book(displayName, uri)
                }
            }
        }
    )

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            outputDirUri = uri
            uri?.let {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
        }
    )

    val startProcessingService = {
        val intent = Intent(context, AudiobookService::class.java).apply {
            action = AudiobookService.ACTION_START
            putParcelableArrayListExtra(AudiobookService.EXTRA_BOOK_URIS, ArrayList(selectedBooks.map { it.uri }))
            putExtra("EXTRA_OUTPUT_URI", outputDirUri)
        }

        ContextCompat.startForegroundService(context, intent)
        Toast.makeText(context, "Processing started in background", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AudioBookify") },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, AboutActivity::class.java))
                        }
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        AudioBookifyContent(
            modifier = Modifier.padding(paddingValues),
            selectedBooks = selectedBooks,
            outputDirUri = outputDirUri,
            isProcessing = isProcessing,
            queueState = queueState,
            onAddBooksClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            onClearBooksClick = { selectedBooks = emptyList() },
            onSetOutputFolderClick = { dirPickerLauncher.launch(null) },
            onStartProcessingClick = {
                if (outputDirUri != null && selectedBooks.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startProcessingService()
                    }
                } else {
                    val message = when {
                        outputDirUri == null -> "Select output folder"
                        selectedBooks.isEmpty() -> "Add some books"
                        else -> "Unknown error"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onCancelProcessingClick = { viewModel.cancelWork() }
        )
    }
}

@Composable
fun AudioBookifyContent(
    modifier: Modifier = Modifier,
    selectedBooks: List<Book>,
    outputDirUri: Uri?,
    isProcessing: Boolean,
    queueState: List<BookState>,
    onAddBooksClick: () -> Unit,
    onClearBooksClick: () -> Unit,
    onSetOutputFolderClick: () -> Unit,
    onStartProcessingClick: () -> Unit,
    onCancelProcessingClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAddBooksClick,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing // Disable while processing
                ) {
                    Text("Add Books")
                }

                if (selectedBooks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearBooksClick,
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Books")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSetOutputFolderClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing // Disable while processing
            ) {
                Text(if (outputDirUri == null) "Set Output Folder" else "Output Set")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Books to process:", style = MaterialTheme.typography.titleMedium)

            if (isProcessing && queueState.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(queueState) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = book.name,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 1
                            )
                            when (book.status) {
                                BookStatus.QUEUED -> Text("Queued")
                                BookStatus.PROCESSING -> Text("Processing (Chunk ${book.currentChunk})")
                                BookStatus.FINISHED -> Text("Finished")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(selectedBooks) { book ->
                        Text(text = book.name, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            if (isProcessing) {
                Button(
                    onClick = onCancelProcessingClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Processing")
                }
            } else {
                Button(
                    onClick = onStartProcessingClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Processing")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AudioBookify") },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Info, contentDescription = "About")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            AudioBookifyContent(
                modifier = Modifier.padding(paddingValues),
                selectedBooks = listOf(
                    Book("The Great Gatsby.epub", Uri.EMPTY),
                    Book("1984.txt", Uri.EMPTY),
                    Book("Pride and Prejudice.html", Uri.EMPTY)
                ),
                outputDirUri = null,
                isProcessing = false,
                queueState = listOf(
                    BookState(Uri.EMPTY, "The Great Gatsby.epub", BookStatus.FINISHED, 8960),
                    BookState(Uri.EMPTY, "1984.txt", BookStatus.PROCESSING, 42),
                    BookState(Uri.EMPTY, "Pride and Prejudice.html", BookStatus.QUEUED, 0)
                ),
                onAddBooksClick = {},
                onClearBooksClick = {},
                onSetOutputFolderClick = {},
                onStartProcessingClick = {},
                onCancelProcessingClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun IsProcessingPreview() {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AudioBookify") },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Info, contentDescription = "About")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            AudioBookifyContent(
                modifier = Modifier.padding(paddingValues),
                selectedBooks = listOf(
                    Book("The Great Gatsby.epub", Uri.EMPTY),
                    Book("1984.txt", Uri.EMPTY),
                    Book("Pride and Prejudice.html", Uri.EMPTY)
                ),
                outputDirUri = null,
                isProcessing = true,
                queueState = listOf(
                    BookState(Uri.EMPTY, "The Great Gatsby.epub", BookStatus.FINISHED, 8960),
                    BookState(Uri.EMPTY, "1984.txt", BookStatus.PROCESSING, 42),
                    BookState(Uri.EMPTY, "Pride and Prejudice.html", BookStatus.QUEUED, 0)
                ),
                onAddBooksClick = {},
                onClearBooksClick = {},
                onSetOutputFolderClick = {},
                onStartProcessingClick = {},
                onCancelProcessingClick = {}
            )
        }
    }
}
