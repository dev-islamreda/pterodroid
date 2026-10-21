package com.example.ui.console

import androidx.compose.ui.graphics.Color

object TerminalLogFormatter {
    private val ansiRegex = Regex("\u001B\\[[;?0-9]*[a-zA-Z]|\u001B\\].*?\\u0007|\\[[0-9;]+m")

    fun cleanAnsi(text: String): String {
        return text.replace(ansiRegex, "")
            .replace("\r", "")
            .trimEnd()
    }

    fun getLineColor(cleanText: String, isDark: Boolean = true): Color {
        val trimmed = cleanText.trim()
        return when {
            trimmed.startsWith(">") -> if (isDark) Color(0xFF81A885) else Color(0xFF386A41)
            trimmed.startsWith("[DAEMON]") || trimmed.startsWith("[SYSTEM]") -> if (isDark) Color(0xFF90CAF9) else Color(0xFF1E6091)
            trimmed.startsWith("[STATUS]") -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
            trimmed.contains("ERROR", ignoreCase = true) ||
                    trimmed.contains("FATAL", ignoreCase = true) ||
                    trimmed.contains("Exception", ignoreCase = true) ||
                    trimmed.contains("SEVERE", ignoreCase = true) ||
                    trimmed.contains("PERMISSION DENIED", ignoreCase = true) ||
                    trimmed.contains("FAILED", ignoreCase = true) -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
            trimmed.contains("WARN", ignoreCase = true) ||
                    trimmed.contains("Warning", ignoreCase = true) -> if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
            trimmed.contains("INFO", ignoreCase = true) -> if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64)
            else -> if (isDark) Color(0xFFEAE7E1) else Color(0xFF2C2825)
        }
    }
}
