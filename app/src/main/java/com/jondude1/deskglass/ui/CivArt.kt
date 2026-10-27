package com.jondude1.deskglass.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/** All the pixel art for the village, drawn from small text grids when the app starts. */
class CivArt {
    private fun sprite(rows: List<String>, pal: Map<Char, Int>): ImageBitmap {
        val h = rows.size
        val w = rows[0].length
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val c = pal[rows[y].getOrElse(x) { '.' }]
            if (c != null) bmp.setPixel(x, y, c)
        }
        return bmp.asImageBitmap()
    }

    private fun grassTile(seed: Int): ImageBitmap {
        val r = Random(seed.toLong())
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) {
            val roll = r.nextInt(14)
            bmp.setPixel(x, y, when (roll) {
                0 -> 0xFF4C9650.toInt()
                1 -> 0xFF63B05C.toInt()
                else -> 0xFF58A653.toInt()
            })
        }
        return bmp.asImageBitmap()
    }

    private fun waterTile(frame: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) {
            val wave = ((x + frame * 3 + (y / 4) * 5) % 8) == 0 && y % 4 == 1
            bmp.setPixel(x, y, if (wave) 0xFF6FA3DC.toInt() else 0xFF2F6FB5.toInt())
        }
        return bmp.asImageBitmap()
    }

    val grass = List(4) { grassTile(it + 1) }
    val water = List(2) { waterTile(it) }

    val tree = sprite(TREE, mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2F6F34.toInt(), 't' to 0xFF7A4B24.toInt(), 'T' to 0xFF5A3517.toInt()))
    val stump = sprite(STUMP, mapOf('t' to 0xFF7A4B24.toInt(), 'T' to 0xFF5A3517.toInt()))
    val rock = sprite(ROCK, mapOf('r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC3C8D0.toInt(), 'd' to 0xFF6B707A.toInt()))
    private val bushPal = mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2F6F34.toInt(), 'r' to 0xFFD7384E.toInt())
    val bush = sprite(BUSH, bushPal)
    val bushEmpty = sprite(BUSH.map { it.replace('r', 'g') }, bushPal)
    val hut = sprite(HUT, mapOf('r' to 0xFFB5533C.toInt(), 'R' to 0xFF8F3E2C.toInt(), 'w' to 0xFFD9B77E.toInt(), 'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8A6A3E.toInt()))
    val farm = sprite(FARM, mapOf('d' to 0xFF6B4A2B.toInt(), 'g' to 0xFF7BC043.toInt(), 'G' to 0xFFE8C547.toInt()))
    private val firePal = mapOf('y' to 0xFFFFD54A.toInt(), 'o' to 0xFFFF8A2B.toInt(), 'L' to 0xFF6B4A2B.toInt(), 'l' to 0xFF4A3220.toInt())
    val fire = listOf(sprite(FIRE1, firePal), sprite(FIRE2, firePal))

    private val shirts = intArrayOf(0xFFD94F4F.toInt(), 0xFF4F7FD9.toInt(), 0xFFE0B040.toInt(), 0xFF8E4FD9.toInt(), 0xFF4FB58E.toInt(), 0xFFE07A3A.toInt(), 0xFFD94F9B.toInt(), 0xFFEFEFEF.toInt())
    private val hairs = intArrayOf(0xFF3B2A1A.toInt(), 0xFF8A5A2B.toInt(), 0xFF222222.toInt(), 0xFFD9B04A.toInt(), 0xFF7A2E1E.toInt(), 0xFFB0B0B0.toInt(), 0xFF5A3A6A.toInt(), 0xFF2E4A3A.toInt())
    private val people = List(8) { i ->
        val pal = mapOf('r' to hairs[i], 'h' to 0xFFF0C8A0.toInt(), 'd' to 0xFF222222.toInt(), 's' to shirts[i], 'p' to 0xFF3A3F5C.toInt())
        listOf(sprite(PERSON1, pal), sprite(PERSON2, pal))
    }

    fun person(colorIdx: Int, frame: Int): ImageBitmap = people[colorIdx.coerceIn(0, 7)][frame and 1]

    companion object {
        val TREE = listOf(
            "...gggggg...",
            "..gGggggGg..",
            ".gggGgggggg.",
            ".ggggggGggg.",
            "gGgggGggggGg",
            "gggggggggggg",
            ".ggGgggggGg.",
            "..gggggggg..",
            "....tttt....",
            ".....tt.....",
            ".....tT.....",
            "....tttt....",
        )
        val STUMP = listOf(
            "..tttt..",
            ".tTttTt.",
            ".tttttt.",
            "..tttt..",
        )
        val ROCK = listOf(
            "....rrrr....",
            "..rrRRrrrr..",
            ".rrRRrrrrrrd",
            "rrrrrrrrDrrd".replace('D', 'd'),
            "rrrdrrrrrrdd",
            ".rrrrrrrrddd",
            "..dddddddd..",
        )
        val BUSH = listOf(
            "...gggg...",
            "..ggGggg..",
            ".gggggrggg",
            "ggrgggggGg",
            "gggggGgrgg",
            ".ggggggggg",
            "..gggggg..",
        )
        val HUT = listOf(
            "......rr......",
            "....rrrrrr....",
            "..rrrrRrrrrr..",
            ".rrrrrrRrrrrr.",
            "rrrrrrrrrrrrrr",
            ".wwwwwwwwwwww.",
            ".wwwwwwwwwwww.",
            ".wwwwddddwwww.",
            ".wwwwddddwwww.",
            ".wwwwddddwwww.",
            ".bbbbbbbbbbbb.",
        )
        val FARM = listOf(
            "dddddddddddddd",
            "dgdgdgdgdgdgdg",
            "dGdGdGdGdGdGdG",
            "dddddddddddddd",
            "dgdgdgdgdgdgdg",
            "dGdGdGdGdGdGdG",
            "dddddddddddddd",
            "dgdgdgdgdgdgdg",
            "dGdGdGdGdGdGdG",
            "dddddddddddddd",
        )
        val FIRE1 = listOf(
            "....y...",
            "...yoy..",
            "..yooyy.",
            "..yooy..",
            "..LlLlL.",
            ".LLLLLL.",
        )
        val FIRE2 = listOf(
            "...y....",
            "..yoy...",
            "..yooy..",
            ".yyooyy.",
            "..LlLlL.",
            ".LLLLLL.",
        )
        val PERSON1 = listOf(
            ".rrrr.",
            ".hhhh.",
            ".hdhd.",
            "..hh..",
            ".ssss.",
            "hssssh",
            "hssssh",
            ".pppp.",
            ".p..p.",
            ".p..p.",
        )
        val PERSON2 = listOf(
            ".rrrr.",
            ".hhhh.",
            ".hdhd.",
            "..hh..",
            ".ssss.",
            "hssssh",
            "hssssh",
            ".pppp.",
            "..pp..",
            "..pp..",
        )
    }
}
