# AudioBookify Project Plan

## Statement of Work
AudioBookify is an Android application designed to convert text-based documents (TXT, EPUB, HTML) into high-quality audio files using Android's Text-to-Speech (TTS) engines. The app will feature smart content splitting and background processing.

### Core Features:
1.  **Document Import & Management**:
    - Support for `.txt`, `.epub`, and `.html` files via System File Picker.
    - Persistent storage of imported "books" or "documents".
2.  **Text Extraction Engine**:
    - Plain text extraction for `.txt`.
    - HTML parsing using Jsoup to remove boilerplate and extract main content.
    - EPUB parsing to handle chapters and navigation structure.
3.  **TTS Configuration**:
    - Engine selection (e.g., Google Speech Services, Samsung TTS).
    - Voice selection based on the selected engine.
    - Real-time adjustment of Pitch and Speech Rate.
4.  **Audio Generation & Chunking**:
    - Background synthesis using `synthesizeToFile`.
    - Split audio into user-defined durations (e.g., 10-minute chunks).
    - Intelligent splitting at paragraph or sentence boundaries.
    - Support for `.mp3` or `.m4a` formats (depending on TTS engine support and Android version).
5.  **Queue & Progress Tracking**:
    - WorkManager for reliable background execution.
    - Notification-based progress updates.

## Architecture
- **Pattern**: MVVM (Model-View-ViewModel) + Clean Architecture.
- **UI**: Jetpack Compose for all screens.
- **Dependency Injection**: Hilt (preferred) or Koin.
- **Data Persistence**: Room for storing document metadata and processing status.
- **File Handling**: Scoped Storage compliance.
- **Asynchrony**: Kotlin Coroutines and Flow.

## Proposed Module Structure
- `:app`: Main UI and DI setup.
- `:core`: Shared models and utilities.
- `:domain`: Business logic, use cases, and repository interfaces.
- `:data`: Repository implementations, Room DB, and File IO.
- `:tts`: TTS engine wrappers and synthesis logic.
- `:parser`: Text extraction logic for different file formats.

## Work Log
- **2023-10-27**: Initialized `AGENTS.md` with the project plan and architecture overview.
