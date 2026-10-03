package com.ottotech.workoutbuilder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.ai.ApiKeyFormat
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.AppPrefs
import com.ottotech.workoutbuilder.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val hasKey: Boolean,
    /** What the user is typing into the key field; never pre-filled with the saved key. */
    val draftKey: String = "",
    val draftError: String? = null,
    val soundOn: Boolean,
    val voiceOn: Boolean,
    val theme: ThemeMode,
)

class SettingsViewModel(private val keys: ApiKeyStore, private val prefs: AppPrefs) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(hasKey = keys.hasKey, soundOn = !prefs.cuesMuted, voiceOn = prefs.voiceOn, theme = prefs.themeMode.value))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onDraftKey(text: String) = _state.update { it.copy(draftKey = text, draftError = null) }

    fun saveKey() {
        val key = ApiKeyFormat.normalise(_state.value.draftKey)
        if (!ApiKeyFormat.looksValid(key)) {
            _state.update { it.copy(draftError = "That doesn't look like an Anthropic key — they start with sk-ant-") }
            return
        }
        keys.set(key)
        _state.update { it.copy(hasKey = true, draftKey = "", draftError = null) }
    }

    fun removeKey() {
        keys.clear()
        _state.update { it.copy(hasKey = false) }
    }

    fun onSoundOn(on: Boolean) {
        prefs.cuesMuted = !on
        _state.update { it.copy(soundOn = on) }
    }

    fun onVoiceOn(on: Boolean) {
        prefs.voiceOn = on
        _state.update { it.copy(voiceOn = on) }
    }

    /** Applies immediately: MainActivity observes the same preference. */
    fun onTheme(mode: ThemeMode) {
        prefs.setThemeMode(mode)
        _state.update { it.copy(theme = mode) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                SettingsViewModel(app.apiKeys, app.prefs)
            }
        }
    }
}
