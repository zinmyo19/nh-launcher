package com.zynelabs.nhlauncher

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the launcher knows right now: installed apps, their icons and the screen being shown. */
class Model(private val app: Context) {
    val prefs = Prefs(app)
    var apps by mutableStateOf(listOf<AppEntry>())
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val pins = mutableStateListOf<String>()
    var hidden by mutableStateOf(prefs.hidden)
    var screen by mutableStateOf("home") // home | drawer | settings | hidden | about
    var query by mutableStateOf("")
    val output = mutableStateListOf<String>()
    var loaded by mutableStateOf(false)
    var homeMenu by mutableStateOf(false)
    /** The picture behind everything (NH wallpaper or the user's own); null for HUD / system / black. */
    var bgBitmap by mutableStateOf<Bitmap?>(null)
    var bgImage by mutableStateOf<ImageBitmap?>(null)
    /** Set by the activity: opens the system image picker. */
    var pickImage: (() -> Unit)? = null
    var toast: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun refresh() {
        Thread {
            val list = Apps.load(app)
            main.post {
                apps = list
                if (!prefs.pinnedSet) prefs.pinned = Apps.defaultPins(app, list)
                val valid = list.map { it.key }.toSet()
                val p = prefs.pinned.filter { it in valid }
                pins.clear(); pins.addAll(p)
                loaded = true
            }
            for (e in list) if (e.key !in icons) {
                Apps.icon(app, e)?.let { b -> main.post { icons[e.key] = b } }
            }
        }.start()
    }

    private var bgLoadedFor = -1
    private var bgStamp = 0L
    /** Loads the picture for the current background mode (off the main thread). */
    fun loadBg(force: Boolean = false) {
        val mode = prefs.bgMode
        if (mode != Bg.CUSTOM) { bgBitmap = null; bgImage = null; bgLoadedFor = -1; return }
        val stamp = if (mode == Bg.CUSTOM) Wallpaper.customFile(app).lastModified() else 0L
        if (!force && bgLoadedFor == mode && stamp == bgStamp && bgBitmap != null) return
        bgLoadedFor = mode; bgStamp = stamp
        Thread {
            val b = Wallpaper.load(app, mode)
            main.post { bgBitmap = b; bgImage = b?.asImageBitmap() }
        }.start()
    }

    fun setBgMode(mode: Int) { prefs.bgMode = mode; loadBg() }

    fun applyToPhone() {
        val b = bgBitmap
        if (b == null) { toast?.invoke("Pick your own image first"); return }
        Thread {
            val ok = Wallpaper.applyToPhone(app, b, prefs.bgFit, prefs.bgAlign, prefs.bgDim)
            main.post { toast?.invoke(if (ok) "Phone wallpaper set (home + lock)" else "Could not set the phone wallpaper") }
        }.start()
    }

    fun visible() = apps.filter { it.key !in hidden }
    fun byKey(k: String) = apps.firstOrNull { it.key == k }
    fun togglePin(e: AppEntry) {
        if (e.key in pins) pins.remove(e.key) else pins.add(e.key)
        prefs.pinned = pins.toList()
    }
    fun toggleHide(e: AppEntry) {
        hidden = if (e.key in hidden) hidden - e.key else hidden + e.key
        prefs.hidden = hidden
        if (e.key in hidden && e.key in pins) { pins.remove(e.key); prefs.pinned = pins.toList() }
    }
    fun goHome() { screen = "home"; query = ""; output.clear() }

    fun matches(q: String): List<AppEntry> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return emptyList()
        return visible().filter { it.label.lowercase().contains(s) || it.pkg.lowercase().contains(s) }
            .sortedBy { if (it.label.lowercase().startsWith(s)) 0 else 1 }
    }
}

