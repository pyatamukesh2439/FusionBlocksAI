package com.fusion.aicontroller.state

import com.fusion.aicontroller.model.ControllerStatus
import com.fusion.aicontroller.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ControllerStateRepository {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _status = MutableStateFlow(ControllerStatus.STOPPED)
    val status: StateFlow<ControllerStatus> = _status.asStateFlow()

    private val _placementsMade = MutableStateFlow(0)
    val placementsMade: StateFlow<Int> = _placementsMade.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(LogEntry(timestamp = timeFormat.format(Date()), message = "Fusion Blocks Intelligent AI Initialized."))
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun setStatus(newStatus: ControllerStatus) {
        _status.value = newStatus
        addLog("Status: ${newStatus.displayName}")
    }

    fun incrementPlacements() {
        _placementsMade.value++
    }

    fun addLog(message: String) {
        val entry = LogEntry(timestamp = timeFormat.format(Date()), message = message)
        _logs.value = (_logs.value + entry).takeLast(30)
    }
}
