package com.zynelabs.nhlauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import java.io.File

/** Where a picture sits on the screen: left, top, width, height in pixels. */
class Placement(val x: Float, val y: Float, val w: Float, val h: Float)

object Wallpaper {
    private const val MAX_SIDE = 2560

    fun customFile(ctx: Context) = File(ctx.filesDir, "custom_bg.jpg")
    fun hasCustom(ctx: Context) = customFile(ctx).exists()

    /** fit = show the whole picture (black fades into the empty space); fill = crop to cover the screen. */
    fun place(bw: Int, bh: Int, sw: Float, sh: Float, fit: Int, align: Int): Placement {
        val scale = if (fit == 0) minOf(sw / bw, sh / bh) else maxOf(sw / bw, sh / bh)
        val w = bw * scale; val h = bh * scale
        val x = (sw - w) / 2f
        val y = when (align) { 0 -> 0f; 2 -> sh - h; else -> (sh - h) / 2f }
        return Placement(x, y, w, h)
    }

    fun load(ctx: Context, mode: Int): Bitmap? = try {
        when (mode) {
            Bg.CUSTOM -> if (hasCustom(ctx)) BitmapFactory.decodeFile(customFile(ctx).path) else null
            else -> null
        }
    } catch (_: Throwable) { null }

    /** Reads a picture the user picked, shrinks it and keeps a private copy inside the app. */
    fun importCustom(ctx: Context, uri: Uri): Boolean = try {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val big = maxOf(info.size.width, info.size.height)
                if (big > MAX_SIDE) { val k = MAX_SIDE.toFloat() / big; dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > MAX_SIDE) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: throw IllegalStateException("decode")
        }
        customFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    } catch (_: Throwable) { false }

    /** Paints the picture the way the launcher shows it (without dimming), at phone-screen size. */
    fun render(src: Bitmap, sw: Int, sh: Int, fit: Int, align: Int, dim: Int): Bitmap {
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(AColor.BLACK)
        val p = place(src.width, src.height, sw.toFloat(), sh.toFloat(), fit, align)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(src, null, RectF(p.x, p.y, p.x + p.w, p.y + p.h), paint)
        if (fit == 0) {
            val fade = p.h * 0.16f
            val g = Paint()
            if (p.y > 0f || true) {
                g.shader = LinearGradient(0f, p.y, 0f, p.y + fade, AColor.BLACK, AColor.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(0f, p.y, sw.toFloat(), p.y + fade, g)
                g.shader = LinearGradient(0f, p.y + p.h - fade, 0f, p.y + p.h, AColor.TRANSPARENT, AColor.BLACK, Shader.TileMode.CLAMP)
                c.drawRect(0f, p.y + p.h - fade, sw.toFloat(), p.y + p.h, g)
            }
        }
        if (dim > 0) c.drawColor(AColor.argb((dim * 255 / 100), 0, 0, 0))
        return out
    }

    /** Sets the picture as the phone's own home + lock wallpaper. Returns true on success. */
    fun applyToPhone(ctx: Context, src: Bitmap, fit: Int, align: Int, dim: Int): Boolean = try {
        val dm = ctx.resources.displayMetrics
        val bmp = render(src, dm.widthPixels, dm.heightPixels, fit, align, dim)
        val wm = WallpaperManager.getInstance(ctx)
        if (Build.VERSION.SDK_INT >= 24) wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        else wm.setBitmap(bmp)
        true
    } catch (_: Throwable) { false }
}
