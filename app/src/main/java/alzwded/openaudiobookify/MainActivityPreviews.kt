package alzwded.openaudiobookify

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@DevicesPreview
@Composable
fun DefaultPreview() {
    OpenAudioBookifyTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("OpenAudioBookify") },
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
            OpenAudioBookifyContent(
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
                onRemoveBookClick = {},
                onSetOutputFolderClick = {},
                onStartProcessingClick = {},
                onCancelProcessingClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@DevicesPreview
@Composable
fun IsProcessingPreview() {
    OpenAudioBookifyTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("OpenAudioBookify") },
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
            OpenAudioBookifyContent(
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
                onRemoveBookClick = {},
                onSetOutputFolderClick = {},
                onStartProcessingClick = {},
                onCancelProcessingClick = {}
            )
        }
    }
}