// ---------------------------------------------------------------- backdrop
@Composable
fun Backdrop(m: Model) {
    val prefs = m.prefs
    val acc = NH.Acc
    val bg = NH.Bg
    val img = m.bgImage
    val mode = prefs.bgMode
    val dim = prefs.bgDim
    val pic = mode == Bg.CUSTOM && img != null
    Canvas(Modifier.fillMaxSize()) {
        if (mode != Bg.SYSTEM) drawRect(if (mode == Bg.BLACK) Color.Black else bg)
        if (pic && img != null) {
            val p = Wallpaper.place(img.width, img.height, size.width, size.height, prefs.bgFit, prefs.bgAlign)
            // draw only the part that is on screen
            drawImage(
                img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(p.x.toInt(), p.y.toInt()), dstSize = IntSize(p.w.toInt() + 1, p.h.toInt() + 1),
                filterQuality = FilterQuality.High,
            )
            if (prefs.bgFit == 0) {
                val fade = p.h * 0.16f
                if (p.y > 0.5f || true) {
                    drawRect(Brush.verticalGradient(listOf(bg, Color.Transparent), startY = p.y, endY = p.y + fade), Offset(0f, p.y), Size(size.width, fade))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg), startY = p.y + p.h - fade, endY = p.y + p.h), Offset(0f, p.y + p.h - fade), Size(size.width, fade))
                }
            }
        }
        if (pic || mode == Bg.SYSTEM) {
            if (dim > 0) drawRect(Color.Black.copy(alpha = dim / 100f))
        } else if (mode == Bg.HUD) {
            drawRect(
                Brush.radialGradient(listOf(acc.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 1.02f), radius = size.width * 1.05f),
            )
            drawRect(
                Brush.radialGradient(listOf(acc.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.8f),
            )
        }
        if (prefs.grid) {
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) { drawRect(Color(0x0D8FB6CC), Offset(x, 0f), Size(1f, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawRect(Color(0x0D8FB6CC), Offset(0f, y), Size(size.width, 1f)); y += step }
        }
        if (prefs.brackets) {
            val len = 26.dp.toPx(); val t2 = 2.dp.toPx(); val mm = 10.dp.toPx(); val c = acc.copy(alpha = 0.55f)
            val w = size.width; val h = size.height
            drawRect(c, Offset(mm, mm), Size(len, t2)); drawRect(c, Offset(mm, mm), Size(t2, len))
            drawRect(c, Offset(w - mm - len, mm), Size(len, t2)); drawRect(c, Offset(w - mm - t2, mm), Size(t2, len))
            drawRect(c, Offset(mm, h - mm - t2), Size(len, t2)); drawRect(c, Offset(mm, h - mm - len), Size(t2, len))
            drawRect(c, Offset(w - mm - len, h - mm - t2), Size(len, t2)); drawRect(c, Offset(w - mm - t2, h - mm - len), Size(t2, len))
        }
        if (prefs.scanlines) {
            val step = 3.dp.toPx(); var yy = 0f
            while (yy < size.height) { drawRect(Color(0x08000000), Offset(0f, yy), Size(size.width, 1f)); yy += step }
        }
    }
}

// ---------------------------------------------------------------- pieces

private fun iconShape(kind: Int, size: Int): androidx.compose.ui.graphics.Shape = when (kind) {
    1 -> androidx.compose.foundation.shape.RoundedCornerShape((size / 8).dp)
    2 -> androidx.compose.foundation.shape.CircleShape
    3 -> androidx.compose.ui.graphics.RectangleShape
    else -> chamferAll((size / 5).dp)
}

private fun tintFilter(tint: Int, acc: Color): ColorFilter? {
    if (tint == 0) return null
    if (tint == 1) return ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    val r = acc.red; val g = acc.green; val b = acc.blue
    val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        lr * r, lg * r, lb * r, 0f, 0f,
        lr * g, lg * g, lb * g, 0f, 0f,
        lr * b, lg * b, lb * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

@Composable
fun AppIcon(e: AppEntry, icons: Map<String, ImageBitmap>, size: Int, prefs: Prefs? = null) {
    val bmp = icons[e.key]
    val shape = iconShape(prefs?.iconShape ?: 0, size)
    val filter = tintFilter(prefs?.iconTint ?: 0, NH.Acc)
    Box(Modifier.size(size.dp).clip(shape).background(NH.Panel2), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, e.label, Modifier.size(size.dp), contentScale = ContentScale.Crop, colorFilter = filter)
        else Txt(e.label.take(1).uppercase(), (size / 2.5f), NH.Acc, bold = true)
    }
}

@Composable
fun AppCell(e: AppEntry, m: Model, size: Int, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(e, m.icons, size, m.prefs)
            if (e.isTerminal) Box(Modifier.align(Alignment.BottomEnd).size(10.dp).background(NH.Green))
        }
        if (m.prefs.labels) {
            Spacer(Modifier.height(5.dp))
            Txt(e.label.lowercase(), 10.5f, NH.Text, maxLines = 1)
        }
    }
}

@Composable
private fun Gauge(label: String, frac: Float, value: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Txt(label, 10.5f, NH.Sub, bold = true, letter = 1f, modifier = Modifier.width(38.dp))
        val col = if (warn) NH.Red else NH.Acc
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val n = 24; val gap = 2.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
            val lit = (frac.coerceIn(0f, 1f) * n).toInt()
            for (i in 0 until n) drawRect(if (i < lit) col else Color(0xFF14232C), Offset(i * (w + gap), 0f), Size(w, size.height))
        }
        Txt(value, 10.5f, NH.Text, modifier = Modifier.width(100.dp).padding(start = 8.dp), maxLines = 1)
    }
}

