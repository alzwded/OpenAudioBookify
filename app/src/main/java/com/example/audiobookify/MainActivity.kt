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
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

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
    val isInspectionMode = LocalInspectionMode.current
    var selectedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var outputDirUri by remember { mutableStateOf<Uri?>(null) }
    var ttsInitialized by remember { mutableStateOf(false) }
    
    // Use Any? to avoid direct TextToSpeech type reference in the property type,
    // which helps avoid ClassNotFoundException during Compose Preview reflection.
    val tts = remember(isInspectionMode) {
        if (isInspectionMode) {
            null
        } else {
            var instance: TextToSpeech? = null
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    instance?.language = Locale.US
                    ttsInitialized = true
                }
            }
            instance
        }
    }

    DisposableEffect(tts) {
        onDispose {
            if (tts is TextToSpeech) {
                tts.stop()
                tts.shutdown()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                selectedBooks = selectedBooks + uris.map { uri ->
                    Book(uri.lastPathSegment ?: "Unknown", uri)
                }
            }
        }
    )

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            outputDirUri = uri
        }
    )

    AudioBookifyContent(
        selectedBooks = selectedBooks,
        outputDirUri = outputDirUri,
        ttsInitialized = ttsInitialized,
        onAddBooksClick = { filePickerLauncher.launch(arrayOf("text/plain")) },
        onSetOutputFolderClick = { dirPickerLauncher.launch(null) },
        onStartProcessingClick = {
            if (ttsInitialized && outputDirUri != null && selectedBooks.isNotEmpty()) {
                tts?.let { processBooks(context, it, selectedBooks) }
            } else {
                val message = when {
                    !ttsInitialized -> "TTS not ready"
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
    ttsInitialized: Boolean,
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
                Text("Add .txt Books")
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
                modifier = Modifier.fillMaxWidth(),
                enabled = ttsInitialized
            ) {
                Text("Start Processing")
            }
        }
    }
}

/**
 * Processes books. The [tts] parameter is typed as [Any] to avoid [TextToSpeech] 
 * appearing in the function signature, which prevents [ClassNotFoundException] 
 * during Compose Preview's reflection-based method scanning.
 */
fun processBooks(context: Context, tts: Any, books: List<Book>) {
    val ttsInstance = tts as? TextToSpeech ?: return
    books.forEach { book ->
        // TODO this is where we'd call the other code the pro model wrote
        // TODO have a factory to dispatch to various types of
        //      files: .txt, .html, .md, .epub, .rst
        try {
            // TODO accumulate text until '[.]$' or '\n\n'
            //      or maybe just [.][:white:] or '\n\n'
            val content = context.contentResolver.openInputStream(book.uri)?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                val outputDir = context.getExternalFilesDir(null)
                val outputFile = File(outputDir, "${book.name.replace("/", "_")}.wav")
                
                val result = ttsInstance.synthesizeToFile(content, null, outputFile, book.name)
                if (result == TextToSpeech.SUCCESS) {
                    Toast.makeText(context, "Queued: ${book.name}", Toast.LENGTH_SHORT).show()
                    Log.d("AudioBookify", "Synthesizing to ${outputFile.absolutePath}")
                } else {
                    Log.e("AudioBookify", "Failed to queue ${book.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioBookify", "Error processing ${book.name}", e)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        AudioBookifyContent(
            selectedBooks = listOf(
                Book("The Great Gatsby", Uri.EMPTY),
                Book("1984", Uri.EMPTY),
                Book("Pride and Prejudice", Uri.EMPTY)
            ),
            outputDirUri = null,
            ttsInitialized = true,
            onAddBooksClick = {},
            onSetOutputFolderClick = {},
            onStartProcessingClick = {}
        )
    }
}
