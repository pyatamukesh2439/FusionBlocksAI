package com.fusion.aicontroller.model

enum class ControllerStatus(val displayName: String) {
    STOPPED("Stopped"),
    RUNNING("Running"),
    PERMISSION_REQUIRED("Permission Required")
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: String,
    val message: String
)
