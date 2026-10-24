package com.zynelabs.nhlauncher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.Inet4Address

const val TERMINAL_PKG = "com.zynelabs.nhterminal"
const val TERMINAL_CATEGORY = "com.zynelabs.nethunter.category.TERMINAL"

class AppEntry(
    val label: String,
    val pkg: String,
    val cls: String,
    val adaptive: Boolean,
) {
    val key get() = "$pkg/$cls"
    val component get() = ComponentName(pkg, cls)
    val isTerminal get() = pkg == TERMINAL_PKG
}

object Apps {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                val ai = it.activityInfo
                val ic = try { it.loadIcon(pm) } catch (_: Exception) { null }
                AppEntry(
                    it.loadLabel(pm).toString(), ai.packageName, ai.name,
                    Build.VERSION.SDK_INT >= 26 && ic is AdaptiveIconDrawable,
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /** Draws the app icon into a small bitmap (done off the main thread). */
    fun icon(ctx: Context, e: AppEntry, px: Int = 144): ImageBitmap? = try {
        val d = ctx.packageManager.getActivityIcon(e.component)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            // Draw the raw layers (no system mask) so the launcher can cut its own diagonal corners.
            val q = (px * 0.25f).toInt()
            d.background?.apply { setBounds(-q, -q, px + q, px + q); draw(c) }
            d.foreground?.apply { setBounds(-q, -q, px + q, px + q); draw(c) }
        } else {
            d.setBounds(0, 0, px, px)
            d.draw(c)
        }
        bmp.asImageBitmap()
    } catch (_: Exception) { null }

    fun launch(ctx: Context, e: AppEntry): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        true
    } catch (_: Exception) { false }

    fun appInfo(ctx: Context, e: AppEntry) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    fun uninstall(ctx: Context, e: AppEntry) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    /** NH Terminal is found by its category; if it is not installed this returns false. */
    fun terminalInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(TERMINAL_PKG, 0); true
    } catch (_: Exception) { false }

    fun openTerminal(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(TERMINAL_CATEGORY).addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(TERMINAL_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (_: Exception) {
            try {
                val l = ctx.packageManager.getLaunchIntentForPackage(TERMINAL_PKG)
                if (l != null) { ctx.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } else false
            } catch (_: Exception) { false }
        }
    }

    fun openSystem(ctx: Context, action: String): Boolean = try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    /** Reasonable first-run pins: terminal, browser, phone, camera, settings. */
    fun defaultPins(ctx: Context, all: List<AppEntry>): List<String> {
        val pm = ctx.packageManager
        val out = LinkedHashSet<String>()
        all.firstOrNull { it.isTerminal }?.let { out += it.key }
        fun byIntent(i: Intent) {
            val r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return
            all.firstOrNull { it.pkg == r.packageName }?.let { out += it.key }
        }
        byIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        byIntent(Intent(Intent.ACTION_DIAL))
        byIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        all.firstOrNull { it.pkg == "com.android.settings" }?.let { out += it.key }
        return out.toList()
    }

    fun expandNotifications(ctx: Context) {
        try {
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {}
    }
}

/** One reading of the phone's vitals for the home screen panel. */
class Vitals(
    val batt: Int, val charging: Boolean, val battTempC: Float,
    val ramUsed: Float, val ramText: String,
    val diskUsed: Float, val diskText: String,
    val net: String, val ip: String,
    val uptime: String,
)

object Sysmon {
    private fun gb(b: Long) = "%.1fG".format(b / 1073741824.0)

    fun read(ctx: Context): Vitals {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else 0

        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val used = (mi.totalMem - mi.availMem).coerceAtLeast(0)

        val sf = try { StatFs(Environment.getDataDirectory().path) } catch (_: Exception) { null }
        val dTotal = sf?.totalBytes ?: 1L
        val dUsed = dTotal - (sf?.availableBytes ?: 0L)

        var net = "OFFLINE"; var ip = "-"
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork
            if (n != null) {
                val caps = cm.getNetworkCapabilities(n)
                net = when {
                    caps == null -> "ONLINE"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
                    else -> "ONLINE"
                }
                cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }
                    ?.hostAddress?.let { ip = it }
            }
        } catch (_: Exception) {}

        val s = SystemClock.elapsedRealtime() / 1000
        val up = "%dd %02dh %02dm".format(s / 86400, (s % 86400) / 3600, (s % 3600) / 60)
        return Vitals(
            pct, st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL, temp,
            used.toFloat() / mi.totalMem.coerceAtLeast(1), "${gb(used)}/${gb(mi.totalMem)}",
            dUsed.toFloat() / dTotal, "${gb(dUsed)}/${gb(dTotal)}",
            net, ip, up,
        )
    }
}
