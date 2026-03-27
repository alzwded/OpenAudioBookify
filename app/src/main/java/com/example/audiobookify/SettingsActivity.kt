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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    private lateinit var settingsHelper: SettingsHelper
    private var tts: TextToSpeech? = null

    // Hoisted state for UI to react to TTS initialization and queries
    private var availableEngines = mutableStateListOf<TextToSpeech.EngineInfo>()
    private var availableVoices = mutableStateListOf<Voice>()
    private var isTtsReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsHelper = SettingsHelper(this)

        // Initialize TTS with the saved engine, or default if null
        initTts(settingsHelper.ttsEngine)

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text("Settings") }
                        )
                    }
                ) { padding ->
                    SettingsScreen(
                        settingsHelper = settingsHelper,
                        isTtsReady = isTtsReady.value,
                        engines = availableEngines,
                        voices = availableVoices,
                        onEngineSelected = { newEngine ->
                            // When engine changes, save it and re-initialize to fetch its specific voices
                            settingsHelper.ttsEngine = newEngine
                            isTtsReady.value = false
                            initTts(newEngine)
                        },
                        onVoiceSelected = { newVoice ->
                            settingsHelper.ttsVoice = newVoice.name
                            settingsHelper.ttsLanguage = newVoice.locale.toLanguageTag()
                            tts?.voice = newVoice // Apply immediately for sample
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
                    try {
                        t.voices?.let { voices -> 
                            availableVoices.addAll(voices.sortedBy { it.name }) 
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Auto-initialize defaults if they haven't been set yet
                    if (settingsHelper.ttsEngine == null) {
                        settingsHelper.ttsEngine = t.defaultEngine
                    }
                    if (settingsHelper.ttsVoice == null) {
                        settingsHelper.ttsVoice = t.voice?.name
                    }
                    if (settingsHelper.ttsLanguage == null) {
                        settingsHelper.ttsLanguage = t.voice?.locale?.toLanguageTag() 
                            ?: t.language?.toLanguageTag()
                    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsHelper: SettingsHelper,
    isTtsReady: Boolean,
    engines: List<TextToSpeech.EngineInfo>,
    voices: List<Voice>,
    onEngineSelected: (String) -> Unit,
    onVoiceSelected: (Voice) -> Unit,
    onPlaySample: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rate by remember { mutableFloatStateOf(settingsHelper.speechRate) }
    var pitch by remember { mutableFloatStateOf(settingsHelper.pitch) }
    var bitrate by remember { mutableStateOf(settingsHelper.encoderBitrate.toString()) }

    // Read current settings dynamically to react when auto-initialized or changed
    var currentEngine by remember(settingsHelper.ttsEngine, isTtsReady) { 
        mutableStateOf(settingsHelper.ttsEngine ?: "") 
    }
    var currentVoice by remember(settingsHelper.ttsVoice, isTtsReady) { 
        mutableStateOf(settingsHelper.ttsVoice ?: "") 
    }

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
                    value = engines.find { it.name == currentEngine }?.label ?: currentEngine,
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
                                currentEngine = engine.name
                                onEngineSelected(engine.name)
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
                    value = voices.find { it.name == currentVoice }?.let { "${it.name} (${it.locale.displayName})" } ?: currentVoice,
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
                            text = { Text("${voice.name} (${voice.locale.displayName})") },
                            onClick = {
                                currentVoice = voice.name
                                onVoiceSelected(voice)
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
            TextButton(onClick = { rate = 1.0f; settingsHelper.speechRate = 1.0f }) {
                Text("Reset")
            }
        }
        Slider(
            value = rate,
            onValueChange = { rate = it; settingsHelper.speechRate = it },
            valueRange = 0.5f..4.0f
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pitch: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pitch = 1.0f; settingsHelper.pitch = 1.0f }) {
                Text("Reset")
            }
        }
        Slider(
            value = pitch,
            onValueChange = { pitch = it; settingsHelper.pitch = it },
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
            onValueChange = { 
                bitrate = it
                it.toIntOrNull()?.let { intVal -> settingsHelper.encoderBitrate = intVal }
            },
            label = { Text("Encoder Bitrate (bps) - Default 48000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
