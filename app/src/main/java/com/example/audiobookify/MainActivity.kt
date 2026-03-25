/*
 * Copyright (c) 2026, Vlad Mesco
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Book(val name: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AudioBookifyApp()
            }
        }
    }
}

@Composable
fun AudioBookifyApp() {
    val context = LocalContext.current
    var selectedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var outputDirUri by remember { mutableStateOf<Uri?>(null) }
    
    // Check notification permission for Android 13+ (Required for Foreground Services)
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
            // Persist read/write permissions for the selected directory across restarts
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

    AudioBookifyContent(
        selectedBooks = selectedBooks,
        outputDirUri = outputDirUri,
        onAddBooksClick = { filePickerLauncher.launch(arrayOf("*/*")) },
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
        }
    )
}

@Composable
fun AudioBookifyContent(
    selectedBooks: List<Book>,
    outputDirUri: Uri?,
    onAddBooksClick: () -> Unit,
    onSetOutputFolderClick: () -> Unit,
    onStartProcessingClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AudioBookify Walking Skeleton", 
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onAddBooksClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Books (TXT, EPUB, etc.)")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onSetOutputFolderClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (outputDirUri == null) "Set Output Folder" else "Output Set")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Books to process:", style = MaterialTheme.typography.titleMedium)
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedBooks) { book ->
                    Text(text = book.name, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            
            Button(
                onClick = onStartProcessingClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Processing")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        AudioBookifyContent(
            selectedBooks = listOf(
                Book("The Great Gatsby.epub", Uri.EMPTY),
                Book("1984.txt", Uri.EMPTY),
                Book("Pride and Prejudice.html", Uri.EMPTY)
            ),
            outputDirUri = null,
            onAddBooksClick = {},
            onSetOutputFolderClick = {},
            onStartProcessingClick = {}
        )
    }
}
