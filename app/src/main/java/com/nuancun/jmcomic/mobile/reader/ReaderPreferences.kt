package com.nuancun.jmcomic.mobile.reader

import android.content.Context

enum class ReadingMode { HORIZONTAL, VERTICAL, CONTINUOUS }

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.HORIZONTAL,
    val followSystemBrightness: Boolean = true,
    val brightness: Float = 0.65f,
    val warmthLevel: Int = 0,
    val background: ReaderBackground = ReaderBackground.LIGHT_GRAY,
)

enum class ReaderBackground(val label: String, val argb: Long) {
    WHITE("象牙", 0xFFFAF7EF),
    LIGHT_GRAY("暖纸", 0xFFF0EEE6),
    CREAM("米白", 0xFFFAF5E9),
    PALE_GREEN("鼠尾草", 0xFFE6E9DC),
    MIST_BLUE("亚麻", 0xFFEAE1D0),
    PALE_PURPLE("暖砂", 0xFFE9D8C7),
    BLACK("墨纸", 0xFF141413),
}

class ReaderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("reader_preferences", Context.MODE_PRIVATE)

    fun load(): ReaderSettings = ReaderSettings(
        mode = enumValueOrDefault(preferences.getString("mode", null), ReadingMode.HORIZONTAL),
        followSystemBrightness = preferences.getBoolean("follow_system_brightness", true),
        brightness = preferences.getFloat("brightness", 0.65f).coerceIn(0.05f, 1f),
        warmthLevel = preferences.getInt("warmth_level", 0).coerceIn(0, 3),
        background = enumValueOrDefault(preferences.getString("background", null), ReaderBackground.LIGHT_GRAY),
    )

    fun save(settings: ReaderSettings) {
        preferences.edit()
            .putString("mode", settings.mode.name)
            .putBoolean("follow_system_brightness", settings.followSystemBrightness)
            .putFloat("brightness", settings.brightness.coerceIn(0.05f, 1f))
            .putInt("warmth_level", settings.warmthLevel.coerceIn(0, 3))
            .putString("background", settings.background.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)
}
