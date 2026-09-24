package com.ottotech.workoutbuilder.domain

/** User-selectable theme, persisted in Settings. Defaults to following the system. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** A missing or unrecognised stored value means "follow the system". */
        fun fromStored(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