@Composable
private fun SysmonPanel(v: Vitals) {
    Panel {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("// SYSMON", 10.5f, NH.Acc, bold = true, letter = 1.6f)
                Box(Modifier.weight(1f))
                Tag(v.net, if (v.net == "OFFLINE") NH.Red else NH.Green)
                Spacer(Modifier.width(6.dp))
                Txt(v.ip, 10.5f, NH.Sub)
            }
            Spacer(Modifier.height(6.dp))
            Gauge("BAT", v.batt / 100f, "${v.batt}%" + (if (v.charging) " CHG" else "") + if (v.battTempC > 0) " ${v.battTempC.toInt()}C" else "", warn = v.batt <= 15 && !v.charging)
            Gauge("RAM", v.ramUsed, v.ramText, warn = v.ramUsed > 0.9f)
            Gauge("DISK", v.diskUsed, v.diskText, warn = v.diskUsed > 0.92f)
            Row(Modifier.padding(top = 3.dp)) {
                Txt("UP", 10.5f, NH.Sub, bold = true, letter = 1f, modifier = Modifier.width(38.dp))
                Txt(v.uptime, 10.5f, NH.Text)
            }
        }
    }
}

// ---------------------------------------------------------------- screens

@Composable
fun LauncherRoot(m: Model) {
    var tick by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val act = LocalContext.current as? Activity
    val mode = m.prefs.bgMode
    val themeIdx = NH.themeIdx
    val hideStatus = m.prefs.hideStatus
    LaunchedEffect(mode, themeIdx) {
        act?.window?.let { w ->
            if (mode == Bg.SYSTEM) {
                w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(ColorDrawable(0))
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                w.setBackgroundDrawable(ColorDrawable(if (mode == Bg.BLACK) 0xFF000000.toInt() else NH.Bg.toArgb()))
            }
        }
        m.loadBg()
    }
    LaunchedEffect(hideStatus) {
        act?.window?.let { w ->
            val c = WindowInsetsControllerCompat(w, w.decorView)
            if (hideStatus) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.statusBars())
            } else c.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    Box(Modifier.fillMaxSize()) {
        Backdrop(m)
        when (m.screen) {
            "settings" -> SettingsScreen(m, onTick = { tick++ })
            "hidden" -> HiddenScreen(m)
            "about" -> AboutScreen(m)
            else -> {
                if (m.screen == "drawer") DrawerScreen(m, onLong = { menuFor = it })
                else HomeScreen(m, onLong = { menuFor = it })
                CommandLayer(m)
            }
        }
    }
    menuFor?.let { e -> AppMenu(e, m) { menuFor = null } }
    if (m.homeMenu) HomeMenu(m)
}

