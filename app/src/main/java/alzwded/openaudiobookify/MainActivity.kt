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

import android.Manifest
import android.app.Application
import android.content.ClipData
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
import androidx.activity.viewModels
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

private const val TAG = "OAB_MAIN_ACTIVITY"

data class Book(val name: String, val uri: Uri)

// --- ViewModel to Bridge Service & Compose ---
@UnstableApi
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var audiobookService: AudiobookService? = null

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive = _isServiceActive.asStateFlow()

    private val _queueState = MutableStateFlow<List<BookState>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val _selectedBooks = MutableStateFlow<List<Book>>(emptyList())
    val selectedBooks = _selectedBooks.asStateFlow()

    private val _outputDirUri = MutableStateFlow<Uri?>(null)
    val outputDirUri = _outputDirUri.asStateFlow()

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

    fun addBooks(uris: List<Uri>) {
        val context = getApplication<Application>()
        val currentList = _selectedBooks.value

        // Filter out URIs already in the current list
        val newUris = uris.filter { uri -> currentList.none { it.uri == uri } }

        val newBooks = newUris.mapNotNull { uri ->
            try {
                // Request persistent read permission if supported (e.g. from OpenDocument)
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some URIs (like from MediaStore or shared via Intent) don't support persistable permissions.
                // We'll still try to use them with the temporary permission grant.
                Log.w(TAG, "Could not take persistable permission for $uri: ${e.message}")
            }

            var displayName = "Unknown"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query URI $uri", e)
                // If we can't query it even now, we probably don't have access at all
                return@mapNotNull null
            }
            Book(displayName, uri)
        }
        _selectedBooks.value = currentList + newBooks
    }

    fun removeBook(book: Book) {
        _selectedBooks.value = _selectedBooks.value.filter { it.uri != book.uri }
    }

    fun clearBooks() {
        _selectedBooks.value = emptyList()
    }

    fun setOutputDirUri(uri: Uri?) {
        _outputDirUri.value = uri
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

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        enableEdgeToEdge()

        setContent {
            OpenAudioBookifyTheme {
                OpenAudioBookifyApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle new intents when activity is already running
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                handleSingleShare(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleMultipleShare(intent)
            }
        }
    }

    private fun handleSingleShare(intent: Intent) {
        // Get the shared URI
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        uri?.let {
            // Add to ViewModel
            viewModel.addBooks(listOf(it))
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        // Get multiple shared URIs
        val uris: List<Uri> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
        }

        if (uris.isNotEmpty()) {
            // Add to ViewModel
            viewModel.addBooks(uris)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenAudioBookifyApp(viewModel: MainViewModel) {
    val context = LocalContext.current

    // Track the service state from the ViewModel
    val isProcessing by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val outputDirUri by viewModel.outputDirUri.collectAsStateWithLifecycle()

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
                Toast.makeText(context, context.getString(R.string.notification_permission_required), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.addBooks(uris)
            }
        }
    )

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            viewModel.setOutputDirUri(uri)
            uri?.let {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not take persistable permission for output dir $it", e)
                }
            }
        }
    )

    val startProcessingService = {
        val intent = Intent(context, AudiobookService::class.java).apply {
            action = AudiobookService.ACTION_START
            putParcelableArrayListExtra(AudiobookService.EXTRA_BOOK_URIS, ArrayList(selectedBooks.map { it.uri }))
            putExtra("EXTRA_OUTPUT_URI", outputDirUri)

            // Only pass the book URIs via ClipData for temporary READ permission inheritance.
            // The directory is picked with ACTION_OPEN_DOCUMENT_TREE and because it stems from
            // the activity, we can immediately takePersistableUriPermission with read+write
            if (selectedBooks.isNotEmpty()) {
                val clipData = ClipData.newRawUri("AudiobookUris", selectedBooks.first().uri)

                // Add any subsequent books
                selectedBooks.drop(1).forEach { book ->
                    clipData.addItem(ClipData.Item(book.uri))
                }
                this.clipData = clipData

                // ONLY ask to grant READ permission. If you ask for WRITE on an ACTION_SEND URI, it crashes.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        ContextCompat.startForegroundService(context, intent)
        Toast.makeText(context, context.getString(R.string.processing_started), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenAudioBookify") },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, AboutActivity::class.java))
                        }
                    ) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { paddingValues ->
        OpenAudioBookifyContent(
            modifier = Modifier.padding(paddingValues),
            selectedBooks = selectedBooks,
            outputDirUri = outputDirUri,
            isProcessing = isProcessing,
            queueState = queueState,
            onAddBooksClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            onClearBooksClick = { viewModel.clearBooks() },
            onRemoveBookClick = { book -> viewModel.removeBook(book) },
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
                        outputDirUri == null -> context.getString(R.string.error_no_output_folder)
                        selectedBooks.isEmpty() -> context.getString(R.string.error_no_books_added)
                        else -> context.getString(R.string.error_unknown)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onCancelProcessingClick = { viewModel.cancelWork() }
        )
    }
}

@Composable
fun OpenAudioBookifyContent(
    modifier: Modifier = Modifier,
    selectedBooks: List<Book>,
    outputDirUri: Uri?,
    isProcessing: Boolean,
    queueState: List<BookState>,
    onAddBooksClick: () -> Unit,
    onClearBooksClick: () -> Unit,
    onRemoveBookClick: (Book) -> Unit,
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
                    Text(stringResource(R.string.add_books))
                }

                if (selectedBooks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearBooksClick,
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_books))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSetOutputFolderClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing // Disable while processing
            ) {
                Text(if (outputDirUri == null) stringResource(R.string.set_output_folder) else stringResource(R.string.output_set))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.books_to_process),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })

            if (isProcessing && queueState.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(queueState) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = book.name,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 1
                            )
                            when (book.status) {
                                BookStatus.QUEUED -> Text(stringResource(R.string.status_queued))
                                BookStatus.PROCESSING -> Text(stringResource(R.string.status_processing, book.currentChunk))
                                BookStatus.FINISHED -> Text(stringResource(R.string.status_finished))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(selectedBooks) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = book.name,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { onRemoveBookClick(book) },
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_book_desc, book.name))
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Button(
                    onClick = onCancelProcessingClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.cancel_processing))
                }
            } else {
                Button(
                    onClick = onStartProcessingClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.start_processing))
                }
            }
        }
    }
}
