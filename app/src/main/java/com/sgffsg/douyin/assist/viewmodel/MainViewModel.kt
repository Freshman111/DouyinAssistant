package com.sgffsg.douyin.assist.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    private val _serviceEnabled = MutableLiveData<Boolean>()
    val serviceEnabled: LiveData<Boolean> = _serviceEnabled

    private val _autoLikeRunning = MutableLiveData<Boolean>()
    val autoLikeRunning: LiveData<Boolean> = _autoLikeRunning

    private val _autoSwipeRunning = MutableLiveData<Boolean>()
    val autoSwipeRunning: LiveData<Boolean> = _autoSwipeRunning

    init {
        _serviceEnabled.value = false
        _autoLikeRunning.value = false
        _autoSwipeRunning.value = false
    }

    fun updateServiceStatus(enabled: Boolean) {
        _serviceEnabled.value = enabled
    }

    fun updateAutoLikeStatus(running: Boolean) {
        _autoLikeRunning.value = running
    }

    fun updateAutoSwipeStatus(running: Boolean) {
        _autoSwipeRunning.value = running
    }
}
