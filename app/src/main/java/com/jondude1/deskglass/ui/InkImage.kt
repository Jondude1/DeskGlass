package com.jondude1.deskglass.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import com.jondude1.deskglass.data.InkStroke
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Handwriting as a base64 PNG, for sending to Claude. */
fun renderInkBase64(strokes: List<InkStroke>): String =
    Base64.encodeToString(renderInkPng(strokes), Base64.NO_WRAP)

/** Draws handwriting black-on-white (so it reads well) and returns the PNG bytes. */
fun renderInkPng(strokes: List<InkStroke>): ByteArray {
    val w = 1600
    val maxY = strokes.maxOf { s -> s.points.maxOf { it.second } }
    val h = (maxY * w + 80f).toInt().coerceIn(400, 4800)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    for (s in strokes) {
        val pts = s.points.map { Pair(it.first * w, it.second * w) }
        paint.strokeWidth = max(3f, s.width * w * 1.2f)
        if (pts.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pts[0].first, pts[0].second, paint.strokeWidth / 2f, paint)
            paint.style = Paint.Style.STROKE
            continue
        }
        val path = Path()
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val cur = pts[i]
            path.quadTo(prev.first, prev.second, (prev.first + cur.first) / 2f, (prev.second + cur.second) / 2f)
        }
        path.lineTo(pts.last().first, pts.last().second)
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return out.toByteArray()
}
