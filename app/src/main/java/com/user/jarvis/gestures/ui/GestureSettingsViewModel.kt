package com.user.jarvis.gestures.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.user.jarvis.gestures.GestureRepository
import com.user.jarvis.gestures.models.GestureAction
import com.user.jarvis.gestures.models.GestureConfig

class GestureSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GestureRepository(application)

    private val _gestures = MutableLiveData<List<GestureConfig>>()
    val gestures: LiveData<List<GestureConfig>> = _gestures

    init {
        loadGestures()
    }

    private fun loadGestures() {
        _gestures.value = repository.getGestures()
    }

    fun toggleGesture(gestureId: String, enabled: Boolean) {
        val currentList = _gestures.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == gestureId }
        if (index != -1) {
            val updated = currentList[index].copy(enabled = enabled)
            currentList[index] = updated
            repository.saveGestures(currentList)
            _gestures.value = currentList
        }
    }

    fun updateGestureAction(gestureId: String, newAction: GestureAction) {
        val currentList = _gestures.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == gestureId }
        if (index != -1) {
            val updated = currentList[index].copy(action = newAction)
            currentList[index] = updated
            repository.saveGestures(currentList)
            _gestures.value = currentList
        }
    }

    fun resetToDefaults() {
        repository.resetToDefaults()
        loadGestures()
    }
}