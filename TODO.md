- [x] BookTextProvider interface
- [x] Refactor out HTML DOM parsing logic from EpubExtractor
- [x] move out EpubExtractor.batchByLength into AudiobookPipeline
- [x] Refactor AudiobookService/AudiobookPipeline to use BookTextExtractor, like MainActivity.processBooks
- [x] Refactor AudiobookService to accept multiple epubs as input (ArrayList or Array, call the extra field `file_uris` or something like that)
- [x] Use ContentResolver to derive mime type in BookTextProvider rather than extension
- [x] Integrate AudiobookService/AudiobookPipeline in MainActivity.kt, replacing existing code in processBooks, eliminating local ttsInstance and tts
- [x] ~~fix debug render after previous point invariably breaks it~~ not needed, but it tests okay
- [x] ~~In AudiobookPipeline, make sure chunk files are generated with String.format("%05d", chunkIndex), and that we keep track of them~~ not needed, kept track of anyway
- [x] In AudiobookPipeline, configure AAC settings, i.e. Mono, 48kbps bitrate, etc; there should be per-app settings of these things
- [ ] ~~Rework pipeline and chunkers to generate any number of m4a files and merge them, see "Merge M4A Files with Media3 Transformer" chat; intermediate m4a files should be written to Cache folder; rework output path to be an output file (the final m4a file); basically merge everything into one giant stream~~ not sure that's useful...
- [x] add icon
- [ ] Configuration screen with persistent settings
  * [ ] TTS settings?
    + [ ] language/dialect/voice; should read system TTS settings if possible
    + [ ] speech rate
    + [ ] speech pitch
  * [ ] AAC settings (mostly bitrate)
- [ ] Add "about" info on the settings screen, like a new audiobookifyapp@gmail.com contact info, link to github etc
- [x] check for node.isBlock in html extractor is probably overeager and dubious; should refactor to yield innerText on leaf nodes OR consider a node with child textnodes to be a leaf
- [ ] Ensure main window keeps track of global "doing something" vs "idle" state. In "doing something", the only action is "Cancel"; in "idle", you can select books, change settings, and hit start
- [ ] When "doing something", ensure we track which books were processed, which is being processed, which are done
- [ ] Default output path?
- [ ] Check cancel works
