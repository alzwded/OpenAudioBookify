# AudioBookify Project Plan

## Statement of Work
AudioBookify is an Android application designed to convert text-based documents (TXT, EPUB, HTML, MD) into high-quality audio files using Android's Text-to-Speech (TTS) engines. The app will feature smart content splitting and background processing.

### Core Features:
1.  **Document Import & Management**:
    - Support for `.txt`, `.epub`, `.md` and `.html` files via System File Picker.
    - Persistent storage of imported "books" or "documents".
2.  **Text Extraction Engine**:
    - Plain text extraction for `.txt`.
    - Markdown extraction for `.md`.
    - HTML parsing using Jsoup to remove boilerplate and extract main content.
    - EPUB parsing to handle chapters and navigation structure.
3.  **TTS Configuration**:
    - Engine selection (e.g., Google Speech Services, Samsung TTS).
    - Voice selection based on the selected engine.
    - Real-time adjustment of Pitch and Speech Rate.
4.  **Audio Generation & Chunking**:
    - Background synthesis using `synthesizeToFile`.
    - Stream paragraph chunks into TTS into Media3 Transformer to intermediate parts.
    - Consolidate parts into final audiobook.
    - Intelligent splitting at paragraph or sentence boundaries.
    - Support for `.m4a` or other formats (depending on TTS engine support and Android version).
5.  **Queue & Progress Tracking**:
    - ForegroundService allowing easy cancellation or queue monitoring.
    - Notification-based progress updates.

## Architecture
- **Pattern**: MVVM (Model-View-ViewModel) + Clean Architecture.
- **UI**: Jetpack Compose for all screens.
- **Scratch Storage**: use cache, be efficient about cleanup
- **File Handling**: Scoped Storage compliance.
- **Asynchrony**: Kotlin Coroutines and Flow.

## See also
 AudiobookPipeline.md
