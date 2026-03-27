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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    private lateinit var settingsHelper: SettingsHelper
    private var tts: TextToSpeech? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsHelper = SettingsHelper(this)

        // Initialize a temporary TTS to fetch available engines/voices if needed
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // You can populate available engines/voices here
            }
        }

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") }
                        )
                    }
                ) { padding ->
                    SettingsScreen(settingsHelper, Modifier.padding(padding))
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun SettingsScreen(settingsHelper: SettingsHelper, modifier: Modifier = Modifier) {
    var rate by remember { mutableFloatStateOf(settingsHelper.speechRate) }
    var pitch by remember { mutableFloatStateOf(settingsHelper.pitch) }
    var bitrate by remember { mutableStateOf(settingsHelper.encoderBitrate.toString()) }

    Column(modifier = modifier.padding(16.dp).fillMaxSize()) {
        Text("Speech Rate: ${"%.2f".format(rate)}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = rate,
            onValueChange = { rate = it; settingsHelper.speechRate = it },
            valueRange = 0.5f..2.0f
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Pitch: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = pitch,
            onValueChange = { pitch = it; settingsHelper.pitch = it },
            valueRange = 0.5f..2.0f
        )
        Spacer(modifier = Modifier.height(16.dp))

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
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Note: TTS Engine, Language, and Voice modifications usually require dropdowns querying system capabilities. They can be added here easily once the list is fetched via TextToSpeech.", style = MaterialTheme.typography.bodySmall)
    }
}
