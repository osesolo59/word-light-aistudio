package com.aistudio.aimusic.wltv.jqr8x.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MusicRepository, private val context: Context) : ViewModel() {

    init {
        ensureUserProfileExists()
    }

    private fun ensureUserProfileExists() {
        viewModelScope.launch {
            try {
                repository.ensureUserProfileExists()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Profile initialization deferred: ${e.message}")
            }
        }
    }
}
