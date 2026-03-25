- [x] BookTextProvider interface
- [x] Refactor out HTML DOM parsing logic from EpubExtractor
- [x] move out EpubExtractor.batchByLength into AudiobookPipeline
- [x] Refactor AudiobookService/AudiobookPipeline to use BookTextExtractor, like MainActivity.processBooks
- [x] Refactor AudiobookService to accept multiple epubs as input (ArrayList or Array, call the extra field `file_uris` or something like that)
- [x] Use ContentResolver to derive mime type in BookTextProvider rather than extension
- [ ] In AudiobookPipeline, make sure chunk files are generated with String.format("%05d", chunkIndex), and that we keep track of them
- [ ] In AudiobookPipeline, configure AAC settings, i.e. Mono, 48kbps bitrate, etc; there should be per-app settings of these things
- [ ] Rework pipeline and chunkers to generate any number of m4a files and merge them, see "Merge M4A Files with Media3 Transformer" chat; intermediate m4a files should be written to Cache folder; rework output path to be an output file (the final m4a file); basically merge everything into one giant stream
- [ ] Integrate AudiobookService/AudiobookPipeline in MainActivity.kt, replacing existing code in processBooks, eliminating local ttsInstance and tts
  ```kotlin
   class MainActivity : AppCompatActivity() {
    
        private val filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            // User selected files - we have permission here
            startFileProcessingService(uris)
        }
        
        private fun startFileProcessingService(uris: List<Uri>) {
            val intent = Intent(this, FileProcessingService::class.java).apply {
                putParcelableArrayListExtra("file_uris", ArrayList(uris))
                
                // ✅ CRITICAL: Grant read permission to the service
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // If service needs to write to the files:
                // addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            startService(intent)
        }
        
        fun pickFiles() {
            filePickerLauncher.launch("*/*")
        }
    }

    class FileProcessingService : Service() {
    
        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableArrayListExtra("file_uris", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableArrayListExtra<Uri>("file_uris")
            }
    
            uris?.forEach { uri ->
                processFile(uri) // ✅ Can access because permission was granted
            }
    
            stopSelf()
            return START_NOT_STICKY
        }
    
        private fun processFile(uri: Uri) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Read and process the file
                    val bytes = inputStream.readBytes()
                    // ... do something with the file
                }
            } catch (e: SecurityException) {
                // This would happen if FLAG_GRANT_READ_URI_PERMISSION wasn't set
                Log.e("Service", "Permission denied for URI: $uri", e)
            }
        }
    
        override fun onBind(intent: Intent?): IBinder? = null
    }
  ```
- [ ] fix debug render after previous point invariably breaks it
- [ ] Ensure main window keeps track of global "doing something" vs "idle" state. In "doing something", the only action is "Cancel"; in "idle", you can select books, change settings, and hit start
- [ ] When "doing something", ensure we track which books were processed, which is being processed, which are done
- [ ] TTS settings?
- [ ] AAC settings (mostly bitrate)
- [ ] Default output path?
- [ ] Check cancel works
