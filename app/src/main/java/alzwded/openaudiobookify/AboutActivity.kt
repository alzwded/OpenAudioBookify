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

package alzwded.openaudiobookify

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OAB_ABOUT_ACTIVITY"

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenAudioBookifyTheme {
                AboutScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var isDumpingLogs by remember { mutableStateOf(false) }

    val exportLogsAction = {
        if (!isDumpingLogs) {
            isDumpingLogs = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val success = exportLogs(context)
                    withContext(Dispatchers.Main) {
                        val messageRes = if (success) {
                            R.string.logs_exported_successfully
                        } else {
                            R.string.failed_to_export_logs
                        }
                        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isDumpingLogs = false
                    }
                }
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                exportLogsAction()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.write_external_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { uriHandler.openUri("https://github.com/alzwded/OpenAudioBookify") },
                modifier = Modifier.fillMaxWidth()
            ) {
                val githubRepoDesc = stringResource(R.string.github_repo_desc)
                Text(stringResource(R.string.github_repo), modifier = Modifier.semantics {
                    contentDescription = githubRepoDesc
                })
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { uriHandler.openUri("mailto:openaudiobookifyapp@gmail.com") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.contact_us))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        exportLogsAction()
                    }
                },
                enabled = !isDumpingLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dump_logs))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(stringResource(R.string.license), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.license_text),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun exportLogs(context: Context): Boolean {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "OpenAudioBookify_logs_$timestamp.txt"
    Log.i(TAG, "Dumping app logs to $fileName")

    var process: Process? = null
    return try {
        // -d dumps the current log and exits
        // -v threadtime gives standard timestamp, PID, and thread ID
        process = Runtime.getRuntime().exec("logcat -d -v threadtime")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeLogsToMediaStore(context, process, fileName)
        } else {
            writeLogsToExternalStorage(context, process, fileName)
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            Log.w(TAG, "logcat exited with code $exitCode")
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to export logs", e)
        false
    } finally {
        process?.destroy()
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun writeLogsToMediaStore(context: Context, process: Process, fileName: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val resolver = context.contentResolver
    val collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val targetUri = resolver.insert(collectionUri, contentValues)
        ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw IOException("Failed to create MediaStore entry in Downloads")

    try {
        resolver.openOutputStream(targetUri)?.use { outStream ->
            process.inputStream.use { inStream ->
                inStream.copyTo(outStream)
            }
        } ?: throw IOException("Failed to open output stream for $targetUri")

        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(targetUri, contentValues, null, null)
        Log.i(TAG, "Logs exported successfully to MediaStore: $targetUri")
    } catch (e: Exception) {
        resolver.delete(targetUri, null, null)
        throw e
    }
}

private fun writeLogsToExternalStorage(context: Context, process: Process, fileName: String) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
        throw IOException("Failed to create Downloads directory: $downloadsDir")
    }

    val logFile = File(downloadsDir, fileName)
    try {
        logFile.outputStream().use { outStream ->
            process.inputStream.use { inStream ->
                inStream.copyTo(outStream)
            }
        }
        MediaScannerConnection.scanFile(context, arrayOf(logFile.absolutePath), arrayOf("text/plain"), null)
        Log.i(TAG, "Logs exported successfully to file: ${logFile.absolutePath}")
    } catch (e: Exception) {
        if (logFile.exists()) {
            logFile.delete()
        }
        throw e
    }
}

@DevicesPreview
@Composable
fun AboutDefaultPreview() {
    OpenAudioBookifyTheme {
        AboutScreen(onBackClick = {})
    }
}
