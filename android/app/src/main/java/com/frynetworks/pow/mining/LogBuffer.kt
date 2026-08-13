package com.frynetworks.pow.mining

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Bounded ring buffer of miner output, pushed to the UI rather than polled. */
class LogBuffer(private val capacity: Int = 500) {

    private val lines = ArrayDeque<String>(capacity)
    private val _state = MutableStateFlow<List<String>>(emptyList())
    val state: StateFlow<List<String>> = _state

    @Synchronized
    fun append(line: String) {
        if (line.isBlank()) return
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(line)
        _state.value = lines.toList()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        _state.value = emptyList()
    }
}
