package com.zynelabs.nhlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var model: Model

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { model.refresh() }
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Thread {
            val ok = Wallpaper.importCustom(applicationContext, uri)
            runOnUiThread {
                if (ok) { model.prefs.bgMode = Bg.CUSTOM; model.loadBg(force = true); Toast.makeText(this, "Image set as launcher background", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(this, "Could not read that image", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        model = Model(applicationContext)
        model.pickImage = { try { picker.launch("image/*") } catch (_: Exception) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show() } }
        model.toast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        model.refresh()
        model.loadBg()
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        })
        setContent {
            // A launcher has nothing to go back to: Back only closes the screen on top.
            BackHandler(enabled = model.screen != "home" || model.query.isNotEmpty()) {
                when (model.screen) {
                    "hidden", "about" -> model.screen = "settings"
                    else -> model.goHome()
                }
            }
            LauncherRoot(model)
        }
    }

    /** Pressing Home while the launcher is open returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) model.goHome()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