@Composable
private fun HomeScreen(m: Model, onLong: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var vit by remember { mutableStateOf<Vitals?>(null) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            now = Date()
            if (n % 3 == 0) vit = Sysmon.read(ctx)
            n++
            kotlinx.coroutines.delay(1000)
        }
    }
    val h24 = m.prefs.clock24
    val hm = remember(now, h24) { SimpleDateFormat(if (h24) "HH:mm" else "hh:mm", Locale.getDefault()).format(now) }
    val ampm = remember(now, h24) { if (h24) "" else SimpleDateFormat("a", Locale.ENGLISH).format(now).uppercase() }
    val ss = remember(now) { SimpleDateFormat("ss", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE dd MMM yyyy", Locale.ENGLISH).format(now).uppercase() }
    var drag by remember { mutableStateOf(0f) }
    val cols = m.prefs.columns
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { m.homeMenu = true }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -140f && m.prefs.swipeUp) m.screen = "drawer"
                        else if (drag > 140f && m.prefs.swipeDown) Apps.expandNotifications(ctx)
                    },
                    onVerticalDrag = { _, dy -> drag += dy },
                )
            },
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(hm, 56f, NH.Text, bold = true, letter = -1f)
            if (m.prefs.seconds || ampm.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Column {
                    if (ampm.isNotEmpty()) Txt(ampm, 13f, NH.Sub, bold = true)
                    if (m.prefs.seconds) Txt(ss, 18f, NH.Acc, bold = true)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Box(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) { Tag(android.os.Build.MODEL.take(16), NH.Sub); Spacer(Modifier.height(10.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(NH.Acc))
            Spacer(Modifier.width(8.dp))
            Txt(date, 12f, NH.Sub, bold = true, letter = 2f)
        }
        Spacer(Modifier.height(16.dp))
        if (m.prefs.sysmon) vit?.let { SysmonPanel(it) }
        SectionTitle("pinned")
        val pinned = m.pins.mapNotNull { m.byKey(it) }
        if (m.loaded && pinned.isEmpty()) {
            Txt("Nothing pinned. Open the drawer (swipe up), long-press an app, choose PIN.", 11.5f, NH.Sub)
        }
        val rows = pinned.chunked(cols)
        Column {
            for (r in rows.take(m.prefs.pinRows)) {
                Row(Modifier.fillMaxWidth()) {
                    for (e in r) Box(Modifier.weight(1f)) { AppCell(e, m, m.prefs.iconDp, { if (e.isTerminal) Apps.openTerminal(ctx) else Apps.launch(ctx, e) }, { onLong(e) }) }
                    repeat(cols - r.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Box(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 86.dp), horizontalArrangement = Arrangement.Center) {
            if (m.prefs.hint) Txt("^ swipe up: apps   v swipe down: notifications   hold: menu", 9f, NH.Sub, maxLines = 1)
        }
    }
}

@Composable
private fun DrawerScreen(m: Model, onLong: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    val list = if (m.query.isBlank()) m.visible() else m.matches(m.query)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("$", 14f, NH.Acc, bold = true)
            Spacer(Modifier.width(8.dp))
            Txt("ls /apps", 14f, NH.Text, bold = true)
            Spacer(Modifier.width(10.dp))
            Tag("${list.size} items")
            Box(Modifier.weight(1f))
            Box(Modifier.height(52.dp).clickable { m.screen = "settings" }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Txt("[cfg]", 13f, NH.Acc, bold = true)
            }
            Box(Modifier.height(52.dp).clickable { m.goHome() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Txt("[x]", 14f, NH.Acc, bold = true)
            }
        }
        HLine()
        LazyVerticalGrid(
            columns = GridCells.Fixed(m.prefs.columns), modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        ) {
            items(list, key = { it.key }) { e ->
                AppCell(e, m, m.prefs.iconDp, {
                    if (e.isTerminal) Apps.openTerminal(ctx) else Apps.launch(ctx, e)
                    m.goHome()
                }, { onLong(e) })
            }
        }
    }
}

/** The "prompt" at the bottom: type to search, Enter to open the first match or run a command. */
@Composable
private fun CommandLayer(m: Model) {
    val ctx = LocalContext.current
    fun say(vararg lines: String) { m.output.clear(); m.output.addAll(lines) }
    fun run() {
        val q = m.query.trim()
        if (q.isEmpty()) return
        when (q.lowercase()) {
            "help", "?" -> say("ls          open the app drawer", "term        open NH Terminal", "wifi        Wi-Fi settings", "settings    Android settings",
                "nh          NH Launcher settings", "theme       next accent colour", "palette     next colour theme",
                "bg          next background (HUD / my image / system / black)", "clear       clear this panel", "<name>      open the best matching app")
            "ls" -> { m.screen = "drawer"; m.query = ""; m.output.clear() }
            "term", "terminal", "nhterminal" -> {
                if (Apps.openTerminal(ctx)) { m.goHome() } else say("nh: NH Terminal is not installed on this phone.")
            }
            "wifi" -> { Apps.openSystem(ctx, Settings.ACTION_WIFI_SETTINGS); m.goHome() }
            "settings" -> { Apps.openSystem(ctx, Settings.ACTION_SETTINGS); m.goHome() }
            "nh", "config" -> { m.screen = "settings"; m.query = ""; m.output.clear() }
            "theme" -> {
                val n = (m.prefs.accent + 1) % Accent.all.size
                m.prefs.useAccent(n)
                say("accent -> ${Accent.all[n].name}"); m.query = ""
            }
            "palette" -> {
                val n = (m.prefs.theme + 1) % Themes.all.size
                m.prefs.useTheme(n); say("theme -> ${Themes.all[n].name}"); m.query = ""
            }
            "bg" -> {
                var n = (m.prefs.bgMode + 1) % 4
                if (n == Bg.CUSTOM && !Wallpaper.hasCustom(ctx)) n = Bg.SYSTEM
                m.setBgMode(n); say("background -> ${bgNames[n]}"); m.query = ""
            }
            "clear" -> { m.query = ""; m.output.clear() }
            else -> {
                val hit = m.matches(q).firstOrNull()
                if (hit != null) { if (hit.isTerminal) Apps.openTerminal(ctx) else Apps.launch(ctx, hit); m.goHome() }
                else say("nh: not found: $q")
            }
        }
    }
    val matches = if (m.screen == "home") m.matches(m.query).take(5) else emptyList()
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.Bottom) {
        if (matches.isNotEmpty() || m.output.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                Panel(accent = true) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        matches.forEachIndexed { i, e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (e.isTerminal) Apps.openTerminal(ctx) else Apps.launch(ctx, e); m.goHome() }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(e, m.icons, 28, m.prefs)
                                Spacer(Modifier.width(10.dp))
                                Txt(e.label.lowercase(), 13f, if (i == 0) NH.Acc else NH.Text, bold = i == 0)
                                Box(Modifier.weight(1f))
                                if (i == 0) Txt("[enter]", 10f, NH.Sub)
                            }
                        }
                        if (matches.isEmpty()) m.output.forEach { Txt(it, 11.5f, NH.Text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
            Panel {
                Row(Modifier.fillMaxWidth().height(50.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("root", 13f, NH.Red, bold = true)
                    Txt("@nh", 13f, NH.Acc, bold = true)
                    Txt(":~# ", 13f, NH.Text, bold = true)
                    Box(Modifier.weight(1f)) {
                        if (m.query.isEmpty()) Txt("search or type help", 12.5f, NH.Sub)
                        BasicTextField(
                            value = m.query, onValueChange = { m.query = it; m.output.clear() }, singleLine = true,
                            textStyle = TextStyle(color = NH.Text, fontSize = 13.sp, fontFamily = NH.Mono),
                            cursorBrush = SolidColor(NH.Acc), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { run() }),
                        )
                    }
                    Box(
                        Modifier.fillMaxHeight().width(54.dp).clickable { if (m.screen == "drawer") m.goHome() else { m.screen = "drawer" } },
                        contentAlignment = Alignment.Center,
                    ) { Txt(if (m.screen == "drawer") "[~]" else "[ls]", 12.5f, NH.Acc, bold = true) }
                }
            }
        }
    }
}

@Composable
private fun AppMenu(e: AppEntry, m: Model, close: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().clip(chamfer(14.dp)).background(NH.Panel2).border(BorderStroke(1.dp, NH.Acc), chamfer(14.dp))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(e, m.icons, 40, m.prefs)
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt(e.label, 14f, NH.Text, bold = true, maxLines = 1)
                    Txt(e.pkg, 9.5f, NH.Sub, maxLines = 1)
                }
            }
            HLine()
            MenuRow(if (e.key in m.pins) "Unpin from home" else "Pin to home") { m.togglePin(e); close() }
            MenuRow("Hide from launcher") { m.toggleHide(e); close() }
            MenuRow("App info") { Apps.appInfo(ctx, e); close() }
            MenuRow("Uninstall", danger = true) { Apps.uninstall(ctx, e); close() }
        }
    }
}

