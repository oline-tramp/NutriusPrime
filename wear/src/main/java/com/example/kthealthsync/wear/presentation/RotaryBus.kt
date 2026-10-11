package com.example.kthealthsync.wear.presentation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RotaryBus {
    private val _events = MutableSharedFlow<Float>(
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    fun emit(delta: Float) {
        _events.tryEmit(delta)
    }
}
