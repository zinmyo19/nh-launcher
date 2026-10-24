package com.zynelabs.nhlauncher

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).background(NH.Panel), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(52.dp).clickable(onClick = onBack).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Txt("<", 20f, NH.Acc, bold = true)
            }
            Txt(title.uppercase(), 13f, NH.Text, bold = true, letter = 2f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NH.Acc))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            content()
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingsScreen(m: Model, onTick: () -> Unit) {
    val ctx = LocalContext.current
    val p = m.prefs
    val imageMode = p.bgMode == Bg.CUSTOM
    Page("Launcher settings", { m.goHome() }) {
        // ------------------------------------------------ background
        SectionTitle("Background")
        Panel { Column {
            Column(Modifier.padding(14.dp)) {
                Txt("Wallpaper", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("HUD", "My image", "System", "Black"), p.bgMode, perRow = 3) { i ->
                    if (i == Bg.CUSTOM && !Wallpaper.hasCustom(ctx)) m.pickImage?.invoke() else m.setBgMode(i)
                }
                Spacer(Modifier.height(8.dp))
                Txt(when (p.bgMode) {
                    Bg.HUD -> "Plain HUD glow, no picture."
                    Bg.CUSTOM -> "Your own picture from the gallery."
                    Bg.SYSTEM -> "Shows the wallpaper set in Android."
                    else -> "Pure black (saves battery on AMOLED)."
                }, 11f, NH.Sub)
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                HudButton(if (Wallpaper.hasCustom(ctx)) "Change my image" else "Pick my image", Modifier.fillMaxWidth(), primary = false, onClick = { m.pickImage?.invoke() })
            }
            if (imageMode) {
                HLine()
                Column(Modifier.padding(14.dp)) {
                    Txt("Darken picture", 13.5f, NH.Text, bold = true)
                    Spacer(Modifier.height(10.dp))
                    val dims = listOf(0, 20, 35, 50, 65, 80)
                    Chips(dims.map { "$it%" }, dims.indexOf(p.bgDim).let { if (it < 0) 2 else it }, perRow = 6) { p.bgDim = dims[it] }
                }
                HLine()
                Column(Modifier.padding(14.dp)) {
                    Txt("Picture size", 13.5f, NH.Text, bold = true)
                    Spacer(Modifier.height(10.dp))
                    Chips(listOf("Fit (whole picture)", "Fill (crop)"), p.bgFit, perRow = 2) { p.bgFit = it }
                    Spacer(Modifier.height(12.dp))
                    Txt("Picture position", 13.5f, NH.Text, bold = true)
                    Spacer(Modifier.height(10.dp))
                    Chips(listOf("Top", "Centre", "Bottom"), p.bgAlign, perRow = 3) { p.bgAlign = it }
                }
                HLine()
                Column(Modifier.padding(14.dp)) {
                    Txt("Also use it as the phone's own wallpaper (home + lock screen).", 11.5f, NH.Sub)
                    Spacer(Modifier.height(10.dp))
                    HudButton("Set as phone wallpaper", Modifier.fillMaxWidth(), primary = false, onClick = { m.applyToPhone() })
                }
            }
            HLine()
            SettingRow("Grid overlay", "Faint blueprint grid", { HudSwitch(p.grid) { p.grid = it } })
            HLine()
            SettingRow("Corner brackets", "HUD frame marks in the corners", { HudSwitch(p.brackets) { p.brackets = it } })
            HLine()
            SettingRow("Scanlines", "Faint CRT lines", { HudSwitch(p.scanlines) { p.scanlines = it } })
        } }

        // ------------------------------------------------ theme
        SectionTitle("Theme")
        Panel { Column {
            Column(Modifier.padding(14.dp)) {
                Txt("Colour theme", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(Themes.all.map { it.name }, p.theme, perRow = 3) { p.useTheme(it) }
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Accent colour", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Accent.all.forEachIndexed { i, a ->
                        Box(
                            Modifier.weight(1f).height(40.dp).background(a.color.copy(alpha = if (i == p.accent) 1f else 0.25f))
                                .border(BorderStroke(1.dp, if (i == p.accent) NH.Text else a.color)).clickable { p.useAccent(i) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Txt(Accent.all[p.accent].name, 10.5f, NH.Sub, letter = 1f)
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Panels", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(4.dp))
                Txt("How see-through the boxes are over the wallpaper.", 11f, NH.Sub)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("Solid", "Glass", "Clear"), p.panel, perRow = 3) { p.usePanel(it) }
            }
        } }

        // ------------------------------------------------ icons
        SectionTitle("Icons")
        Panel { Column {
            Column(Modifier.padding(14.dp)) {
                Txt("Icon shape", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("Cut", "Square", "Round", "Full"), p.iconShape, perRow = 4) { p.iconShape = it }
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Icon size", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("Small", "Medium", "Large"), p.iconSize, perRow = 3) { p.iconSize = it }
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Icon colour", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("Original", "Mono", "Accent"), p.iconTint, perRow = 3) { p.iconTint = it }
            }
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Icons per row", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("3", "4", "5"), p.columns - 3, perRow = 3) { p.columns = it + 3 }
            }
            HLine()
            SettingRow("App names", "Show the name under each icon", { HudSwitch(p.labels) { p.labels = it } })
        } }

        // ------------------------------------------------ home
        SectionTitle("Home screen")
        Panel { Column {
            SettingRow("System monitor", "Battery, RAM, storage, network and uptime", { HudSwitch(p.sysmon) { p.sysmon = it } })
            HLine()
            SettingRow("Show seconds", null, { HudSwitch(p.seconds) { p.seconds = it } })
            HLine()
            SettingRow("24-hour clock", "Off = 12-hour with AM/PM", { HudSwitch(p.clock24) { p.clock24 = it } })
            HLine()
            Column(Modifier.padding(14.dp)) {
                Txt("Pinned rows", 13.5f, NH.Text, bold = true)
                Spacer(Modifier.height(10.dp))
                Chips(listOf("1", "2", "3", "4"), p.pinRows - 1, perRow = 4) { p.pinRows = it + 1 }
            }
            HLine()
            SettingRow("Swipe up = app drawer", null, { HudSwitch(p.swipeUp) { p.swipeUp = it } })
            HLine()
            SettingRow("Swipe down = notifications", null, { HudSwitch(p.swipeDown) { p.swipeDown = it } })
            HLine()
            SettingRow("Gesture hint", "Small tip line at the bottom", { HudSwitch(p.hint) { p.hint = it } })
            HLine()
            SettingRow("Hide status bar", "Swipe from the top edge to peek", { HudSwitch(p.hideStatus) { p.hideStatus = it } })
            HLine()
            SettingRow("Hidden apps", "${m.hidden.size} hidden", { Txt(">", 14f, NH.Acc, bold = true) }, onClick = { m.screen = "hidden" })
        } }

        SectionTitle("Default launcher")
        Panel { Column(Modifier.padding(14.dp)) {
            Txt("Make NH Launcher the app that opens when you press Home.", 11.5f, NH.Sub)
            Spacer(Modifier.height(10.dp))
            HudButton("Choose default launcher", Modifier.fillMaxWidth(), onClick = { Apps.openSystem(ctx, Settings.ACTION_HOME_SETTINGS) })
        } }

        SectionTitle("Related")
        Panel { Column {
            SettingRow("NH Terminal", if (Apps.terminalInstalled(ctx)) "installed - tap to open" else "not installed (separate app)", { Txt(">", 14f, NH.Acc, bold = true) },
                onClick = { Apps.openTerminal(ctx) })
            HLine()
            SettingRow("About", "Version ${BuildConfig.VERSION_NAME}", { Txt(">", 14f, NH.Acc, bold = true) }, onClick = { m.screen = "about" })
        } }

        SectionTitle("Reset")
        Panel { Column(Modifier.padding(14.dp)) {
            Txt("Puts every look and home-screen setting back to default. Pinned and hidden apps are kept.", 11.5f, NH.Sub)
            Spacer(Modifier.height(10.dp))
            HudButton("Reset look and feel", Modifier.fillMaxWidth(), primary = false, danger = true, onClick = { p.resetLook(); m.loadBg(); onTick() })
        } }
    }
}

@Composable
fun HiddenScreen(m: Model) {
    Page("Hidden apps", { m.screen = "settings" }) {
        Spacer(Modifier.height(10.dp))
        Txt("Tap an app to hide or show it. Hidden apps stay installed; they just do not appear in the drawer or search.", 11.5f, NH.Sub)
        Spacer(Modifier.height(10.dp))
        Panel { Column {
            m.apps.forEachIndexed { i, e ->
                if (i > 0) HLine()
                val hid = e.key in m.hidden
                Row(Modifier.fillMaxWidth().clickable { m.toggleHide(e) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(e, m.icons, 32, m.prefs)
                    Spacer(Modifier.width(12.dp))
                    Txt(e.label, 13f, if (hid) NH.Sub else NH.Text, bold = true, modifier = Modifier.weight(1f), maxLines = 1)
                    Tag(if (hid) "hidden" else "shown", if (hid) NH.Red else NH.Green)
                }
            }
        } }
    }
}

@Composable
fun AboutScreen(m: Model) {
    Page("About", { m.screen = "settings" }) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(NH.Acc), contentAlignment = Alignment.Center) { Txt("NH", 20f, NH.OnAcc, bold = true) }
            Spacer(Modifier.width(14.dp))
            Column {
                Txt("NH LAUNCHER", 18f, NH.Text, bold = true, letter = 2f)
                Txt("version ${BuildConfig.VERSION_NAME}", 11.5f, NH.Sub)
            }
        }
        SectionTitle("What it is")
        Txt("A home screen with a command-line feel: clock, system monitor, pinned apps, and a prompt at the bottom where you type an app name and press Enter.", 12.5f, NH.Text)
        SectionTitle("Gestures")
        Panel { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("swipe up        app drawer (ls /apps)", 11.5f, NH.Text)
            Txt("swipe down      notifications", 11.5f, NH.Text)
            Txt("long press app  pin, hide, info, uninstall", 11.5f, NH.Text)
            Txt("prompt          type 'help' for commands", 11.5f, NH.Text)
        } }
        SectionTitle("Please read")
        Txt("Independent app by Zyne. Not made or endorsed by OffSec, the Kali Linux project or the NetHunter project. It does not collect or send any data; there is no internet permission.", 11.5f, NH.Sub)
        Spacer(Modifier.height(10.dp))
        Txt("Open-source font: DejaVu Sans Mono (Bitstream Vera / DejaVu licence). App code: GPL-3.0-or-later.", 10.5f, NH.Sub)
            }
}
