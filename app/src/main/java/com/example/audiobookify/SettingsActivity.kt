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

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsHelper = SettingsHelper(this)

        // Initialize TTS with the saved engine, or default if null
        initTts(settingsHelper.ttsEngine)

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
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
                        onEngineSelected = { engineId ->
                            // When engine changes, save it and re-initialize to fetch its specific voices
                            settingsHelper.ttsEngine = engineId
                            isTtsReady.value = false
                            initTts(engineId)
                        },
                        onVoiceSelected = { voiceId ->
                            // Find the actual Voice object by ID
                            val voiceObj = availableVoices.find { it.name == voiceId }
                            voiceObj?.let {
                                settingsHelper.ttsVoice = it.name
                                settingsHelper.ttsLanguage = it.locale.toLanguageTag()
                                tts?.voice = it
                            }
                        },
                        onPlaySample = {
                            tts?.let { t ->
                                t.setSpeechRate(settingsHelper.speechRate)
                                t.setPitch(settingsHelper.pitch)
                                t.speak(
                                    "This is a sample of the current voice and speed settings.",
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
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { t ->
                    availableEngines.clear()
                    availableEngines.addAll(t.engines)
                    availableVoices.clear()

                    // reset voices, as they probably don't match across engines
                    val voices = t.voices?.toList() ?: emptyList()
                    availableVoices.addAll(voices)

                    // Determine the best voice based on system locale
                    val systemLocale = Locale.getDefault()

                    val bestVoice = voices.find { it.locale == systemLocale } // Exact match
                        ?: voices.find { it.locale.language == systemLocale.language } // Language match
                        ?: voices.firstOrNull() // Absolute first fallback

                    // Propagate to SettingsHelper and TTS Instance
                    if (bestVoice != null) {
                        settingsHelper.ttsVoice = bestVoice.name
                        settingsHelper.ttsLanguage = bestVoice.locale.toLanguageTag()
                        t.voice = bestVoice
                    } else {
                        // Reset to nothing if the engine is empty or invalid
                        settingsHelper.ttsVoice = null
                        settingsHelper.ttsLanguage = null
                    }

                    // flipping this from false to true triggers a re-render
                    isTtsReady.value = true
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
    onEngineSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onPlaySample: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local UI state for immediate slider/text feedback
    var rate by remember { mutableFloatStateOf(settingsHelper.speechRate) }
    var pitch by remember { mutableFloatStateOf(settingsHelper.pitch) }
    var bitrate by remember { mutableStateOf(settingsHelper.encoderBitrate.toString()) }

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
            it.toIntOrNull()?.let { intVal -> settingsHelper.encoderBitrate = intVal }
        },
        isTtsReady = isTtsReady,
        engines = engineModels,
        voices = voiceModels,
        currentEngineId = settingsHelper.ttsEngine ?: "",
        onEngineSelected = onEngineSelected,
        currentVoiceId = settingsHelper.ttsVoice ?: "",
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
    bitrate: String,
    onBitrateChange: (String) -> Unit,
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
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!isTtsReady) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            Text("Initializing TTS Engine...", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Engine Dropdown
            var engineExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = engineExpanded,
                onExpandedChange = { engineExpanded = !engineExpanded }
            ) {
                OutlinedTextField(
                    value = engines.find { it.id == currentEngineId }?.label ?: currentEngineId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("TTS Engine") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = engineExpanded,
                    onDismissRequest = { engineExpanded = false }
                ) {
                    engines.forEach { engine ->
                        DropdownMenuItem(
                            text = { Text(engine.label) },
                            onClick = {
                                onEngineSelected(engine.id)
                                engineExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice Dropdown
            var voiceExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded }
            ) {
                OutlinedTextField(
                    // Display voice name alongside its friendly locale name
                    value = voices.find { it.id == currentVoiceId }?.displayName
                        ?: if (voices.isEmpty()) "No voices available for this engine" else "Select a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("TTS Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                onVoiceSelected(voice.id)
                                voiceExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speech Rate: ${"%.2f".format(rate)}x", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onRateChange(1.0f) }) {
                Text("Reset")
            }
        }
        Slider(
            value = rate,
            onValueChange = onRateChange,
            valueRange = 0.5f..4.0f
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pitch: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onPitchChange(1.0f) }) {
                Text("Reset")
            }
        }
        Slider(
            value = pitch,
            onValueChange = onPitchChange,
            valueRange = 0.25f..2.0f
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

        OutlinedTextField(
            value = bitrate,
            onValueChange = onBitrateChange,
            label = { Text("Encoder Bitrate (bps) - Default 48000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsDefaultPreview() {
    MaterialTheme {
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
            // We use the stateless "Content" version for the preview.
            // Note: Engines/Voices are empty because the system classes are final and hard to mock.
            SettingsScreenContent(
                rate = 1.25f,
                onRateChange = {},
                pitch = 1.0f,
                onPitchChange = {},
                bitrate = "48000",
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
