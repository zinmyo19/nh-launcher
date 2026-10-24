package com.zynelabs.nhlauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlin.reflect.KProperty

class AccentDef(val name: String, val color: Color, val on: Color)

object Accent {
    val all = listOf(
        AccentDef("KALI BLUE", Color(0xFF367BF0), Color(0xFFFFFFFF)),
        AccentDef("MATRIX", Color(0xFF2EF28C), Color(0xFF021A0E)),
        AccentDef("CRIMSON", Color(0xFFFF4D5E), Color(0xFF1C0004)),
        AccentDef("CYAN", Color(0xFF2FD7F5), Color(0xFF001A20)),
        AccentDef("VIOLET", Color(0xFFA77CFF), Color(0xFF12002E)),
        AccentDef("ICE", Color(0xFFA8D8FF), Color(0xFF001426)),
        AccentDef("LIME", Color(0xFFB6F23A), Color(0xFF121A00)),
    )
}

/** A colour palette for the whole launcher (backgrounds, panels, lines, text). */
class ThemeDef(
    val name: String, val bg: Color, val panel: Color, val panel2: Color,
    val line: Color, val text: Color, val sub: Color,
)

object Themes {
    val all = listOf(
        ThemeDef("KALI", Color(0xFF05090C), Color(0xFF0A1318), Color(0xFF0F1B22), Color(0xFF1D313C), Color(0xFFD3E4EC), Color(0xFF6C8896)),
        ThemeDef("AMOLED", Color(0xFF000000), Color(0xFF070707), Color(0xFF101010), Color(0xFF252525), Color(0xFFE6E6E6), Color(0xFF7A7A7A)),
        ThemeDef("MIDNIGHT", Color(0xFF060A18), Color(0xFF0B1226), Color(0xFF111B36), Color(0xFF22305A), Color(0xFFD6E0FF), Color(0xFF7183B0)),
        ThemeDef("CARBON", Color(0xFF0C0E10), Color(0xFF15181B), Color(0xFF1D2227), Color(0xFF2E353C), Color(0xFFDDE3E8), Color(0xFF8993A0)),
        ThemeDef("HACKER", Color(0xFF020A05), Color(0xFF07140B), Color(0xFF0C1D11), Color(0xFF15361F), Color(0xFFC9F5D8), Color(0xFF5F9A74)),
    )
}

object Bg {
    const val HUD = 0
    const val CUSTOM = 1
    const val SYSTEM = 2
    const val BLACK = 3
}

private class IntP(private val sp: SharedPreferences, private val key: String, private val def: Int, private val lo: Int, private val hi: Int) {
    private val s = mutableIntStateOf(sp.getInt(key, def).coerceIn(lo, hi))
    operator fun getValue(r: Any?, p: KProperty<*>): Int = s.intValue
    operator fun setValue(r: Any?, p: KProperty<*>, v: Int) { val x = v.coerceIn(lo, hi); s.intValue = x; sp.edit().putInt(key, x).apply() }
}

private class BoolP(private val sp: SharedPreferences, private val key: String, private val def: Boolean) {
    private val s = mutableStateOf(sp.getBoolean(key, def))
    operator fun getValue(r: Any?, p: KProperty<*>): Boolean = s.value
    operator fun setValue(r: Any?, p: KProperty<*>, v: Boolean) { s.value = v; sp.edit().putBoolean(key, v).apply() }
}

/** Plain SharedPreferences; nothing leaves the phone. Every setting is observable, so the UI updates at once. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("nhlauncher", Context.MODE_PRIVATE)

    // ---- theme (mirrored into NH so every composable sees the change)
    var accent: Int by IntP(sp, "accent", 0, 0, Accent.all.lastIndex)
        private set
    fun useAccent(v: Int) { val x = v.coerceIn(0, Accent.all.lastIndex); accent = x; NH.accentIdx = x }
    var theme: Int by IntP(sp, "theme", 0, 0, Themes.all.lastIndex)
        private set
    fun useTheme(v: Int) { val x = v.coerceIn(0, Themes.all.lastIndex); theme = x; NH.themeIdx = x }
    var panel: Int by IntP(sp, "panel", 1, 0, 2) // 0 solid, 1 glass, 2 clear
        private set
    fun usePanel(v: Int) { val x = v.coerceIn(0, 2); panel = x; NH.panelIdx = x }

    // ---- background
    var bgMode by IntP(sp, "bg_mode_v2", Bg.HUD, 0, 3)
    var bgDim by IntP(sp, "bg_dim", 35, 0, 90)          // percent of black laid over the picture
    var bgFit by IntP(sp, "bg_fit", 0, 0, 1)            // 0 fit (whole picture), 1 fill (crop)
    var bgAlign by IntP(sp, "bg_align", 2, 0, 2)        // 0 top, 1 centre, 2 bottom
    var grid by BoolP(sp, "grid_bg", true)
    var scanlines by BoolP(sp, "scanlines", true)
    var brackets by BoolP(sp, "brackets", true)

    // ---- icons
    var iconShape by IntP(sp, "icon_shape", 0, 0, 3)    // 0 cut corners, 1 square, 2 round, 3 none
    var iconSize by IntP(sp, "icon_size", 1, 0, 2)      // 0 small, 1 medium, 2 large
    var iconTint by IntP(sp, "icon_tint", 0, 0, 2)      // 0 original, 1 mono, 2 accent
    var labels by BoolP(sp, "labels", true)
    var columns by IntP(sp, "columns", 4, 3, 5)

    // ---- home
    var sysmon by BoolP(sp, "sysmon", true)
    var seconds by BoolP(sp, "seconds", true)
    var clock24 by BoolP(sp, "clock24", true)
    var pinRows by IntP(sp, "pin_rows", 3, 1, 4)
    var hint by BoolP(sp, "hint", true)
    var hideStatus by BoolP(sp, "hide_status", false)
    var swipeUp by BoolP(sp, "swipe_up", true)
    var swipeDown by BoolP(sp, "swipe_down", true)

    val iconDp: Int get() = when (iconSize) { 0 -> 46; 2 -> 62; else -> 54 }

    init { NH.accentIdx = accent; NH.themeIdx = theme; NH.panelIdx = panel }

    /** Puts every look-and-feel setting back to its default (pinned and hidden apps are kept). */
    fun resetLook() {
        useAccent(0); useTheme(0); usePanel(1)
        bgMode = Bg.HUD; bgDim = 35; bgFit = 0; bgAlign = 2; grid = true; scanlines = true; brackets = true
        iconShape = 0; iconSize = 1; iconTint = 0; labels = true; columns = 4
        sysmon = true; seconds = true; clock24 = true; pinRows = 3; hint = true; hideStatus = false; swipeUp = true; swipeDown = true
    }

    /** Ordered list of pinned "package/class" keys. */
    var pinned: List<String>
        get() = (sp.getString("pinned", null) ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("pinned", v.joinToString("\n")).apply()
    val pinnedSet: Boolean get() = sp.contains("pinned")

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()
}
