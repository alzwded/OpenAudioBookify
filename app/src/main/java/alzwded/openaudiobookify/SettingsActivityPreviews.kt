package alzwded.openaudiobookify

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@DevicesPreview
@Composable
fun SelectionDialogPreview() {
	OpenAudioBookifyTheme {
        SearchableSelectionDialog(
			title = "Select Voice",
			searchLabel = "Search voices",
            currentSelectedId = "en-gb-x-fis",
            options = listOf(
                SelectionOption("en-us-x-sfg", "en-us-x-sfg - English (United States) local"),
                SelectionOption("en-us-x-ntk", "en-us-x-ntk - English (United States) network"),
                SelectionOption("en-gb-x-fis", "en-gb-x-fis - English (United Kingdom) local")
            ),
            onDismissRequest = { },
            onOptionSelected = { }
        )
	}
}

@DevicesPreview
@Composable
fun SettingsDefaultPreview() {
    OpenAudioBookifyTheme {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            SettingsScreenContent(
                rate = 1.25f,
                onRateChange = {},
                pitch = 1.0f,
                onPitchChange = {},
                bitrate = 48000,
                onBitrateChange = {},
                isTtsReady = true,
                engines = listOf(
                    TtsEngine("com.google.android.tts", "Google Speech Services"),
                    TtsEngine("com.amazon.tts", "Amazon Polly")
                ),
                voices = listOf(
                    TtsVoice("en-us-x-sfg", "English (US) - Voice I"),
                    TtsVoice("en-us-x-ntk", "English (US) - Voice II"),
                    TtsVoice("en-gb-x-fis", "English (UK) - Voice III")
                ),
                currentEngineId = "com.google.android.tts",
                onEngineSelected = {},
                currentVoiceId = "en-us-x-ntk",
                onVoiceSelected = {},
                onPlaySample = {},
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@DevicesPreview
@Composable
fun SettingsNoEnginesPreview() {
    OpenAudioBookifyTheme {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            SettingsScreenContent(
                rate = 1.25f,
                onRateChange = {},
                pitch = 1.0f,
                onPitchChange = {},
                bitrate = 48000,
                onBitrateChange = {},
                isTtsReady = true,
                engines = emptyList(),
                voices = emptyList(),
                currentEngineId = "",
                onEngineSelected = {},
                currentVoiceId = "",
                onVoiceSelected = {},
                onPlaySample = {},
                modifier = Modifier.padding(padding)
            )
        }
    }
}
