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
    WHITE("纯白", 0xFFFFFFFF),
    LIGHT_GRAY("浅灰", 0xFFF2F3F5),
    CREAM("米白", 0xFFFAF5E9),
    PALE_GREEN("浅绿", 0xFFEAF3E8),
    MIST_BLUE("雾蓝", 0xFFEAF1F8),
    PALE_PURPLE("淡紫", 0xFFF1ECF7),
    BLACK("纯黑", 0xFF000000),
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
