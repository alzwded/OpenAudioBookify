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

import android.content.Context
import android.content.SharedPreferences

class SettingsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("openaudiobookify_settings", Context.MODE_PRIVATE)

    var ttsEngine: String?
        get() = prefs.getString("tts_engine", null)
        set(value) = prefs.edit().putString("tts_engine", value).apply()

    // TODO dubious, should remove, selecting a voice should be enough
    var ttsLanguage: String?
        get() = prefs.getString("tts_language", null)
        set(value) = prefs.edit().putString("tts_language", value).apply()

    var ttsVoice: String?
        get() = prefs.getString("tts_voice", null)
        set(value) = prefs.edit().putString("tts_voice", value).apply()

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1.0f)
        set(value) = prefs.edit().putFloat("speech_rate", value).apply()

    var pitch: Float
        get() = prefs.getFloat("pitch", 1.0f)
        set(value) = prefs.edit().putFloat("pitch", value).apply()

    var encoderBitrate: Int
        get() = prefs.getInt("encoder_bitrate", 48000)
        set(value) = prefs.edit().putInt("encoder_bitrate", value).apply()
}
