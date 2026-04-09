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

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val TAG = "OAB_SETTINGS_ACTIVITY"

// Simple Display Models for the UI
data class TtsEngine(val id: String, val label: String)
data class TtsVoice(val id: String, val displayName: String)

class SettingsActivity : ComponentActivity() {
    private lateinit var settingsHelper: SettingsHelper
    private var tts: TextToSpeech? = null

    // Hoisted state for UI to react to TTS initialization and queries
    private var availableEngines = mutableStateListOf<TextToSpeech.EngineInfo>()
    private var availableVoices = mutableStateListOf<Voice>()
    private var isTtsReady = mutableStateOf(false)
    private var currentEngineId = mutableStateOf<String?>(null)
    private var currentVoiceId = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsHelper = SettingsHelper(this)
        
        currentEngineId.value = settingsHelper.ttsEngine
        currentVoiceId.value = settingsHelper.ttsVoice

        // Initialize TTS with the saved engine, or default if null
        initTts(settingsHelper.ttsEngine)

        setContent {
            OpenAudioBookifyTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    SettingsScreen(
                        settingsHelper = settingsHelper,
                        isTtsReady = isTtsReady.value,
                        engines = availableEngines,
                        voices = availableVoices,
                        currentEngineId = currentEngineId.value ?: "",
                        currentVoiceId = currentVoiceId.value ?: "",
                        onEngineSelected = { engineId ->
                            // When engine changes, save it and re-initialize to fetch its specific voices
                            settingsHelper.ttsEngine = engineId
                            currentEngineId.value = engineId
                            isTtsReady.value = false
                            initTts(engineId)
                        },
                        onVoiceSelected = { voiceId ->
                            // Find the actual Voice object by ID
                            val voiceObj = availableVoices.find { it.name == voiceId }
                            Log.d(TAG, "looking for ${voiceId}, found ${voiceObj?.name ?: "<null>"}")
                            voiceObj?.let {
                                Log.d(TAG, "selected ${it.name}")
                                settingsHelper.ttsVoice = it.name
                                currentVoiceId.value = it.name
                                settingsHelper.ttsLanguage = it.locale.toLanguageTag()
                                tts?.voice = it
                            }
                        },
                        onPlaySample = {
                            tts?.let { t ->
                                t.setSpeechRate(settingsHelper.speechRate)
                                t.setPitch(settingsHelper.pitch)
                                t.speak(
                                    "1, 2, 3, 4, 12345678.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "sample_id"
                                )
                            }
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    private fun initTts(engineName: String?) {
        tts?.shutdown() // Ensure any existing instance is cleaned up
        tts = TextToSpeech(this, { status ->
            runOnUiThread {
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { t ->
                        availableEngines.clear()
                        availableEngines.addAll(t.engines)
                        availableVoices.clear()
                        
                        if (currentEngineId.value == null) {
                            currentEngineId.value = t.defaultEngine
                        }

                        // reset voices, as they probably don't match across engines
                        val voices = t.voices?.toList()?.sortedBy { it.name } ?: emptyList()
                        availableVoices.addAll(voices)

                        // Determine the best voice based on system locale
                        val systemLocale = Locale.getDefault()

                        val bestVoice =
                            currentVoiceId.value?.let { voiceId -> voices.find { it.name == voiceId } } // restore voice from settings bundle
                            ?: voices.find { it.locale == systemLocale } // Exact match
                            ?: voices.find { it.locale.language == systemLocale.language } // Language match
                            ?: voices.firstOrNull() // Absolute first fallback

                        // Propagate to SettingsHelper and TTS Instance
                        if (bestVoice != null) {
                            settingsHelper.ttsVoice = bestVoice.name
                            currentVoiceId.value = bestVoice.name
                            settingsHelper.ttsLanguage = bestVoice.locale.toLanguageTag()
                            t.voice = bestVoice
                        } else {
                            // Reset to nothing if the engine is empty or invalid
                            settingsHelper.ttsVoice = null
                            currentVoiceId.value = null
                            settingsHelper.ttsLanguage = null
                        }

                        // flipping this from false to true triggers a re-render
                        isTtsReady.value = true
                    }
                } else {
                    Log.e(TAG, "Failed to initialize TTS with engine '$engineName'. Status code: $status")
                    Toast.makeText(this@SettingsActivity, "Failed to initialize TTS", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }, engineName)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

/**
 * Stateful wrapper that connects the UI to the SettingsHelper logic.
 */
@Composable
fun SettingsScreen(
    settingsHelper: SettingsHelper,
    isTtsReady: Boolean,
    engines: List<TextToSpeech.EngineInfo>,
    voices: List<Voice>,
    currentEngineId: String,
    currentVoiceId: String,
    onEngineSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onPlaySample: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local UI state for immediate slider/text feedback
    var rate by remember { mutableFloatStateOf(settingsHelper.speechRate) }
    var pitch by remember { mutableFloatStateOf(settingsHelper.pitch) }
    var bitrate by remember { mutableIntStateOf(settingsHelper.encoderBitrate) }

    // Map system objects to our simple Display Models
    val engineModels = engines.map { TtsEngine(it.name, it.label) }
    val voiceModels = voices.map { TtsVoice(it.name, "${it.name} (${it.locale.displayName})") }

    SettingsScreenContent(
        rate = rate,
        onRateChange = { rate = it; settingsHelper.speechRate = it },
        pitch = pitch,
        onPitchChange = { pitch = it; settingsHelper.pitch = it },
        bitrate = bitrate,
        onBitrateChange = {
            bitrate = it
            settingsHelper.encoderBitrate = it
        },
        isTtsReady = isTtsReady,
        engines = engineModels,
        voices = voiceModels,
        currentEngineId = currentEngineId,
        onEngineSelected = onEngineSelected,
        currentVoiceId = currentVoiceId,
        onVoiceSelected = onVoiceSelected,
        onPlaySample = onPlaySample,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    rate: Float,
    onRateChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    bitrate: Int,
    onBitrateChange: (Int) -> Unit,
    isTtsReady: Boolean,
    engines: List<TtsEngine>,
    voices: List<TtsVoice>,
    currentEngineId: String,
    onEngineSelected: (String) -> Unit,
    currentVoiceId: String,
    onVoiceSelected: (String) -> Unit,
    onPlaySample: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEngineSelection by remember { mutableStateOf(false) }
    var showVoiceSelection by remember { mutableStateOf(false) }
    val view = LocalView.current

    LaunchedEffect(isTtsReady) {
        if (isTtsReady) {
            view.announceForAccessibility("Ready")
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!isTtsReady) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "Initializing TTS Engine...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else if (engines.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "No Text-To-Speech engines found. Please install a TTS engine such as 'Speech Recognition and Synthesis by Google' or 'eSpeak' to configure these settings.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            val engineDisplayText = engines.find { it.id == currentEngineId }?.label ?: currentEngineId

            // Engine Selector
            Box(modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "TTS Engine: $engineDisplayText"
                    role = Role.Button
                    onClick(label = "Select Engine") {
                        if (engines.isNotEmpty()) showEngineSelection = true
                        true
                    }
                }
            ) {
                OutlinedTextField(
                    value = engineDisplayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("TTS Engine") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEngineSelection) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Invisible box to steal clicks
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            if (engines.isNotEmpty()) showEngineSelection = true
                        }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val voiceDisplayText = voices.find { it.id == currentVoiceId }?.displayName
                                   ?: if (voices.isEmpty()) "No voices available for this engine"
                                      else "Select a voice"
            // Voice Selector (Opens a Searchable Dialog instead of a Dropdown)
            Box(modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Voice: $voiceDisplayText"
                    role = Role.Button
                    onClick(label = "Select Voice") {
                        if (voices.isNotEmpty()) showVoiceSelection = true
                        true
                    }
                }
            ) {
                OutlinedTextField(
                    // Display voice name alongside its friendly locale name
                    value = voiceDisplayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("TTS Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVoiceSelection) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Invisible box to steal clicks so the keyboard doesn't open
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            if (voices.isNotEmpty()) showVoiceSelection = true
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val speechRateDisplayText = "${"%.2f".format(rate)}x"
            Row(
                modifier = Modifier.fillMaxWidth().semantics(/*mergeDescendants = true*/) {},
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Speech Rate: $speechRateDisplayText",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clearAndSetSemantics { } )
                TextButton(
                    onClick = { onRateChange(1.0f) },
                    modifier = Modifier.semantics {
                        contentDescription = "Reset Speech Rate"
                    }
                ) {
                    Text("Reset", modifier = Modifier.clearAndSetSemantics { })
                }
            }
            Slider(
                value = rate,
                onValueChange = onRateChange,
                valueRange = 0.5f..4.0f,
                modifier = Modifier.semantics {
                    contentDescription = "Speech Rate"
                    stateDescription = speechRateDisplayText
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val pitchDisplayText = "%.2f".format(pitch)
            Row(
                modifier = Modifier.fillMaxWidth().semantics(/*mergeDescendants = true*/) {},
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pitch: $pitchDisplayText",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clearAndSetSemantics { }
                )
                TextButton(
                    onClick = { onPitchChange(1.0f) },
                    modifier = Modifier.semantics {
                        contentDescription = "Reset Pitch"
                    }
                ) {
                    Text("Reset", modifier = Modifier.clearAndSetSemantics { })
                }
            }
            Slider(
                value = pitch,
                onValueChange = onPitchChange,
                valueRange = 0.25f..2.0f,
                modifier = Modifier.semantics {
                    contentDescription = "Pitch"
                    stateDescription = pitchDisplayText
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPlaySample,
                enabled = isTtsReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Play Sample")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        val bitrateValues = listOf(48000, 64000, 128000, 192000, 256000)
        val bitrateLabels = listOf("Lowest", "Low", "Typical", "High", "Highest")
        val currentIndex = bitrateValues.indexOf(bitrate).coerceAtLeast(0)

        Text(
            text = "Encoder Bitrate: ${bitrateLabels[currentIndex]} (${bitrate / 1000} kbps)",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clearAndSetSemantics { }
        )
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { index ->
                onBitrateChange(bitrateValues[index.toInt()])
            },
            valueRange = 0f..4f,
            steps = 3,
            modifier = Modifier.semantics {
                val index = currentIndex.toInt()
                val label = bitrateLabels.getOrNull(index) ?: "Unknown"
                val kbps = bitrateValues.getOrNull(index)?.let { "(${ it / 1000 } kbps)" } ?: ""
                contentDescription = "Encoder Bitrate"
                stateDescription = "$label $kbps"
            }
        )
    }

    // Engine Selection Dialog
    if (showEngineSelection) {
        val engineOptions = engines.map { SelectionOption(it.id, it.label) }
        SearchableSelectionDialog(
            title = "Select Engine",
            searchLabel = "Search engines",
            currentSelectedId = currentEngineId,
            options = engineOptions,
            onDismissRequest = { showEngineSelection = false },
            onOptionSelected = {
                onEngineSelected(it)
                showEngineSelection = false
            }
        )
    }

    // Voice Selection Dialog
    if (showVoiceSelection) {
        val voiceOptions = voices.map { SelectionOption(it.id, it.displayName) }
        SearchableSelectionDialog(
            title = "Select Voice",
            searchLabel = "Search voices",
            currentSelectedId = currentVoiceId,
            options = voiceOptions,
            onDismissRequest = { showVoiceSelection = false },
            onOptionSelected = {
                onVoiceSelected(it)
                showVoiceSelection = false
            }
        )
    }
}