@Composable
fun MenuRow(label: String, danger: Boolean = false, run: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = run).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(">", 12.5f, if (danger) NH.Red else NH.Acc, bold = true)
        Spacer(Modifier.width(10.dp))
        Txt(label, 13f, if (danger) NH.Red else NH.Text, bold = true)
    }
}

val bgNames = listOf("HUD", "MY IMAGE", "SYSTEM WALLPAPER", "BLACK")

/** Long-press on an empty part of the home screen. */
@Composable
fun HomeMenu(m: Model) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = { m.homeMenu = false }) {
        Column(Modifier.fillMaxWidth().clip(chamfer(14.dp)).background(NH.Panel2).border(BorderStroke(1.dp, NH.Acc), chamfer(14.dp))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("//", 13f, NH.Acc, bold = true); Spacer(Modifier.width(8.dp)); Txt("NH LAUNCHER", 13f, NH.Text, bold = true, letter = 1.5f)
            }
            HLine()
            MenuRow("Launcher settings") { m.homeMenu = false; m.screen = "settings" }
            MenuRow("Next background  (${bgNames[m.prefs.bgMode].lowercase()})") {
                var n = (m.prefs.bgMode + 1) % 4
                if (n == Bg.CUSTOM && !Wallpaper.hasCustom(ctx)) n = Bg.SYSTEM
                m.setBgMode(n); m.homeMenu = false
            }
            MenuRow("Next colour theme  (${Themes.all[m.prefs.theme].name.lowercase()})") { m.prefs.useTheme((m.prefs.theme + 1) % Themes.all.size) }
            MenuRow("Next accent  (${Accent.all[m.prefs.accent].name.lowercase()})") { m.prefs.useAccent((m.prefs.accent + 1) % Accent.all.size) }
            MenuRow("Android settings") { m.homeMenu = false; Apps.openSystem(ctx, Settings.ACTION_SETTINGS) }
        }
    }
}
