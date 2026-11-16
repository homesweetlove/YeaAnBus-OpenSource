package kr.yeaan.bus.ui.settings

import android.content.SharedPreferences

enum class Accent(val label: String) { OCEAN("오션 블루"), FOREST("포레스트"), VIOLET("라벤더"), AMBER("선셋 오렌지") }
enum class Appearance(val label: String) { SYSTEM("기기 설정"), LIGHT("밝게"), DARK("어둡게") }
enum class TextSize(val label: String, val scale: Float) { NORMAL("기본", 1f), LARGE("크게", 1.15f), EXTRA("더 크게", 1.3f) }

data class UserSettings(
    val accent: Accent = Accent.OCEAN,
    val appearance: Appearance = Appearance.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val dataSaver: Boolean = false,
    val keepMapAwake: Boolean = false
) {
    fun refreshInterval(normal: Long): Long = if (dataSaver) maxOf(30_000L, normal) else normal

    fun save(prefs: SharedPreferences) {
        prefs.edit().putString("accent", accent.name).putString("appearance", appearance.name)
            .putString("textSize", textSize.name).putBoolean("dataSaver", dataSaver)
            .putBoolean("keepMapAwake", keepMapAwake).apply()
    }

    companion object {
        fun read(prefs: SharedPreferences) = UserSettings(
            accent = Accent.entries.firstOrNull { it.name == prefs.getString("accent", null) } ?: Accent.OCEAN,
            appearance = Appearance.entries.firstOrNull { it.name == prefs.getString("appearance", null) } ?: Appearance.SYSTEM,
            textSize = TextSize.entries.firstOrNull { it.name == prefs.getString("textSize", null) } ?: TextSize.NORMAL,
            dataSaver = prefs.getBoolean("dataSaver", false),
            keepMapAwake = prefs.getBoolean("keepMapAwake", false)
        )
    }
}
